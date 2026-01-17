package com.example.bluetoothconnector.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothconnector.data.SettingsRepository
import com.example.bluetoothconnector.update.UpdateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Update check state
 */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val info: UpdateManager.UpdateInfo) : UpdateState()
    object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
}

/**
 * ViewModel for managing settings
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = SettingsRepository(application)
    private val updateManager = UpdateManager(application)
    
    /**
     * Flow that emits the current auto-disconnect setting
     */
    val autoDisconnectEnabled: Flow<Boolean> = repository.autoDisconnectEnabled
    
    private val _tileConfiguredDeviceName = MutableStateFlow<String?>(null)
    val tileConfiguredDeviceName: StateFlow<String?> = _tileConfiguredDeviceName
    
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState
    
    // Add reference to TileService prefs (this should ideally be in repository but simple enough here)
    private val tilePrefs by lazy {
        application.getSharedPreferences(com.example.bluetoothconnector.service.QuickConnectTileService.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
    
    private val tilePrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == com.example.bluetoothconnector.service.QuickConnectTileService.KEY_DEVICE_NAME) {
            updateTileStatus()
        }
    }
    
    init {
        tilePrefs.registerOnSharedPreferenceChangeListener(tilePrefsListener)
        updateTileStatus()
    }
    
    private fun updateTileStatus() {
        val name = tilePrefs.getString(com.example.bluetoothconnector.service.QuickConnectTileService.KEY_DEVICE_NAME, null)
        viewModelScope.launch {
            _tileConfiguredDeviceName.emit(name)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        tilePrefs.unregisterOnSharedPreferenceChangeListener(tilePrefsListener)
    }
    
    /**
     * Reset the Tile configuration
     */
    fun resetTileConfiguration() {
        tilePrefs.edit().clear().apply()
        // Force update UI state immediately because sometimes listener might lag or not trigger for clear() depending on OS version behavior or key check
        updateTileStatus()
        // Force update tile state
        com.example.bluetoothconnector.service.QuickConnectTileService.requestUpdate(getApplication())
    }

    /**
     * Update the auto-disconnect setting
     */
    fun setAutoDisconnect(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoDisconnect(enabled)
        }
    }
    
    /**
     * Check for app updates
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            
            val result = updateManager.checkForUpdate()
            
            _updateState.value = result.fold(
                onSuccess = { info ->
                    if (info.hasUpdate) {
                        UpdateState.Available(info)
                    } else {
                        UpdateState.UpToDate
                    }
                },
                onFailure = { error ->
                    UpdateState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }
    
    /**
     * Download and install update
     */
    fun downloadUpdate(info: UpdateManager.UpdateInfo) {
        updateManager.downloadAndInstall(info.downloadUrl, info.versionName)
    }
    
    /**
     * Get current app version
     */
    fun getCurrentVersion(): String {
        return try {
            getApplication<Application>().packageManager
                .getPackageInfo(getApplication<Application>().packageName, 0)
                .versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
