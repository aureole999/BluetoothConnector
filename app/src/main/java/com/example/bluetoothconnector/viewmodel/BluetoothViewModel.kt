package com.example.bluetoothconnector.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothconnector.bluetooth.BluetoothDeviceManager
import com.example.bluetoothconnector.data.SettingsRepository
import com.example.bluetoothconnector.R
import com.example.bluetoothconnector.service.BluetoothConnectionService
import com.example.bluetoothconnector.service.QuickConnectTileService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * UI state for the Bluetooth screen
 */
data class BluetoothUiState(
    val pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val isBluetoothEnabled: Boolean = false,
    val isBluetoothAvailable: Boolean = false,
    val connectingDeviceAddresses: Set<String> = emptySet(),
    val connectedDeviceAddresses: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val autoDisconnectCountdown: Map<String, Int> = emptyMap() // address -> countdown
)

/**
 * Bluetooth device information for UI display
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val majorDeviceClass: Int,
    val bondState: Int,
    val device: BluetoothDevice
)

/**
 * ViewModel for managing Bluetooth UI state and operations
 */
class BluetoothViewModel(application: Application) : AndroidViewModel(application) {
    
    private val bluetoothManager = BluetoothDeviceManager(application)
    private val settingsRepository = SettingsRepository(application)
    
    private val _uiState = MutableStateFlow(BluetoothUiState())
    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()
    
