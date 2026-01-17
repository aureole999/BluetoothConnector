package com.example.bluetoothconnector.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothconnector.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing settings
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = SettingsRepository(application)
    
    /**
     * Flow that emits the current auto-disconnect setting
     */
    val autoDisconnectEnabled: Flow<Boolean> = repository.autoDisconnectEnabled
    
    private val _tileConfiguredDeviceName = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val tileConfiguredDeviceName: kotlinx.coroutines.flow.StateFlow<String?> = _tileConfiguredDeviceName
    
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
}
