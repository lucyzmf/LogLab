package com.example.myapplication

import android.util.Log as AndroidLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * SessionLogger records events with timestamps during an experiment session.
 * It supports both CSV and JSON formats, maintains an in-memory buffer,
 * and provides thread-safe operations for concurrent logging.
 */
class SessionLogger(
    private val timestampProvider: TimestampProvider = TimestampProvider.getInstance(),
    private val format: LogFormat = LogFormat.CSV
) {
    private val TAG = "SessionLogger"
    private val eventBuffer = ConcurrentLinkedQueue<LogEvent>()
    private val mutex = Mutex() // For operations that need exclusive access

    /**
     * Supported log formats
     */
    enum class LogFormat {
        CSV, JSON
    }

    /**
     * Represents a single logged event
     */
    data class LogEvent(
        val eventCode: String,
        val absoluteTime: String,
        val relativeTimeMs: Long,
        val metadata: Map<String, String> = emptyMap()
    )

    /**
     * Logs an event with the current timestamp
     *
     * @param eventCode A string code identifying the event type
     * @param metadata Optional key-value pairs with additional event information
     * @return The LogEvent that was created and added to the buffer
     */
    suspend fun logEvent(eventCode: String, metadata: Map<String, String> = emptyMap()): LogEvent {
        val event = LogEvent(
            eventCode = eventCode,
            absoluteTime = timestampProvider.getAbsoluteTime(),
            relativeTimeMs = timestampProvider.getRelativeTimeMillis(),
            metadata = metadata
        )

        eventBuffer.add(event)
        AndroidLog.d(TAG, "Logged event: $eventCode at ${event.absoluteTime}")
        return event
    }

    /**
     * Returns the current number of events in the buffer
     */
    fun getEventCount(): Int = eventBuffer.size

    /**
     * Clears all events from the buffer
     */
    suspend fun clearBuffer() = mutex.withLock {
        eventBuffer.clear()
        AndroidLog.d(TAG, "Event buffer cleared")
    }

    /**
     * Converts the buffer to a string in the configured format (CSV or JSON)
     */
    suspend fun getFormattedLog(): String = mutex.withLock {
        return when (format) {
            LogFormat.CSV -> formatAsCSV()
            LogFormat.JSON -> formatAsJSON()
        }
    }

    /**
     * Writes the current buffer to a file and optionally clears the buffer
     *
     * @param file The file to write to
     * @param clearBufferAfterFlush Whether to clear the buffer after writing
     * @return True if the write was successful
     */
    suspend fun flushToFile(file: File, clearBufferAfterFlush: Boolean = true): Boolean = mutex.withLock {
        try {
            if (eventBuffer.isEmpty()) {
                AndroidLog.d(TAG, "Buffer empty, nothing to flush")
                return true
            }

            val formattedLog = getFormattedLog()
            
            // Create the appropriate writer based on the file type
            val writer = if (file is MockFile) {
                MockFileWriter(file)
            } else {
                FileWriter(file)
            }
            
            writer.use { it.write(formattedLog) }

            AndroidLog.d(TAG, "Flushed ${eventBuffer.size} events to ${file.absolutePath}")
            
            if (clearBufferAfterFlush) {
                eventBuffer.clear()
                AndroidLog.d(TAG, "Buffer cleared after flush")
            }
            
            return true
        } catch (e: Exception) {
            AndroidLog.e(TAG, "Error flushing to file: ${e.message}", e)
            return false
        }
    }

    /**
     * Formats the buffer as CSV
     */
    private fun formatAsCSV(): String {
        val sb = StringBuilder()
        
        // Header row
        sb.appendLine("event_code,absolute_time,relative_time_ms,metadata")
        
        // Data rows
        eventBuffer.forEach { event ->
            sb.append(event.eventCode).append(",")
            sb.append(event.absoluteTime).append(",")
            sb.append(event.relativeTimeMs).append(",")
            
            // Format metadata as a single field with key-value pairs
            if (event.metadata.isNotEmpty()) {
                val metadataStr = event.metadata.entries.joinToString(";") { 
                    "${it.key}=${it.value}" 
                }
                sb.append(metadataStr)
            }
            
            sb.appendLine()
        }
        
        return sb.toString()
    }

    /**
     * Formats the buffer as JSON
     */
    private fun formatAsJSON(): String {
        val jsonArray = JSONArray()
        
        eventBuffer.forEach { event ->
            val jsonEvent = JSONObject().apply {
                put("event_code", event.eventCode)
                put("absolute_time", event.absoluteTime)
                put("relative_time_ms", event.relativeTimeMs)
                
                // Add metadata as a nested object
                if (event.metadata.isNotEmpty()) {
                    val metadataObj = JSONObject()
                    event.metadata.forEach { (key, value) ->
                        metadataObj.put(key, value)
                    }
                    put("metadata", metadataObj)
                } else {
                    put("metadata", JSONObject())
                }
            }
            
            jsonArray.put(jsonEvent)
        }
        
        return jsonArray.toString(2) // Pretty print with 2-space indentation
    }
}
