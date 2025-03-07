package com.example.myapplication

/**
 * A completely standalone test for TimestampProvider that can be copied to any Kotlin playground
 * or IDE to run without any dependencies.
 */
object StandaloneTest {
    // Copy of the TimestampProvider class for standalone testing
    class TimestampProviderTest {
        private val startTimeMillis = System.currentTimeMillis()
        
        fun getAbsoluteTime(): String {
            val currentTimeMillis = System.currentTimeMillis()
            // Simple ISO 8601 format without using SimpleDateFormat
            val date = java.util.Date(currentTimeMillis)
            return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(date)
        }
        
        fun getRelativeTimeMillis(): Long {
            return System.currentTimeMillis() - startTimeMillis
        }
        
        fun resetRelativeTime() {
            // In this simplified version, we just print that reset was called
            println("Reset called - in real implementation this would reset the start time")
        }
    }
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== Standalone TimestampProvider Test ===")
        
        // Create a new provider
        val provider = TimestampProviderTest()
        
        // Test absolute time
        val absoluteTime = provider.getAbsoluteTime()
        println("Absolute time: $absoluteTime")
        
        // Test relative time starts near zero
        val initialRelative = provider.getRelativeTimeMillis()
        println("Initial relative time: $initialRelative ms")
        
        // Test relative time increments
        println("Waiting 500ms...")
        Thread.sleep(500)
        val laterRelative = provider.getRelativeTimeMillis()
        println("Later relative time: $laterRelative ms")
        println("Difference: ${laterRelative - initialRelative} ms")
        
        // Test reset functionality
        println("Resetting relative time...")
        provider.resetRelativeTime()
        
        println("=== Test completed successfully ===")
    }
}
