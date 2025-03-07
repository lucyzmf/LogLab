package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

class CRC8Test {
    
    @Test
    fun `test CRC8 calculation for byte arrays`() {
        // Test with empty array
        assertEquals(0, CRC8.calculate(ByteArray(0)).toInt())
        
        // Test with simple data
        val data1 = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val checksum1 = CRC8.calculate(data1)
        assertNotEquals(0, checksum1.toInt())
        
        // Test with different data
        val data2 = byteArrayOf(0x01, 0x02, 0x03, 0x05) // Changed last byte
        val checksum2 = CRC8.calculate(data2)
        assertNotEquals(checksum1, checksum2)
        
        // Test with same data
        val data3 = byteArrayOf(0x01, 0x02, 0x03, 0x04) // Same as data1
        val checksum3 = CRC8.calculate(data3)
        assertEquals(checksum1, checksum3)
    }
    
    @Test
    fun `test CRC8 calculation for strings`() {
        // Test with empty string
        assertEquals(0, CRC8.calculate("").toInt())
        
        // Test with simple string
        val str1 = "BUTTON_PRESS"
        val checksum1 = CRC8.calculate(str1)
        assertNotEquals(0, checksum1.toInt())
        
        // Test with different string
        val str2 = "BUTTON_RELEASE" // Different string
        val checksum2 = CRC8.calculate(str2)
        assertNotEquals(checksum1, checksum2)
        
        // Test with same string
        val str3 = "BUTTON_PRESS" // Same as str1
        val checksum3 = CRC8.calculate(str3)
        assertEquals(checksum1, checksum3)
    }
    
    @Test
    fun `test CRC8 validation`() {
        // Test validation with byte array
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val checksum = CRC8.calculate(data)
        
        assertTrue(CRC8.validate(data, checksum))
        assertFalse(CRC8.validate(data, (checksum + 1).toByte()))
        
        // Test validation with string
        val str = "EVENT_CODE"
        val strChecksum = CRC8.calculate(str)
        
        assertTrue(CRC8.validate(str, strChecksum))
        assertFalse(CRC8.validate(str, (strChecksum + 1).toByte()))
    }
}
