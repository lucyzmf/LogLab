package com.example.myapplication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File

class SessionLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()
    
    private lateinit var timestampProvider: MockTimestampProvider
    private lateinit var csvLogger: SessionLogger
    private lateinit var jsonLogger: SessionLogger
    
    /**
     * Mock implementation of TimestampProvider for predictable test results
     */
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
        // Initialize the mock timestamp provider
        timestampProvider = MockTimestampProvider()
        
        // Create loggers with different formats
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
        
        assertEquals("BUTTON_PRESS", event.eventCode)
        assertEquals("2023-10-05T14:30:1.0Z", event.absoluteTime)
        assertEquals(0L, event.relativeTimeMs)
        assertEquals(1, csvLogger.getEventCount())
    }
    
    @Test
    fun testLogEventWithMetadata() = runBlocking {
        val metadata = mapOf("button_id" to "submit", "screen" to "login")
        val event = csvLogger.logEvent("BUTTON_PRESS", metadata)
        
        assertEquals("BUTTON_PRESS", event.eventCode)
        assertEquals(metadata, event.metadata)
    }
    
    @Test
    fun testClearBuffer() = runBlocking {
        csvLogger.logEvent("EVENT1")
        csvLogger.logEvent("EVENT2")
        assertEquals(2, csvLogger.getEventCount())
        
        csvLogger.clearBuffer()
        assertEquals(0, csvLogger.getEventCount())
    }
    
    @Test
    fun testCSVFormatting() = runBlocking {
        // Log some events with advancing time
        csvLogger.logEvent("START")
        timestampProvider.advanceTime(1000)
        csvLogger.logEvent("MIDDLE", mapOf("key1" to "value1"))
        timestampProvider.advanceTime(1000)
        csvLogger.logEvent("END", mapOf("key2" to "value2", "key3" to "value3"))
        
        val csv = csvLogger.getFormattedLog()
        val lines = csv.trim().split("\n")
        
        // Check header
        assertEquals("event_code,absolute_time,relative_time_ms,metadata", lines[0])
        
        // Check data rows
        assertEquals(4, lines.size) // Header + 3 events
        assertTrue(lines[1].startsWith("START,2023-10-05T14:30:1.0Z,0,"))
        assertTrue(lines[2].startsWith("MIDDLE,2023-10-05T14:30:2.0Z,1000,key1=value1"))
        assertTrue(lines[3].contains("key2=value2"))
        assertTrue(lines[3].contains("key3=value3"))
    }
    
    @Test
    fun testJSONFormatting() = runBlocking {
        // Log some events with advancing time
        jsonLogger.logEvent("START")
        timestampProvider.advanceTime(1000)
        jsonLogger.logEvent("MIDDLE", mapOf("key1" to "value1"))
        
        val json = jsonLogger.getFormattedLog()
        val jsonArray = JSONArray(json)
        
        assertEquals(2, jsonArray.length())
        
        val firstEvent = jsonArray.getJSONObject(0)
        assertEquals("START", firstEvent.getString("event_code"))
        assertEquals("2023-10-05T14:30:1.0Z", firstEvent.getString("absolute_time"))
        assertEquals(0, firstEvent.getLong("relative_time_ms"))
        
        val secondEvent = jsonArray.getJSONObject(1)
        assertEquals("MIDDLE", secondEvent.getString("event_code"))
        assertEquals("2023-10-05T14:30:2.0Z", secondEvent.getString("absolute_time"))
        assertEquals(1000, secondEvent.getLong("relative_time_ms"))
        
        val metadata = secondEvent.getJSONObject("metadata")
        assertEquals("value1", metadata.getString("key1"))
    }
    
    @Test
    fun testFlushToFile() = runBlocking {
        // Create a mock file instead of a temporary file
        val file = MockFile("test_log.csv")
        
        // Log some events
        csvLogger.logEvent("EVENT1")
        csvLogger.logEvent("EVENT2")
        
        // Flush to file
        val success = csvLogger.flushToFile(file)
        
        assertTrue(success)
        assertEquals(0, csvLogger.getEventCount()) // Buffer should be cleared
        
        // Check file contents
        val fileContent = file.getContent()
        assertTrue(fileContent.contains("EVENT1"))
        assertTrue(fileContent.contains("EVENT2"))
    }
    
    @Test
    fun testFlushToFileWithoutClearing() = runBlocking {
        val file = MockFile("test_log.csv")
        
        csvLogger.logEvent("EVENT1")
        csvLogger.logEvent("EVENT2")
        
        // Flush without clearing
        val success = csvLogger.flushToFile(file, clearBufferAfterFlush = false)
        
        assertTrue(success)
        assertEquals(2, csvLogger.getEventCount()) // Buffer should still have events
        
        // Check file contents
        val fileContent = file.getContent()
        assertTrue(fileContent.contains("EVENT1"))
        assertTrue(fileContent.contains("EVENT2"))
    }
    
    @Test
    fun testConcurrentLogging() = runBlocking {
        val eventCount = 100
        val threadCount = 10
        
        withContext(Dispatchers.Default) {
            val tasks = List(threadCount) { threadId ->
                async {
                    repeat(eventCount) { i ->
                        csvLogger.logEvent("THREAD_${threadId}_EVENT_$i")
                    }
                }
            }
            tasks.awaitAll()
        }
        
        assertEquals(threadCount * eventCount, csvLogger.getEventCount())
    }
    
    @Test
    fun testConcurrentFlushAndLog() = runBlocking {
        val file = MockFile("concurrent_test.csv")
        
        withContext(Dispatchers.Default) {
            val logTask = async {
                repeat(100) {
                    csvLogger.logEvent("LOG_DURING_FLUSH_$it")
                    kotlinx.coroutines.delay(1)
                }
            }
            
            val flushTask = async {
                repeat(5) {
                    csvLogger.flushToFile(file, clearBufferAfterFlush = false)
                    kotlinx.coroutines.delay(10)
                }
            }
            
            awaitAll(logTask, flushTask)
        }
        
        // Final flush to check all events were logged
        csvLogger.flushToFile(file)
        
        val fileContent = file.getContent()
        assertTrue(fileContent.contains("LOG_DURING_FLUSH_0"))
        assertTrue(fileContent.contains("LOG_DURING_FLUSH_99"))
    }
}
