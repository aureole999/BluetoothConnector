package com.example.bluetoothconnector.ui

import android.bluetooth.BluetoothClass
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ripple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.bluetoothconnector.R
import com.example.bluetoothconnector.viewmodel.BluetoothDeviceInfo

/**
 * Connection state enum for UI display
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

/**
 * A single Bluetooth device item in the list - Material 3 style
 */
@Composable
fun BluetoothDeviceItem(
    deviceInfo: BluetoothDeviceInfo,
    connectionState: ConnectionState,
    autoDisconnectCountdown: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnecting = connectionState == ConnectionState.CONNECTING
    val isConnected = connectionState == ConnectionState.CONNECTED
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = !isConnecting,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            ),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon
            DeviceIconContainer(
                majorDeviceClass = deviceInfo.majorDeviceClass,
                connectionState = connectionState
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = deviceInfo.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Status text with auto-disconnect countdown
                val statusText = when {
                    isConnected && autoDisconnectCountdown != null -> 
                        stringResource(R.string.status_connected_auto_disconnect, autoDisconnectCountdown)
                    isConnected -> stringResource(R.string.status_connected)
                    isConnecting -> stringResource(R.string.status_connecting)
                    else -> stringResource(R.string.status_saved)
                }
                
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isConnected && autoDisconnectCountdown != null -> 
                            MaterialTheme.colorScheme.tertiary
                        isConnected -> MaterialTheme.colorScheme.primary
                        isConnecting -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            // Trailing indicator
            TrailingIndicator(
                connectionState = connectionState,
                autoDisconnectCountdown = autoDisconnectCountdown
            )
        }
    }
}

/**
 * Device icon with connection state indicator
 */
@Composable
private fun DeviceIconContainer(
    majorDeviceClass: Int,
    connectionState: ConnectionState
) {
    val iconSize by animateDpAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTING -> 36.dp
            else -> 40.dp
        },
        animationSpec = tween(200),
        label = "iconSize"
    )
    
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        // Connecting progress ring
        if (connectionState == ConnectionState.CONNECTING) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }
        
        // Icon background circle
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(
                    when (connectionState) {
                        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                        ConnectionState.CONNECTING -> MaterialTheme.colorScheme.surface
                        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getDeviceIcon(majorDeviceClass),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when (connectionState) {
                    ConnectionState.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                    ConnectionState.CONNECTING -> MaterialTheme.colorScheme.primary
                    ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Trailing indicator showing connection status
 */
@Composable
private fun TrailingIndicator(
    connectionState: ConnectionState,
    autoDisconnectCountdown: Int? = null
) {
    AnimatedContent(
        targetState = Pair(connectionState, autoDisconnectCountdown),
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith
                    fadeOut(animationSpec = tween(200))
        },
        label = "trailingIndicator"
    ) { (state, countdown) ->
        when {
            state == ConnectionState.CONNECTED && countdown != null -> {
                // Show countdown number
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = countdown.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                }
            }
            state == ConnectionState.CONNECTED -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.cd_connected),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            state == ConnectionState.CONNECTING -> {
                // Empty - the progress is shown around the icon
                Spacer(modifier = Modifier.size(24.dp))
            }
            else -> {
                // Empty for disconnected state
                Spacer(modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * Get appropriate icon based on Bluetooth major device class
 */
@Composable
private fun getDeviceIcon(majorDeviceClass: Int): ImageVector {
    return when (majorDeviceClass) {
        BluetoothClass.Device.Major.COMPUTER -> Icons.Default.Laptop
        BluetoothClass.Device.Major.PHONE -> Icons.Default.Phone
        BluetoothClass.Device.Major.NETWORKING -> Icons.Default.Router
        BluetoothClass.Device.Major.AUDIO_VIDEO -> Icons.Default.Headphones
        BluetoothClass.Device.Major.PERIPHERAL -> Icons.Default.Keyboard
        BluetoothClass.Device.Major.WEARABLE -> Icons.Default.Watch
        BluetoothClass.Device.Major.TOY -> Icons.Default.Toys
        BluetoothClass.Device.Major.HEALTH -> Icons.Default.MonitorHeart
        0x1F00 -> Icons.Default.DirectionsCar
        else -> Icons.Default.Bluetooth
    }
}
