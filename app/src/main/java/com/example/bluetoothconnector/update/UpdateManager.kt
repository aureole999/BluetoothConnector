package com.example.bluetoothconnector.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Manages app updates by checking GitHub Releases and downloading/installing APKs
 */
class UpdateManager(private val context: Context) {
    
    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/aureole999/BluetoothConnector/releases/latest"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
    
    private val client = OkHttpClient()
    
    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val releaseNotes: String,
        val hasUpdate: Boolean
    )
    
    /**
     * Check for updates from GitHub Releases
     */
    suspend fun checkForUpdate(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to check for updates: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val json = JSONObject(body)
            
            val tagName = json.getString("tag_name") // e.g., "v1.0.1"
            val latestVersion = tagName.removePrefix("v") // e.g., "1.0.1"
            val releaseNotes = json.optString("body", "")
            
            // Find APK asset
            val assets = json.getJSONArray("assets")
            var downloadUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }
            
            if (downloadUrl.isEmpty()) {
                return@withContext Result.failure(Exception("No APK found in release"))
            }
            
            // Compare versions
            val currentVersion = getCurrentVersion()
            val hasUpdate = isNewerVersion(latestVersion, currentVersion)
            
            Result.success(UpdateInfo(
                versionName = latestVersion,
                versionCode = parseVersionCode(latestVersion),
                downloadUrl = downloadUrl,
                releaseNotes = releaseNotes,
                hasUpdate = hasUpdate
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Download and install the update APK
     */
    fun downloadAndInstall(downloadUrl: String, versionName: String) {
        val fileName = "BluetoothConnector-$versionName.apk"
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        // Create request
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Bluetooth Connector Update")
            .setDescription("Downloading version $versionName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType(APK_MIME_TYPE)
        
        val downloadId = downloadManager.enqueue(request)
        
        // Register receiver to install when download completes
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    ctx.unregisterReceiver(this)
                    installApk(fileName)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }
    
    private fun installApk(fileName: String) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        
        if (!file.exists()) return
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        context.startActivity(intent)
    }
    
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }
            
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }
    
    private fun parseVersionCode(version: String): Int {
        val parts = version.split(".")
        return try {
            val major = parts.getOrElse(0) { "1" }.toInt()
            val minor = parts.getOrElse(1) { "0" }.toInt()
            val patch = parts.getOrElse(2) { "0" }.toInt()
            major * 10000 + minor * 100 + patch
        } catch (e: Exception) {
            1
        }
    }
}
