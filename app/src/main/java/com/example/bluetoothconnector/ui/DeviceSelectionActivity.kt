package com.example.bluetoothconnector.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.bluetoothconnector.R
import com.example.bluetoothconnector.service.QuickConnectTileService

/**
 * Activity presented as a dialog to select a device for the Quick Settings Tile
 */
class DeviceSelectionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    DeviceSelectionScreen(
                        onDeviceSelected = { device ->
                            saveSelection(device)
                            finish()
                        },
                        onCancel = {
                            finish()
                        }
                    )
                }
            }
        }
    }
    
    private fun saveSelection(device: BluetoothDevice) {
        val prefs = getSharedPreferences(QuickConnectTileService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(QuickConnectTileService.KEY_DEVICE_ADDRESS, device.address)
            putString(QuickConnectTileService.KEY_DEVICE_NAME, device.name ?: getString(R.string.device_unknown))
            apply()
        }
        
        // Notify TileService to update
        QuickConnectTileService.requestUpdate(this)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceSelectionScreen(
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        
        if (adapter != null && adapter.isEnabled) {
             // Check permission for API 31+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    devices = adapter.bondedDevices.toList()
                }
            } else {
                devices = adapter.bondedDevices.toList()
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.title_select_device),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = stringResource(R.string.msg_select_device),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            if (devices.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.msg_no_paired_devices),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(devices) { device ->
                    DeviceItem(device = device, onClick = { onDeviceSelected(device) })
                    HorizontalDivider()
                }
            }
        }
        
        var autoDisconnect by remember { mutableStateOf(false) }
        
        // Load initial state
        LaunchedEffect(Unit) {
            val prefs = context.getSharedPreferences(QuickConnectTileService.PREFS_NAME, Context.MODE_PRIVATE)
            autoDisconnect = prefs.getBoolean(QuickConnectTileService.KEY_TILE_AUTO_DISCONNECT, false)
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable { autoDisconnect = !autoDisconnect },
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Checkbox(
                checked = autoDisconnect,
                onCheckedChange = { autoDisconnect = it }
            )
            Text(
                text = stringResource(R.string.checkbox_auto_disconnect_3s),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
            
            // Add a save logic to device selection since checkbox state is separate
            // Actually, we pass the checkbox state to saveSelection indirectly by saving it immediately or 
            // when device is selected. Let's save on device select.
            // But wait, onDeviceSelected callback only takes device.
            // We need to update saving logic locally here or update callback signature.
            // Let's update preference immediately on change? Or update callback?
            // Easier: Update callback to save prefs based on local state.
        }
        
        // Update onDeviceSelected usage to capture current autoDisconnect state
        // Since onDeviceSelected is a lambda passed from Activity, we should probably handle saving there
        // or just update prefs here.
        // Let's modify saveSelection in Activity to read checkbox state or 
        // simply save the checkbox state whenever it changes?
        // Let's save checkbox state when it changes for simplicity, or save it right before calling callback.
        
        LaunchedEffect(autoDisconnect) {
             val prefs = context.getSharedPreferences(QuickConnectTileService.PREFS_NAME, Context.MODE_PRIVATE)
             prefs.edit().putBoolean(QuickConnectTileService.KEY_TILE_AUTO_DISCONNECT, autoDisconnect).apply()
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = device.name ?: stringResource(R.string.device_unknown),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