    private var bluetoothService: BluetoothConnectionService? = null
    private var serviceBound = false
    private val countdownJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothConnectionService.LocalBinder
            bluetoothService = binder.getService().apply {
                onConnectionStateChanged = { connected, address ->
                    viewModelScope.launch {
                        if (address != null) {
                            val currentConnected = _uiState.value.connectedDeviceAddresses.toMutableSet()
                            val currentConnecting = _uiState.value.connectingDeviceAddresses.toMutableSet()
                            
                            // Remove from connecting state regardless of result
                            currentConnecting.remove(address)
                            
                            if (connected) {
                                currentConnected.add(address)
                                
                                // Check if auto-disconnect is enabled
                                val autoDisconnectEnabled = settingsRepository.autoDisconnectEnabled.first()
                                if (autoDisconnectEnabled) {
                                    startAutoDisconnectCountdown(address)
                                }
                            } else {
                                currentConnected.remove(address)
                            }
                            
                            _uiState.value = _uiState.value.copy(
                                connectingDeviceAddresses = currentConnecting,
                                connectedDeviceAddresses = currentConnected,
                                errorMessage = null
                            )
                        }
                    }
                }
                onConnectionError = { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error
                    )
                }
            }
            serviceBound = true
            
            // Sync initial state
            viewModelScope.launch {
                val connectedDevices = bluetoothService?.getConnectedDevices() ?: emptyList()
                if (connectedDevices.isNotEmpty()) {
                    val currentConnected = _uiState.value.connectedDeviceAddresses.toMutableSet()
                    currentConnected.addAll(connectedDevices)
                    _uiState.value = _uiState.value.copy(
                        connectedDeviceAddresses = currentConnected
                    )
                }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            serviceBound = false
        }
    }
    
    init {
        // Observe connection state from SharedPreferences (same source as Tile)
        observeConnectionState()
    }
    
    private fun observeConnectionState() {
        val prefs = getApplication<Application>().getSharedPreferences(
            QuickConnectTileService.PREFS_NAME, 
            Context.MODE_PRIVATE
        )
        prefs.registerOnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == QuickConnectTileService.KEY_CONNECTED_DEVICES) {
                val connectedSet = sharedPreferences.getStringSet(
                    QuickConnectTileService.KEY_CONNECTED_DEVICES, 
                    emptySet()
                ) ?: emptySet()
                _uiState.value = _uiState.value.copy(
                    connectedDeviceAddresses = connectedSet
                )
            }
        }
        // Initial sync
        val connectedSet = prefs.getStringSet(
            QuickConnectTileService.KEY_CONNECTED_DEVICES, 
            emptySet()
        ) ?: emptySet()
        _uiState.value = _uiState.value.copy(
            connectedDeviceAddresses = connectedSet
        )
    }
    
    private fun bindServiceIfNeeded() {
        if (!serviceBound) {
            val intent = Intent(getApplication(), BluetoothConnectionService::class.java)
            getApplication<Application>().bindService(intent, serviceConnection, 0) // No BIND_AUTO_CREATE
        }
    }
    
    /**
     * Check Bluetooth availability and enabled state, and load devices if permission is granted
     */
    fun checkBluetoothState(hasPermission: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            isBluetoothAvailable = bluetoothManager.isBluetoothAvailable(),
            isBluetoothEnabled = bluetoothManager.isBluetoothEnabled()
        )
        
        if (hasPermission && bluetoothManager.isBluetoothEnabled()) {
            loadPairedDevices()
        }
    }
    
    /**
     * Load paired devices from Bluetooth adapter
     */
    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        val context = getApplication<Application>()
        val devices = bluetoothManager.getPairedDevices().map { device ->
            BluetoothDeviceInfo(
                name = device.name ?: context.getString(R.string.device_unknown),
                address = device.address,
                majorDeviceClass = device.bluetoothClass?.majorDeviceClass ?: 0,
                bondState = device.bondState,
                device = device
            )
        }
        
        _uiState.value = _uiState.value.copy(
            pairedDevices = devices,
            isLoading = false
        )
    }
    
    /**
     * Connect to a Bluetooth device
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceInfo: BluetoothDeviceInfo) {
        // Don't connect if already connecting or connected to this device
        if (_uiState.value.connectingDeviceAddresses.contains(deviceInfo.address) ||
            _uiState.value.connectedDeviceAddresses.contains(deviceInfo.address)) {
            return
        }
        
        val newConnecting = _uiState.value.connectingDeviceAddresses.toMutableSet().apply {
            add(deviceInfo.address)
        }
        
        _uiState.value = _uiState.value.copy(
            connectingDeviceAddresses = newConnecting,
            errorMessage = null
        )
        
        // Start foreground service for the connection
        val context = getApplication<Application>()
        val intent = Intent(context, BluetoothConnectionService::class.java).apply {
            action = BluetoothConnectionService.ACTION_CONNECT
            putExtra(BluetoothConnectionService.EXTRA_DEVICE, deviceInfo.device)
            putExtra(BluetoothConnectionService.EXTRA_DEVICE_NAME, deviceInfo.name)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
    
    /**
     * Start auto-disconnect countdown (3 seconds) for a specific device
     */
    private fun startAutoDisconnectCountdown(address: String) {
        // Cancel any existing countdown for this address
        countdownJobs[address]?.cancel()
        
        val job = viewModelScope.launch {
            for (countdown in 3 downTo 1) {
                val currentCountdowns = _uiState.value.autoDisconnectCountdown.toMutableMap()
                currentCountdowns[address] = countdown
                _uiState.value = _uiState.value.copy(autoDisconnectCountdown = currentCountdowns)
                delay(1000)
            }
            // Auto disconnect after countdown
            disconnect(address)
            
            // Clear countdown
            val cleanupCountdowns = _uiState.value.autoDisconnectCountdown.toMutableMap()
            cleanupCountdowns.remove(address)
            _uiState.value = _uiState.value.copy(autoDisconnectCountdown = cleanupCountdowns)
            countdownJobs.remove(address)
        }
        countdownJobs[address] = job
    }
    
    /**
     * Disconnect from a specific connected device
     */
    fun disconnect(address: String) {
        // Cancel auto-disconnect countdown if running
        countdownJobs[address]?.cancel()
        countdownJobs.remove(address)
        
        // Clear countdown from UI
        val cleanupCountdowns = _uiState.value.autoDisconnectCountdown.toMutableMap()
        cleanupCountdowns.remove(address)
        _uiState.value = _uiState.value.copy(autoDisconnectCountdown = cleanupCountdowns)
        
        val context = getApplication<Application>()
        val intent = Intent(context, BluetoothConnectionService::class.java).apply {
            action = BluetoothConnectionService.ACTION_DISCONNECT
            putExtra(BluetoothConnectionService.EXTRA_DEVICE_ADDRESS, address)
        }
        context.startService(intent)
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    override fun onCleared() {
        super.onCleared()
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        // Note: We don't disconnect here - let the service maintain the connection
    }
}
