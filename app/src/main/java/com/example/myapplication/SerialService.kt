package com.example.myapplication

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Service for managing USB serial connections, specifically for FTDI devices.
 * Handles device detection, permission requests, and connection lifecycle.
 */
class SerialService(private val context: Context) {
    private val TAG = "SerialService"
    private val ACTION_USB_PERMISSION = "com.example.myapplication.USB_PERMISSION"
    
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    private var usbDevice: UsbDevice? = null
    private var usbSerialDriver: UsbSerialDriver? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var connected = false
    
    // Connection state flow that can be observed by UI components
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    // USB permission receiver
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            Log.d(TAG, "Permission granted for device: ${it.deviceName}")
                            connectToDevice(it)
                        }
                    } else {
                        Log.d(TAG, "Permission denied for device")
                        _connectionState.value = ConnectionState.PermissionDenied
                    }
                }
            }
        }
    }
    
    // USB device detach receiver
    private val usbDetachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                device?.let {
                    if (it == usbDevice) {
                        Log.d(TAG, "USB device detached: ${it.deviceName}")
                        closeConnection()
                    }
                }
            }
        }
    }
    
    init {
        // Register receivers for USB permission and detach events
        val permissionFilter = IntentFilter(ACTION_USB_PERMISSION)
        context.registerReceiver(usbPermissionReceiver, permissionFilter)
        
        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        context.registerReceiver(usbDetachReceiver, detachFilter)
    }
    
    /**
     * Starts device discovery process
     */
    fun discoverDevices() {
        coroutineScope.launch {
            _connectionState.value = ConnectionState.Discovering
            
            // Create a custom probe table that focuses on FTDI devices
            val customTable = ProbeTable()
            customTable.addProduct(0x0403, 0x6001, FtdiSerialDriver::class.java) // FTDI FT232R
            customTable.addProduct(0x0403, 0x6010, FtdiSerialDriver::class.java) // FTDI FT2232H
            customTable.addProduct(0x0403, 0x6011, FtdiSerialDriver::class.java) // FTDI FT4232H
            customTable.addProduct(0x0403, 0x6014, FtdiSerialDriver::class.java) // FTDI FT232H
            
            val prober = UsbSerialProber(customTable)
            
            // Find all available drivers from attached devices
            val availableDrivers = prober.findAllDrivers(usbManager)
            
            if (availableDrivers.isEmpty()) {
                Log.d(TAG, "No USB serial devices found")
                _connectionState.value = ConnectionState.NoDevicesFound
                return@launch
            }
            
            // Get the first available driver
            val driver = availableDrivers[0]
            usbDevice = driver.device
            usbSerialDriver = driver
            
            Log.d(TAG, "Found USB device: ${usbDevice?.deviceName}")
            
            // Request permission if needed
            if (!usbManager.hasPermission(usbDevice)) {
                val permissionIntent = PendingIntent.getBroadcast(
                    context, 0, Intent(ACTION_USB_PERMISSION), 
                    PendingIntent.FLAG_IMMUTABLE
                )
                usbManager.requestPermission(usbDevice, permissionIntent)
                _connectionState.value = ConnectionState.RequestingPermission
            } else {
                // Already have permission, connect directly
                connectToDevice(usbDevice!!)
            }
        }
    }
    
    /**
     * Connects to the specified USB device
     */
    private fun connectToDevice(device: UsbDevice) {
        coroutineScope.launch {
            try {
                _connectionState.value = ConnectionState.Connecting
                
                val connection = usbManager.openDevice(device)
                if (connection == null) {
                    Log.e(TAG, "Failed to open connection to device: ${device.deviceName}")
                    _connectionState.value = ConnectionState.Error("Failed to open connection")
                    return@launch
                }
                
                // Get the first port (most devices have just one)
                val port = usbSerialDriver!!.ports[0]
                usbSerialPort = port
                
                // Open the port with default parameters
                port.open(connection)
                port.setParameters(9600, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                
                connected = true
                _connectionState.value = ConnectionState.Connected
                Log.d(TAG, "Connected to device: ${device.deviceName}")
            } catch (e: IOException) {
                Log.e(TAG, "Error connecting to device", e)
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
                closeConnection()
            }
        }
    }
    
    /**
     * Closes the current connection
     */
    fun closeConnection() {
        coroutineScope.launch {
            try {
                usbSerialPort?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing connection", e)
            } finally {
                usbSerialPort = null
                connected = false
                _connectionState.value = ConnectionState.Disconnected
                Log.d(TAG, "Connection closed")
            }
        }
    }
    
    /**
     * Cleans up resources when the service is no longer needed
     */
    fun cleanup() {
        closeConnection()
        try {
            context.unregisterReceiver(usbPermissionReceiver)
            context.unregisterReceiver(usbDetachReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
            Log.e(TAG, "Error unregistering receivers", e)
        }
    }
    
    /**
     * Sends data to the connected device
     */
    fun sendData(data: ByteArray): Boolean {
        if (!connected || usbSerialPort == null) {
            Log.e(TAG, "Cannot send data: not connected")
            return false
        }
        
        return try {
            usbSerialPort!!.write(data, 1000) // 1000ms timeout
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error sending data", e)
            false
        }
    }
    
    /**
     * Reads data from the connected device
     */
    fun readData(maxLength: Int = 1024): ByteArray? {
        if (!connected || usbSerialPort == null) {
            Log.e(TAG, "Cannot read data: not connected")
            return null
        }
        
        val buffer = ByteArray(maxLength)
        return try {
            val len = usbSerialPort!!.read(buffer, 1000) // 1000ms timeout
            if (len > 0) {
                buffer.copyOf(len)
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading data", e)
            null
        }
    }
    
    /**
     * Connection states for the SerialService
     */
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Discovering : ConnectionState()
        object NoDevicesFound : ConnectionState()
        object RequestingPermission : ConnectionState()
        object PermissionDenied : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}
