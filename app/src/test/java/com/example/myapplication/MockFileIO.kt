package com.example.myapplication

import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.StringWriter

/**
 * Mock implementation of File for unit testing
 */
class MockFile(private val path: String) : File(path) {
    private val content = StringBuffer()
    var throwExceptionOnWrite = false
    
    override fun exists(): Boolean = true
    
    override fun getAbsolutePath(): String = path
    
    fun getContent(): String = content.toString()
    
    fun setContent(text: String) {
        content.setLength(0)
        content.append(text)
    }
    
    fun appendContent(text: String) {
        content.append(text)
    }
}

/**
 * Mock implementation of FileWriter for unit testing
 */
class MockFileWriter(private val file: MockFile) : FileWriter(file) {
    private val stringWriter = StringWriter()
    
    override fun write(str: String) {
        if (file.throwExceptionOnWrite) {
            throw IOException("Mock write exception")
        }
        stringWriter.write(str)
    }
    
    override fun flush() {
        // Do nothing
    }
    
    override fun close() {
        file.setContent(stringWriter.toString())
    }
}
