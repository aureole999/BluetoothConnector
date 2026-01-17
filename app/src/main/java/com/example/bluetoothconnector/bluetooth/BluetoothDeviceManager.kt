package com.example.bluetoothconnector.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Manages Bluetooth operations including getting paired devices and connecting to devices.
 */
class BluetoothDeviceManager(context: Context) {
    
    private val bluetoothManager: BluetoothManager? = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    
    private var currentSocket: BluetoothSocket? = null
    
    companion object {
        // Standard SerialPortService UUID for SPP (Serial Port Profile)
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    
    /**
     * Check if Bluetooth is available on this device
     */
    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null
    
    /**
     * Check if Bluetooth is currently enabled
     */
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
    
    /**
     * Get list of paired Bluetooth devices
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }
    
    /**
     * Connect to a Bluetooth device
     * @param device The device to connect to
     * @return Result indicating success or failure with error message
     */
    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Cancel any existing connection
            disconnect()
            
            // Create socket using SPP UUID
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            currentSocket = socket
            
            // Attempt connection
            socket.connect()
            
            Result.success(Unit)
        } catch (e: IOException) {
            // Try fallback method for some devices
            try {
                disconnect()
                val fallbackSocket = device.javaClass
                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
                currentSocket = fallbackSocket
                fallbackSocket.connect()
                Result.success(Unit)
            } catch (fallbackException: Exception) {
                Result.failure(IOException("接続に失敗しました: ${e.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect from the currently connected device
     */
    fun disconnect() {
        try {
            currentSocket?.close()
        } catch (e: IOException) {
            // Ignore close errors
        } finally {
            currentSocket = null
        }
    }
    
    /**
     * Check if currently connected to a device
     */
    fun isConnected(): Boolean = currentSocket?.isConnected == true
    
    /**
     * Get the currently connected socket (if any)
     */
    fun getConnectedSocket(): BluetoothSocket? = currentSocket
}
