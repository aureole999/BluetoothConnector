package com.example.bluetoothconnector.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.bluetoothconnector.MainActivity
import com.example.bluetoothconnector.R
import com.example.bluetoothconnector.service.QuickConnectTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Foreground Service that maintains Bluetooth connection in the background
 */
class BluetoothConnectionService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "bluetooth_connection_channel"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        
        const val ACTION_CONNECT = "com.example.bluetoothconnector.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.bluetoothconnector.ACTION_DISCONNECT" // Disconnect specific device if extra provided, or all
        const val EXTRA_DEVICE = "extra_device"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address" // For disconnect action
        const val EXTRA_AUTO_DISCONNECT = "extra_auto_disconnect"
    }
    
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Thread-safe map to hold multiple connections
    private val connectedSockets = java.util.concurrent.ConcurrentHashMap<String, BluetoothSocket>()
    private val connectedDeviceNames = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val connectingJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    
    // Callbacks for connection state changes
    var onConnectionStateChanged: ((Boolean, String?) -> Unit)? = null
    var onConnectionError: ((String) -> Unit)? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothConnectionService = this@BluetoothConnectionService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DEVICE)
                }
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: getString(R.string.default_device_name)
                val autoDisconnect = intent.getBooleanExtra(EXTRA_AUTO_DISCONNECT, false)
                
                device?.let {
                    connectToDevice(it, deviceName, autoDisconnect)
                }
            }
            ACTION_DISCONNECT -> {
                val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (deviceAddress != null) {
                    disconnectDevice(deviceAddress)
                } else {
                    disconnectAll()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }
    
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice, deviceName: String, autoDisconnect: Boolean = false) {
        // Cancel any existing connection attempt for this device
        connectingJobs[device.address]?.cancel()
        
        // If already connected to this device, ignore or reconnect? 
        // For now, if connected, just update notification. But usually we might want to ensure a fresh connection.
        // Let's close existing socket for THIS device if it exists to be safe.
        if (connectedSockets.containsKey(device.address)) {
             try {
                 connectedSockets[device.address]?.close()
             } catch (e: Exception) { /* ignore */ }
             connectedSockets.remove(device.address)
             connectedDeviceNames.remove(device.address)
        }

        val job = serviceScope.launch {
            try {
                // Start foreground with connecting notification (appending to existing list maybe? or just "Connecting...")
                val connectingMsg = getString(R.string.notification_connecting, deviceName)
                updateNotification(connectingMsg)
                startForeground(NOTIFICATION_ID, createNotification(connectingMsg))
                
                // Create socket and connect
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                
                withContext(Dispatchers.IO) {
                    socket.connect()
                }
                
                connectedSockets[device.address] = socket
                connectedDeviceNames[device.address] = deviceName
                
                // Update notification to include all connected devices
                updateNotificationWithConnectedDevices()
                
                withContext(Dispatchers.Main) {
                    onConnectionStateChanged?.invoke(true, device.address)
                    QuickConnectTileService.updateConnectionState(this@BluetoothConnectionService, true, device.address)
                }
                
                // Handle auto-disconnect
                if (autoDisconnect) {
                    launch {
                        delay(3000)
                        disconnectDevice(device.address)
                    }
                }
                
            } catch (e: IOException) {
                // Try fallback method
                try {
                    val fallbackSocket = device.javaClass
                        .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        .invoke(device, 1) as BluetoothSocket
                    
                    withContext(Dispatchers.IO) {
                        fallbackSocket.connect()
                    }
                    
                    connectedSockets[device.address] = fallbackSocket
                    connectedDeviceNames[device.address] = deviceName
                    
                    updateNotificationWithConnectedDevices()
                    
                    withContext(Dispatchers.Main) {
                        onConnectionStateChanged?.invoke(true, device.address)
                        QuickConnectTileService.updateConnectionState(this@BluetoothConnectionService, true, device.address)
                    }
                    
                    // Handle auto-disconnect for fallback too
                    if (autoDisconnect) {
                        launch {
                            delay(3000)
                            disconnectDevice(device.address)
                        }
                    }
                    
                } catch (fallbackException: Exception) {
                    handleConnectionError(device.address, e.message)
                }
            } catch (e: Exception) {
                handleConnectionError(device.address, e.message)
            } finally {
                connectingJobs.remove(device.address)
            }
        }
        connectingJobs[device.address] = job
    }

    private suspend fun handleConnectionError(address: String, message: String?) {
        withContext(Dispatchers.Main) {
            val errorMsg = getString(R.string.notification_connection_failed, message ?: "")
            onConnectionError?.invoke(errorMsg)
            onConnectionStateChanged?.invoke(false, address) // Pass address to indicate which failed
            QuickConnectTileService.updateConnectionState(this@BluetoothConnectionService, false, address)
        }
        // If no devices left, stop service?
        if (connectedSockets.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateNotificationWithConnectedDevices()
        }
    }
    
    private fun disconnectDevice(address: String) {
        // Cancel any pending connection attempt
        connectingJobs[address]?.cancel()
        connectingJobs.remove(address)
        
        try {
            connectedSockets[address]?.close()
        } catch (e: IOException) {
            // Ignore
        } finally {
            connectedSockets.remove(address)
            connectedDeviceNames.remove(address)
            
            // Notify persistent state
            serviceScope.launch(Dispatchers.Main) {
                 onConnectionStateChanged?.invoke(false, address)
                 QuickConnectTileService.updateConnectionState(this@BluetoothConnectionService, false, address)
            }
            
            if (connectedSockets.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                updateNotificationWithConnectedDevices()
            }
        }
    }

    fun disconnectAll() {
        connectingJobs.values.forEach { it.cancel() }
        connectingJobs.clear()
        
        connectedSockets.forEach { (address, socket) ->
            try {
                socket.close()
            } catch (e: IOException) {
                // Ignore
            }
            // Notify state update for each
            serviceScope.launch(Dispatchers.Main) {
                 onConnectionStateChanged?.invoke(false, address)
                 QuickConnectTileService.updateConnectionState(this@BluetoothConnectionService, false, address)
            }
        }
        connectedSockets.clear()
        connectedDeviceNames.clear()
    }
    
    fun isConnected(address: String): Boolean = connectedSockets[address]?.isConnected == true
    
    fun getConnectedDevices(): List<String> = connectedSockets.keys.toList()
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun updateNotificationWithConnectedDevices() {
        val names = connectedDeviceNames.values.joinToString(", ")
        val title = if (names.isEmpty()) getString(R.string.notification_title_default) else getString(R.string.notification_title_connected_prefix, names)
        val notification = createNotification(title)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(contentTitle: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val disconnectIntent = Intent(this, BluetoothConnectionService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setSmallIcon(R.drawable.ic_bluetooth_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, getString(R.string.action_disconnect_all), disconnectPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disconnectAll()
        serviceScope.cancel()
    }
}
