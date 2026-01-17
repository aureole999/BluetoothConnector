package com.example.bluetoothconnector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.bluetoothconnector.ui.MainScreen
import com.example.bluetoothconnector.ui.SettingsScreen
import com.example.bluetoothconnector.ui.theme.BluetoothConnectorTheme
import com.example.bluetoothconnector.viewmodel.BluetoothViewModel
import com.example.bluetoothconnector.viewmodel.SettingsViewModel

enum class Screen {
    MAIN,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    
    private val bluetoothViewModel: BluetoothViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    
    private var hasBluetoothPermission by mutableStateOf(false)
    private var currentScreen by mutableStateOf(Screen.MAIN)
    
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBluetoothPermission = permissions.values.all { it }
        if (hasBluetoothPermission) {
            bluetoothViewModel.checkBluetoothState(hasPermission = true)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        checkBluetoothPermissions()
        
        setContent {
            BluetoothConnectorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Handle back navigation from settings
                    BackHandler(enabled = currentScreen == Screen.SETTINGS) {
                        currentScreen = Screen.MAIN
                    }
                    
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            if (targetState == Screen.SETTINGS) {
                                slideInHorizontally { width -> width } togetherWith
                                        slideOutHorizontally { width -> -width }
                            } else {
                                slideInHorizontally { width -> -width } togetherWith
                                        slideOutHorizontally { width -> width }
                            }
                        },
                        label = "screenTransition"
                    ) { screen ->
                        when (screen) {
                            Screen.MAIN -> MainScreen(
                                viewModel = bluetoothViewModel,
                                hasBluetoothPermission = hasBluetoothPermission,
                                onRequestPermission = { requestBluetoothPermissions() },
                                onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = { currentScreen = Screen.MAIN }
                            )
                        }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        checkBluetoothPermissions()
        if (hasBluetoothPermission) {
            bluetoothViewModel.checkBluetoothState(hasPermission = true)
        }
    }
    
    private fun checkBluetoothPermissions() {
        hasBluetoothPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 11 and below
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        
        bluetoothPermissionLauncher.launch(permissions)
    }
}
