package com.example.bluetoothconnector.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bluetoothconnector.R
import com.example.bluetoothconnector.viewmodel.SettingsViewModel
import com.example.bluetoothconnector.viewmodel.UpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val autoDisconnectEnabled by viewModel.autoDisconnectEnabled.collectAsState(initial = false)
    val updateState by viewModel.updateState.collectAsState()
    val tileDeviceName by viewModel.tileConfiguredDeviceName.collectAsState(initial = null)
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Connection Settings Section ---
            SectionHeader(stringResource(R.string.header_connection_settings))
            
            // Auto-disconnect setting
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_auto_disconnect_title)) },
                supportingContent = { Text(stringResource(R.string.setting_auto_disconnect_subtitle)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(
                        checked = autoDisconnectEnabled,
                        onCheckedChange = { viewModel.setAutoDisconnect(it) }
                    )
                },
                modifier = Modifier.clickable { viewModel.setAutoDisconnect(!autoDisconnectEnabled) }
            )
            
            // Hint text
            Text(
                text = stringResource(R.string.hint_automation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // --- Quick Settings Tile Section ---
            SectionHeader(stringResource(R.string.header_quick_settings_tile))
            
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_reset_tile_title)) },
                supportingContent = { 
                    val notConfigured = stringResource(R.string.setting_not_configured)
                    val prefix = stringResource(R.string.setting_current_config_prefix)
                    Text(if (tileDeviceName != null) String.format(prefix, tileDeviceName) else notConfigured)
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    TextButton(
                        onClick = { viewModel.resetTileConfiguration() },
                        enabled = tileDeviceName != null
                    ) {
                        Text(stringResource(R.string.btn_reset))
                    }
                }
            )
            
            // --- About Section ---
            SectionHeader(stringResource(R.string.header_about))
            
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
                            TextButton(onClick = { viewModel.downloadUpdate(state.info) }) {
                                Text(stringResource(R.string.btn_download))
                            }
                        }
                        is UpdateState.Checking -> { /* No button while checking */ }
                        else -> {
                            TextButton(onClick = { viewModel.checkForUpdates() }) {
                                Text(stringResource(R.string.btn_update))
                            }
                        }
                    }
                },
                modifier = Modifier.clickable { 
                    if (updateState !is UpdateState.Checking) {
                        viewModel.checkForUpdates()
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}
