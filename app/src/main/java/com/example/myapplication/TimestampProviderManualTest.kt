package com.example.myapplication

/**
 * A simple manual test runner for TimestampProvider that doesn't require Gradle.
 * You can run this from an IDE or online Kotlin playground.
 */
object TimestampProviderManualTest {
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== TimestampProvider Manual Test ===")
        
        // Create a new provider
        val provider = TimestampProvider()
        
        // Test absolute time
        val absoluteTime = provider.getAbsoluteTime()
        println("Absolute time: $absoluteTime")
        println("Format check: ${absoluteTime.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"))}")
        
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
        val afterResetRelative = provider.getRelativeTimeMillis()
        println("After reset: $afterResetRelative ms")
        
        // Test singleton
        val instance1 = TimestampProvider.getInstance()
        val instance2 = TimestampProvider.getInstance()
        println("Singleton test: same instance? ${instance1 === instance2}")
        
        // Test thread safety with a simple concurrent test
        println("Testing thread safety with 5 threads...")
        val threads = List(5) { threadId ->
            Thread {
                repeat(3) {
                    val abs = instance1.getAbsoluteTime()
                    val rel = instance1.getRelativeTimeMillis()
                    println("Thread $threadId: abs=$abs, rel=$rel")
                    Thread.sleep(10)
                }
            }.apply { start() }
        }
        
        // Wait for all threads to complete
        threads.forEach { it.join() }
        
        println("=== Test completed successfully ===")
    }
}
