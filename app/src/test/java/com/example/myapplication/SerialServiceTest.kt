package com.example.myapplication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SerialServiceTest {

    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockUsbManager: UsbManager
    @Mock private lateinit var mockUsbDevice: UsbDevice
    @Mock private lateinit var mockUsbConnection: UsbDeviceConnection
    @Mock private lateinit var mockUsbSerialDriver: UsbSerialDriver
    @Mock private lateinit var mockUsbSerialPort: UsbSerialPort
    
    private lateinit var serialService: SerialService
    private lateinit var permissionIntentCaptor: ArgumentCaptor<PendingIntent>
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Setup context to return UsbManager
        `when`(mockContext.getSystemService(Context.USB_SERVICE)).thenReturn(mockUsbManager)
        
        // Allow context to register receivers
        doNothing().`when`(mockContext).registerReceiver(any(), any(IntentFilter::class.java))
        
        // Setup mock USB device
        `when`(mockUsbDevice.deviceName).thenReturn("Mock FTDI Device")
        
        // Setup mock driver
        `when`(mockUsbSerialDriver.device).thenReturn(mockUsbDevice)
        `when`(mockUsbSerialDriver.ports).thenReturn(listOf(mockUsbSerialPort))
        
        // Setup permission intent captor
        permissionIntentCaptor = ArgumentCaptor.forClass(PendingIntent::class.java)
        
        // Create the service
        serialService = SerialService(mockContext)
    }
    
    @After
    fun tearDown() {
        serialService.cleanup()
    }
    
    @Test
    fun `test device discovery with no devices found`() = runTest {
        // Setup UsbManager to return empty list of drivers
        `when`(mockUsbManager.deviceList).thenReturn(HashMap<String, UsbDevice>())
        
        // Call discover devices
        serialService.discoverDevices()
        
        // Verify state transitions
        val finalState = serialService.connectionState.first()
        assertTrue(finalState is SerialService.ConnectionState.NoDevicesFound)
    }
    
    @Test
    fun `test device discovery with permission required`() = runTest {
        // Setup UsbManager to return our mock driver
        val drivers = listOf(mockUsbSerialDriver)
        mockUsbManagerToReturnDrivers(drivers)
        
        // Setup permission check to require permission
        `when`(mockUsbManager.hasPermission(mockUsbDevice)).thenReturn(false)
        
        // Call discover devices
        serialService.discoverDevices()
        
        // Verify permission was requested
        verify(mockUsbManager).requestPermission(eq(mockUsbDevice), permissionIntentCaptor.capture())
        
        // Verify state transitions
        val finalState = serialService.connectionState.first()
        assertTrue(finalState is SerialService.ConnectionState.RequestingPermission)
    }
    
    @Test
    fun `test device discovery with permission granted`() = runTest {
        // Setup UsbManager to return our mock driver
        val drivers = listOf(mockUsbSerialDriver)
        mockUsbManagerToReturnDrivers(drivers)
        
        // Setup permission check to have permission
        `when`(mockUsbManager.hasPermission(mockUsbDevice)).thenReturn(true)
        
        // Setup connection
        `when`(mockUsbManager.openDevice(mockUsbDevice)).thenReturn(mockUsbConnection)
        
        // Call discover devices
        serialService.discoverDevices()
        
        // Verify connection was opened
        verify(mockUsbManager).openDevice(mockUsbDevice)
        verify(mockUsbSerialPort).open(mockUsbConnection)
        verify(mockUsbSerialPort).setParameters(
            eq(9600),
            eq(UsbSerialPort.DATABITS_8),
            eq(UsbSerialPort.STOPBITS_1),
            eq(UsbSerialPort.PARITY_NONE)
        )
        
        // Verify state transitions
        val finalState = serialService.connectionState.first()
        assertTrue(finalState is SerialService.ConnectionState.Connected)
    }
    
    @Test
    fun `test connection error handling`() = runTest {
        // Setup UsbManager to return our mock driver
        val drivers = listOf(mockUsbSerialDriver)
        mockUsbManagerToReturnDrivers(drivers)
        
        // Setup permission check to have permission
        `when`(mockUsbManager.hasPermission(mockUsbDevice)).thenReturn(true)
        
        // Setup connection to throw exception
        `when`(mockUsbManager.openDevice(mockUsbDevice)).thenReturn(mockUsbConnection)
        doThrow(IOException("Mock connection error")).`when`(mockUsbSerialPort).open(any())
        
        // Call discover devices
        serialService.discoverDevices()
        
        // Verify error state
        val finalState = serialService.connectionState.first()
        assertTrue(finalState is SerialService.ConnectionState.Error)
        assertEquals("Mock connection error", (finalState as SerialService.ConnectionState.Error).message)
    }
    
    @Test
    fun `test close connection`() = runTest {
        // Setup UsbManager to return our mock driver
        val drivers = listOf(mockUsbSerialDriver)
        mockUsbManagerToReturnDrivers(drivers)
        
        // Setup permission check to have permission
        `when`(mockUsbManager.hasPermission(mockUsbDevice)).thenReturn(true)
        
        // Setup connection
        `when`(mockUsbManager.openDevice(mockUsbDevice)).thenReturn(mockUsbConnection)
        
        // Call discover devices to connect
        serialService.discoverDevices()
        
        // Close the connection
        serialService.closeConnection()
        
        // Verify port was closed
        verify(mockUsbSerialPort).close()
        
        // Verify state transitions
        val finalState = serialService.connectionState.first()
        assertTrue(finalState is SerialService.ConnectionState.Disconnected)
    }
    
    @Test
    fun `test send and read data`() = runTest {
        // Setup UsbManager to return our mock driver
        val drivers = listOf(mockUsbSerialDriver)
        mockUsbManagerToReturnDrivers(drivers)
        
        // Setup permission check to have permission
        `when`(mockUsbManager.hasPermission(mockUsbDevice)).thenReturn(true)
        
        // Setup connection
        `when`(mockUsbManager.openDevice(mockUsbDevice)).thenReturn(mockUsbConnection)
        
        // Setup read/write behavior
        `when`(mockUsbSerialPort.write(any(), anyInt())).thenReturn(Unit)
        doAnswer { invocation ->
            val buffer = invocation.getArgument<ByteArray>(0)
            val testData = "test".toByteArray()
            System.arraycopy(testData, 0, buffer, 0, testData.size)
            testData.size
        }.`when`(mockUsbSerialPort).read(any(), anyInt())
        
        // Call discover devices to connect
        serialService.discoverDevices()
        
        // Send data
        val sendResult = serialService.sendData("hello".toByteArray())
        assertTrue(sendResult)
        
        // Read data
        val readData = serialService.readData()
        assertEquals("test", readData?.let { String(it) })
    }
    
    @Test
    fun `test send and read event code with checksum`() = runTest {
        // Setup UsbManager to return our mock driver
        val drivers = listOf(mockUsbSerialDriver)
        mockUsbManagerToReturnDrivers(drivers)
        
        // Setup permission check to have permission
        `when`(mockUsbManager.hasPermission(mockUsbDevice)).thenReturn(true)
        
        // Setup connection
        `when`(mockUsbManager.openDevice(mockUsbDevice)).thenReturn(mockUsbConnection)
        
        // Call discover devices to connect
        serialService.discoverDevices()
        
        // Setup write behavior to capture the sent data
        val sentData = ArrayList<ByteArray>()
        doAnswer { invocation ->
            val data = invocation.getArgument<ByteArray>(0)
            sentData.add(data.clone())
            Unit
        }.`when`(mockUsbSerialPort).write(any(), anyInt())
        
        // Send event code
        val eventCode = "BUTTON_PRESS"
        val sendResult = serialService.sendEventCode(eventCode)
        assertTrue(sendResult)
        
        // Verify sent data includes checksum
        assertEquals(1, sentData.size)
        val sentBytes = sentData[0]
        assertEquals(eventCode.length + 1, sentBytes.size) // +1 for checksum
        
        // Extract event code and checksum
        val sentEventCodeBytes = sentBytes.copyOf(eventCode.length)
        val sentChecksum = sentBytes[eventCode.length]
        
        // Verify event code
        assertEquals(eventCode, String(sentEventCodeBytes))
        
        // Verify checksum is valid
        assertTrue(CRC8.validate(sentEventCodeBytes, sentChecksum))
        
        // Setup read behavior to return the sent data
        doAnswer { invocation ->
            val buffer = invocation.getArgument<ByteArray>(0)
            System.arraycopy(sentBytes, 0, buffer, 0, sentBytes.size)
            sentBytes.size
        }.`when`(mockUsbSerialPort).read(any(), anyInt())
        
        // Read event code
        val readEventCode = serialService.readEventCode()
        
        // Verify read event code
        assertEquals(eventCode, readEventCode)
    }
    
    @Test
    fun `test checksum validation failure`() = runTest {
        // Setup UsbManager to return our mock driver
        val drivers = listOf(mockUsbSerialDriver)
        mockUsbManagerToReturnDrivers(drivers)
        
        // Setup permission check to have permission
        `when`(mockUsbManager.hasPermission(mockUsbDevice)).thenReturn(true)
        
        // Setup connection
        `when`(mockUsbManager.openDevice(mockUsbDevice)).thenReturn(mockUsbConnection)
        
        // Call discover devices to connect
        serialService.discoverDevices()
        
        // Setup read behavior to return invalid checksum
        val eventCode = "BUTTON_PRESS"
        val invalidChecksum: Byte = (CRC8.calculate(eventCode) + 1).toByte() // Invalid checksum
        
        val dataWithInvalidChecksum = ByteArray(eventCode.length + 1)
        System.arraycopy(eventCode.toByteArray(), 0, dataWithInvalidChecksum, 0, eventCode.length)
        dataWithInvalidChecksum[eventCode.length] = invalidChecksum
        
        doAnswer { invocation ->
            val buffer = invocation.getArgument<ByteArray>(0)
            System.arraycopy(dataWithInvalidChecksum, 0, buffer, 0, dataWithInvalidChecksum.size)
            dataWithInvalidChecksum.size
        }.`when`(mockUsbSerialPort).read(any(), anyInt())
        
        // Read event code with invalid checksum
        val readEventCode = serialService.readEventCode()
        
        // Verify validation failed
        assertNull(readEventCode)
    }
    
    // Helper method to mock UsbManager to return specific drivers
    private fun mockUsbManagerToReturnDrivers(drivers: List<UsbSerialDriver>) {
        // This is a simplified mock - in a real test you would need to properly mock
        // the UsbSerialProber and its findAllDrivers method
        
        // For now, we're just setting up the basic behavior needed for the tests
        val deviceMap = HashMap<String, UsbDevice>().apply {
            put("0", mockUsbDevice)
        }
        `when`(mockUsbManager.deviceList).thenReturn(deviceMap)
    }
}
