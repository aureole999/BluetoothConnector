package com.example.bluetoothconnector.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bluetoothconnector.viewmodel.SettingsViewModel
import com.example.bluetoothconnector.viewmodel.UpdateState
import androidx.compose.ui.res.stringResource
import com.example.bluetoothconnector.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val autoDisconnectEnabled by viewModel.autoDisconnectEnabled.collectAsState(initial = false)
    val updateState by viewModel.updateState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_settings),
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Connection settings section
            Text(
                text = stringResource(R.string.header_connection_settings),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            
            // Auto-disconnect setting
            SettingsSwitch(
                icon = Icons.Default.Timer,
                title = stringResource(R.string.setting_auto_disconnect_title),
                subtitle = stringResource(R.string.setting_auto_disconnect_subtitle),
                checked = autoDisconnectEnabled,
                onCheckedChange = { viewModel.setAutoDisconnect(it) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Info text
            Text(
                text = stringResource(R.string.hint_automation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Tile settings section
            Text(
                text = stringResource(R.string.header_quick_settings_tile),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            
            val tileDeviceName by viewModel.tileConfiguredDeviceName.collectAsState(initial = null)
            
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_reset_tile_title)) },
                supportingContent = { 
                    val notConfigured = stringResource(R.string.setting_not_configured)
                    val prefix = stringResource(R.string.setting_current_config_prefix)
                    Text(if (tileDeviceName != null) String.format(prefix, tileDeviceName) else notConfigured)
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Button(
                        onClick = { viewModel.resetTileConfiguration() },
                        enabled = tileDeviceName != null
                    ) {
                        Text(stringResource(R.string.btn_reset))
                    }
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // About section
            Text(
                text = stringResource(R.string.header_about),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            
            // Version info and update check
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_check_update_title)) },
                supportingContent = { 
                    val statusText = when (val state = updateState) {
                        is UpdateState.Idle -> stringResource(R.string.setting_current_version, viewModel.getCurrentVersion())
                        is UpdateState.Checking -> stringResource(R.string.update_checking)
                        is UpdateState.Available -> stringResource(R.string.update_available, state.info.versionName)
                        is UpdateState.UpToDate -> stringResource(R.string.update_not_available)
                        is UpdateState.Error -> stringResource(R.string.update_error)
                    }
                    Text(statusText)
                },
                leadingContent = {
                    if (updateState is UpdateState.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Update,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                trailingContent = {
                    when (val state = updateState) {
                        is UpdateState.Available -> {
                            Button(onClick = { viewModel.downloadUpdate(state.info) }) {
                                Text(stringResource(R.string.btn_download))
                            }
                        }
                        is UpdateState.Checking -> { /* No button while checking */ }
                        else -> {
                            Button(onClick = { viewModel.checkForUpdates() }) {
                                Text(stringResource(R.string.btn_update))
                            }
                        }
                    }
                }
            )
        }
    }
}

/**
 * Reusable settings switch row
 */
@Composable
private fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

