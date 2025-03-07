package com.example.myapplication

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong

/**
 * Provides timestamps in both absolute (ISO 8601) and relative (milliseconds since start) formats.
 * Thread-safe for concurrent usage in multi-threaded environments.
 * 
 * Usage:
 * - For app-wide consistent timestamps: TimestampProvider.getInstance()
 * - For component-specific timing: new TimestampProvider()
 */
open class TimestampProvider {
    // Using AtomicLong to ensure thread-safety for the start time
    private val startTimeMillis = AtomicLong(System.currentTimeMillis())
    
    // Thread-safe date formatter
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Returns the current time in ISO 8601 format (e.g., "2023-10-05T14:30:00.123Z")
     * Thread-safe implementation for concurrent access.
     * 
     * @return String representation of current time in ISO 8601 format
     */
    open fun getAbsoluteTime(): String {
        val currentTimeMillis = System.currentTimeMillis()
        return synchronized(dateFormat) {
            dateFormat.format(Date(currentTimeMillis))
        }
    }

    /**
     * Returns the number of milliseconds elapsed since this provider was initialized.
     * Thread-safe implementation for concurrent access.
     * 
     * @return Long representing elapsed milliseconds since initialization
     */
    open fun getRelativeTimeMillis(): Long {
        return System.currentTimeMillis() - startTimeMillis.get()
    }

    /**
     * Resets the relative time counter to the current time.
     * Useful for restarting timing for a new experiment or session.
     */
    open fun resetRelativeTime() {
        startTimeMillis.set(System.currentTimeMillis())
    }

    companion object {
        // Singleton instance for app-wide usage
        @Volatile
        private var instance: TimestampProvider? = null
        
        /**
         * Returns the singleton instance of TimestampProvider.
         * Uses double-checked locking for thread safety.
         * 
         * @return The singleton TimestampProvider instance
         */
        fun getInstance(): TimestampProvider {
            return instance ?: synchronized(this) {
                instance ?: TimestampProvider().also { instance = it }
            }
        }
    }
}
