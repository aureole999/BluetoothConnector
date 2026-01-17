package com.example.bluetoothconnector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bluetoothconnector.viewmodel.BluetoothUiState
import com.example.bluetoothconnector.viewmodel.BluetoothViewModel
import androidx.compose.ui.res.stringResource
import com.example.bluetoothconnector.R

/**
 * Main screen displaying paired Bluetooth devices
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: BluetoothViewModel,
    hasBluetoothPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    // Refresh devices when permission is granted
    LaunchedEffect(hasBluetoothPermission) {
        if (hasBluetoothPermission) {
            viewModel.checkBluetoothState(hasPermission = true)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_title_devices),
                        fontWeight = FontWeight.Medium
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (hasBluetoothPermission && uiState.isBluetoothEnabled) {
                FloatingActionButton(
                    onClick = { viewModel.loadPairedDevices() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh)
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !hasBluetoothPermission -> {
                    PermissionRequiredContent(onRequestPermission = onRequestPermission)
                }
                !uiState.isBluetoothAvailable -> {
                    BluetoothNotAvailableContent()
                }
                !uiState.isBluetoothEnabled -> {
                    BluetoothDisabledContent()
                }
                uiState.isLoading -> {
                    LoadingContent()
                }
                uiState.pairedDevices.isEmpty() -> {
                    EmptyDevicesContent()
                }
                else -> {
                    DeviceListContent(
                        uiState = uiState,
                        onDeviceClick = { device ->
                            if (uiState.connectedDeviceAddresses.contains(device.address)) {
                                viewModel.disconnect(device.address)
                            } else {
                                viewModel.connectToDevice(device)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceListContent(
    uiState: BluetoothUiState,
    onDeviceClick: (com.example.bluetoothconnector.viewmodel.BluetoothDeviceInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.header_paired_devices),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        
        items(
            items = uiState.pairedDevices,
            key = { it.address }
        ) { device ->
            val connectionState = when {
                uiState.connectingDeviceAddresses.contains(device.address) -> ConnectionState.CONNECTING
                uiState.connectedDeviceAddresses.contains(device.address) -> ConnectionState.CONNECTED
                else -> ConnectionState.DISCONNECTED
            }
            
            // Only pass countdown for the connected device
            val countdown = uiState.autoDisconnectCountdown[device.address]
            
            BluetoothDeviceItem(
                deviceInfo = device,
                connectionState = connectionState,
                autoDisconnectCountdown = countdown,
                onClick = { onDeviceClick(device) }
            )
        }
    }
}

@Composable
private fun PermissionRequiredContent(onRequestPermission: () -> Unit) {
    CenteredMessage(
        icon = Icons.Default.Bluetooth,
        title = stringResource(R.string.title_permission_required),
        message = stringResource(R.string.msg_permission_required)
    )
    
    LaunchedEffect(Unit) {
        onRequestPermission()
    }
}

@Composable
private fun BluetoothNotAvailableContent() {
    CenteredMessage(
        icon = Icons.Default.BluetoothDisabled,
        title = stringResource(R.string.title_bluetooth_not_available),
        message = stringResource(R.string.msg_bluetooth_not_available)
    )
}

@Composable
private fun BluetoothDisabledContent() {
    CenteredMessage(
        icon = Icons.Default.BluetoothDisabled,
        title = stringResource(R.string.title_bluetooth_disabled),
        message = stringResource(R.string.msg_bluetooth_disabled)
    )
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyDevicesContent() {
    CenteredMessage(
        icon = Icons.Default.Bluetooth,
        title = stringResource(R.string.title_no_devices),
        message = stringResource(R.string.msg_no_devices)
    )
}

@Composable
private fun CenteredMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
