package com.example.myapplication

/**
 * CRC-8 implementation for data validation.
 * Uses the standard CRC-8 polynomial x^8 + x^2 + x + 1 (0x07).
 */
class CRC8 {
    companion object {
        private const val POLYNOMIAL = 0x07
        private val table = ByteArray(256)
        
        init {
            // Initialize the CRC table
            for (i in 0 until 256) {
                var crc = i
                for (j in 0 until 8) {
                    crc = if (crc and 0x80 != 0) {
                        (crc shl 1) xor POLYNOMIAL
                    } else {
                        crc shl 1
                    }
                }
                table[i] = crc.toByte()
            }
        }
        
        /**
         * Calculate CRC-8 checksum for the given data
         * 
         * @param data The data to calculate checksum for
         * @return The calculated CRC-8 checksum
         */
        fun calculate(data: ByteArray): Byte {
            var crc: Byte = 0
            for (b in data) {
                crc = table[(crc.toInt() xor b.toInt()) and 0xFF].toByte()
            }
            return crc
        }
        
        /**
         * Calculate CRC-8 checksum for the given string
         * 
         * @param data The string to calculate checksum for
         * @return The calculated CRC-8 checksum
         */
        fun calculate(data: String): Byte {
            return calculate(data.toByteArray())
        }
        
        /**
         * Validate data with its checksum
         * 
         * @param data The data to validate
         * @param checksum The checksum to validate against
         * @return True if the data is valid, false otherwise
         */
        fun validate(data: ByteArray, checksum: Byte): Boolean {
            return calculate(data) == checksum
        }
        
        /**
         * Validate string with its checksum
         * 
         * @param data The string to validate
         * @param checksum The checksum to validate against
         * @return True if the data is valid, false otherwise
         */
        fun validate(data: String, checksum: Byte): Boolean {
            return calculate(data) == checksum
        }
    }
}
