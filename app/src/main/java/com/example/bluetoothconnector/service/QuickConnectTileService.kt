package com.example.bluetoothconnector.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.bluetoothconnector.ui.DeviceSelectionActivity
import com.example.bluetoothconnector.R

import android.content.SharedPreferences

/**
 * Quick Settings Tile for one-tap Bluetooth connection
 */
class QuickConnectTileService : TileService() {
    
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Companion.KEY_CONNECTED_DEVICES || key == Companion.KEY_DEVICE_NAME) {
            updateTileState()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        updateTileState()
    }
    
    override fun onStopListening() {
        super.onStopListening()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceAddress = prefs.getString(KEY_DEVICE_ADDRESS, null)
        
        if (deviceAddress == null) {
            // No device selected, open selection activity
            val intent = Intent(this, DeviceSelectionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } else {
            // Check current connection state
            val connectedDevices = prefs.getStringSet(KEY_CONNECTED_DEVICES, emptySet()) ?: emptySet()
            val isConnectedToThisDevice = connectedDevices.contains(deviceAddress)
            
            if (isConnectedToThisDevice) {
                // Already connected to THIS device, so disconnect
                val intent = Intent(this, BluetoothConnectionService::class.java).apply {
                    action = BluetoothConnectionService.ACTION_DISCONNECT
                    putExtra(BluetoothConnectionService.EXTRA_DEVICE_ADDRESS, deviceAddress)
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                // Not connected to this device, so connect
                val deviceName = prefs.getString(KEY_DEVICE_NAME, getString(R.string.default_device_name))
                val autoDisconnect = prefs.getBoolean(KEY_TILE_AUTO_DISCONNECT, false)
                
                val intent = Intent(this, BluetoothConnectionService::class.java).apply {
                    action = BluetoothConnectionService.ACTION_CONNECT
                    val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    val device = adapter.getRemoteDevice(deviceAddress)
                    putExtra(BluetoothConnectionService.EXTRA_DEVICE, device)
                    putExtra(BluetoothConnectionService.EXTRA_DEVICE_NAME, deviceName)
                    putExtra(BluetoothConnectionService.EXTRA_AUTO_DISCONNECT, autoDisconnect)
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        }
    }
    private fun updateTileState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceName = prefs.getString(KEY_DEVICE_NAME, null)
        val deviceAddress = prefs.getString(KEY_DEVICE_ADDRESS, null)
        val connectedDevices = prefs.getStringSet(KEY_CONNECTED_DEVICES, emptySet()) ?: emptySet()
        
        qsTile?.apply {
            if (deviceName == null || deviceAddress == null) {
                state = Tile.STATE_INACTIVE
                label = getString(R.string.tile_label_unconfigured)
                subtitle = getString(R.string.tile_subtitle_unconfigured)
            } else {
                if (connectedDevices.contains(deviceAddress)) {
                    state = Tile.STATE_ACTIVE
                    label = deviceName
                    subtitle = getString(R.string.tile_label_connected)
                } else {
                    state = Tile.STATE_INACTIVE
                    label = deviceName
                    subtitle = getString(R.string.tile_label_default)
                }
            }
            updateTile()
        }
    }

    companion object {
        const val PREFS_NAME = "tile_prefs"
        const val KEY_DEVICE_ADDRESS = "device_address"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_CONNECTED_DEVICES = "connected_devices"
        const val KEY_TILE_AUTO_DISCONNECT = "tile_auto_disconnect"
        
        fun requestUpdate(context: Context) {
            requestListeningState(context, ComponentName(context, QuickConnectTileService::class.java))
        }
        
        fun updateConnectionState(context: Context, isConnected: Boolean, deviceAddress: String?) {
            if (deviceAddress == null) return
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentDevices = prefs.getStringSet(KEY_CONNECTED_DEVICES, emptySet())?.toMutableSet() ?: mutableSetOf()
            
            if (isConnected) {
                currentDevices.add(deviceAddress)
            } else {
                currentDevices.remove(deviceAddress)
            }
            
            prefs.edit().putStringSet(KEY_CONNECTED_DEVICES, currentDevices).apply()
            requestUpdate(context)
        }
    }
}
