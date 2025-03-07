package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class TimestampProviderTest {

    private lateinit var timestampProvider: TimestampProvider

    @Before
    fun setUp() {
        timestampProvider = TimestampProvider()
    }

    @Test
    fun testAbsoluteTimeFormat() {
        val timestamp = timestampProvider.getAbsoluteTime()
        
        // Verify ISO 8601 format (YYYY-MM-DDTHH:MM:SS.sssZ)
        val pattern = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"
        )
        assertTrue("Timestamp should match ISO 8601 format", pattern.matcher(timestamp).matches())
        
        // Verify the timestamp is close to current time
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsedTime = dateFormat.parse(timestamp)?.time ?: 0L
        val currentTime = System.currentTimeMillis()
        
        // Should be within 1 second of current time
        assertTrue("Timestamp should be close to current time",
            Math.abs(parsedTime - currentTime) < 1000)
    }

    @Test
    fun testRelativeTimeStartsAtZero() {
        // Create a new provider to ensure we're testing from initialization
        val newProvider = TimestampProvider()
        
        // First relative time should be very close to 0
        val relativeTime = newProvider.getRelativeTimeMillis()
        assertTrue("Initial relative time should be close to 0", relativeTime < 100)
    }

    @Test
    fun testRelativeTimeIncrements() {
        val firstReading = timestampProvider.getRelativeTimeMillis()
        
        // Wait a bit
        Thread.sleep(100)
        
        val secondReading = timestampProvider.getRelativeTimeMillis()
        assertTrue("Relative time should increase", secondReading > firstReading)
        assertTrue("Relative time should increase by approximately the sleep time",
            secondReading - firstReading >= 90) // Allow for small timing variations
    }
    
    @Test
    fun testRelativeTimeReset() {
        // Get initial reading
        val initialReading = timestampProvider.getRelativeTimeMillis()
        
        // Wait a bit to accumulate some time
        Thread.sleep(50)
        
        // Reset the relative time
        timestampProvider.resetRelativeTime()
        
        // Get new reading after reset
        val afterResetReading = timestampProvider.getRelativeTimeMillis()
        
        // Should be close to zero again
        assertTrue("Relative time should reset to near zero", afterResetReading < 20)
    }

    @Test
    fun testThreadSafety() {
        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = java.util.concurrent.atomic.AtomicInteger(0)
        
        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    // Each thread gets timestamps multiple times
                    repeat(100) {
                        val absolute = timestampProvider.getAbsoluteTime()
                        val relative = timestampProvider.getRelativeTimeMillis()
                        
                        // Basic validation to ensure we got valid values
                        if (absolute.isNotEmpty() && relative >= 0) {
                            successCount.incrementAndGet()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        
        // Wait for all threads to complete
        latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()
        
        // All calls should have succeeded
        assertEquals(threadCount * 100, successCount.get())
    }
    
    @Test
    fun testSingletonInstance() {
        val instance1 = TimestampProvider.getInstance()
        val instance2 = TimestampProvider.getInstance()
        
        // Should be the same instance
        assertTrue("getInstance() should return the same instance", instance1 === instance2)
        
        // Both instances should work correctly
        val time1 = instance1.getAbsoluteTime()
        val time2 = instance2.getAbsoluteTime()
        
        // Times should be very close (within 100ms)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsedTime1 = dateFormat.parse(time1)?.time ?: 0L
        val parsedTime2 = dateFormat.parse(time2)?.time ?: 0L
        
        assertTrue("Both instances should return similar timestamps", 
            Math.abs(parsedTime1 - parsedTime2) < 100)
    }
}
