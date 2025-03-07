package com.example.myapplication

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong

/**
 * Provides timestamps in both absolute (ISO 8601) and relative (milliseconds since start) formats.
 * Thread-safe for concurrent usage.
 */
class TimestampProvider {
    private val startTimeMillis: Long = System.currentTimeMillis()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Returns the current time in ISO 8601 format (e.g., "2023-10-05T14:30:00.123Z")
     */
    fun getAbsoluteTime(): String {
        val currentTimeMillis = System.currentTimeMillis()
        return synchronized(dateFormat) {
            dateFormat.format(Date(currentTimeMillis))
        }
    }

    /**
     * Returns the number of milliseconds elapsed since this provider was initialized
     */
    fun getRelativeTimeMillis(): Long {
        return System.currentTimeMillis() - startTimeMillis
    }

    companion object {
        // Singleton instance for app-wide usage
        private val instance = TimestampProvider()

        fun getInstance(): TimestampProvider = instance
    }
}
