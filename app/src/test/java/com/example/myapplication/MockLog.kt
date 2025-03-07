package com.example.myapplication

/**
 * Mock implementation of Android's Log class for unit testing
 */
object Log {
    fun d(tag: String, msg: String): Int {
        println("DEBUG: $tag: $msg")
        return 0
    }

    fun e(tag: String, msg: String): Int {
        println("ERROR: $tag: $msg")
        return 0
    }

    fun e(tag: String, msg: String, throwable: Throwable): Int {
        println("ERROR: $tag: $msg")
        throwable.printStackTrace()
        return 0
    }
}
