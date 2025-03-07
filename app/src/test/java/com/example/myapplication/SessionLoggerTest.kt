package com.example.myapplication

import kotlinx.coroutines.*
import org.json.JSONArray
import org.junit.*
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30]) // Specify Android SDK version for testing
class SessionLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()
    
    private lateinit var timestampProvider: MockTimestampProvider
    private lateinit var csvLogger: SessionLogger
    private lateinit var jsonLogger: SessionLogger

    class MockTimestampProvider : TimestampProvider() {
        private var currentTime = 1000L
        private val startTime = currentTime

        override fun getAbsoluteTime(): String {
            return "2023-10-05T14:30:${(currentTime / 1000) % 60}.${currentTime % 1000}Z"
        }

        override fun getRelativeTimeMillis(): Long {
            return currentTime - startTime
        }

        fun advanceTime(milliseconds: Long) {
            currentTime += milliseconds
        }
    }

    @Before
    fun setUp() {
        ShadowLog.stream = System.out // Enables logging in Robolectric tests

        timestampProvider = MockTimestampProvider()
        csvLogger = SessionLogger(timestampProvider, SessionLogger.LogFormat.CSV)
        jsonLogger = SessionLogger(timestampProvider, SessionLogger.LogFormat.JSON)
    }

    @After
    fun tearDown() {
        runBlocking {
            csvLogger.clearBuffer()
            jsonLogger.clearBuffer()
        }
    }

    @Test
    fun testLogEvent() = runBlocking {
        val event = csvLogger.logEvent("BUTTON_PRESS")

        Assert.assertEquals("BUTTON_PRESS", event.eventCode)
        Assert.assertEquals("2023-10-05T14:30:1.0Z", event.absoluteTime)
        Assert.assertEquals(0L, event.relativeTimeMs)
        Assert.assertEquals(1, csvLogger.getEventCount())
    }

    @Test
    fun testCSVFormatting() = runBlocking {
        csvLogger.logEvent("START")
        timestampProvider.advanceTime(1000)
        csvLogger.logEvent("MIDDLE", mapOf("key1" to "value1"))
        timestampProvider.advanceTime(1000)
        csvLogger.logEvent("END", mapOf("key2" to "value2", "key3" to "value3"))

        val csv = csvLogger.getFormattedLog()
        val lines = csv.trim().split("\n")

        Assert.assertEquals("event_code,absolute_time,relative_time_ms,metadata", lines[0])
        Assert.assertEquals(4, lines.size) // Header + 3 events
    }

    @Test
    fun testJSONFormatting() = runBlocking {
        jsonLogger.logEvent("START")
        timestampProvider.advanceTime(1000)
        jsonLogger.logEvent("MIDDLE", mapOf("key1" to "value1"))

        val json = jsonLogger.getFormattedLog()
        val jsonArray = JSONArray(json)

        Assert.assertEquals(2, jsonArray.length())
    }

    @Test
    fun testFlushToFile() = runBlocking {
        val file = tempFolder.newFile("test_log.csv")

        csvLogger.logEvent("EVENT1")
        csvLogger.logEvent("EVENT2")

        val success = csvLogger.flushToFile(file)
        Assert.assertTrue(success)

        val fileContent = file.readText()
        Assert.assertTrue(fileContent.contains("EVENT1"))
        Assert.assertTrue(fileContent.contains("EVENT2"))
    }
    
    @Test
    fun testAtomicFileWrite() = runBlocking {
        // Create a mock file that we can control
        val mockFile = MockFile("/test/atomic_log.csv")
        
        // Log some events
        csvLogger.logEvent("EVENT1")
        csvLogger.logEvent("EVENT2")
        
        // Flush to the mock file
        val success = csvLogger.flushToFile(mockFile)
        
        // Verify success
        Assert.assertTrue(success)
        
        // Verify content was written
        val content = mockFile.getContent()
        Assert.assertTrue(content.contains("EVENT1"))
        Assert.assertTrue(content.contains("EVENT2"))
    }
    
    @Test
    fun testAtomicFileWriteFailure() = runBlocking {
        // Create a mock file that will fail on rename
        val mockFile = MockFile("/test/failing_log.csv")
        mockFile.throwExceptionOnWrite = true
        
        // Log an event
        csvLogger.logEvent("EVENT1")
        
        // Attempt to flush to the failing mock file
        val success = csvLogger.flushToFile(mockFile)
        
        // Verify failure
        Assert.assertFalse(success)
        
        // Verify buffer was not cleared (operation failed)
        Assert.assertEquals(1, csvLogger.getEventCount())
    }
}
