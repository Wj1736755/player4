package org.fossify.musicplayer.helpers

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Unit tests for MD5AudioCalculator.
 */
class MD5AudioCalculatorTest {

    @Test
    fun `test hasID3v2Tags with ID3 header`() {
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        assertTrue(MD5AudioCalculator.hasID3v2Tags(header))
    }

    @Test
    fun `test hasID3v2Tags without ID3 header`() {
        val header = byteArrayOf(
            0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        assertFalse(MD5AudioCalculator.hasID3v2Tags(header))
    }

    @Test
    fun `test hasID3v2Tags with short header`() {
        val header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte())
        assertFalse(MD5AudioCalculator.hasID3v2Tags(header))
    }

    @Test
    fun `test getID3v2TagSize syncsafe integer`() {
        // Example: tag_size = 2038 bytes
        // Syncsafe: 0x000007F6 = 0x00 0x00 0x0F 0x76
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            0x04, 0x00, 0x00,  // Version + flags
            0x00, 0x00, 0x0F, 0x76  // Tag size (syncsafe)
        )
        assertEquals(2038, MD5AudioCalculator.getID3v2TagSize(header))
    }

    @Test
    fun `test getID3v2TagSize zero`() {
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            0x04, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00  // Tag size = 0
        )
        assertEquals(0, MD5AudioCalculator.getID3v2TagSize(header))
    }

    @Test
    fun `test getID3v2TagSize without ID3 header`() {
        val header = byteArrayOf(
            0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        assertEquals(0, MD5AudioCalculator.getID3v2TagSize(header))
    }

    @Test
    fun `test calculate with non-existent file`() {
        val file = File("non_existent_file.mp3")
        assertNull(MD5AudioCalculator.calculate(file))
    }

    @Test
    fun `test calculate with empty file returns hash`() {
        val tempFile = File.createTempFile("test_empty", ".mp3")
        try {
            // Empty file should still calculate MD5 (of 0 bytes)
            val checksum = MD5AudioCalculator.calculate(tempFile)
            assertNotNull(checksum)
            assertEquals(32, checksum?.length)  // MD5 is 32 hex characters
            assertTrue(ID3TagsHelper.isValidMD5(checksum))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `test calculate with minimal MP3 file`() {
        val tempFile = File.createTempFile("test_minimal", ".mp3")
        try {
            // Create minimal MP3: ID3v2 header + MPEG frame
            val id3Header = byteArrayOf(
                'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
                0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
            )
            val mpegFrame = byteArrayOf(
                0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte(),
                0x00, 0x00, 0x00, 0x00
            )
            
            tempFile.outputStream().use { output ->
                output.write(id3Header)
                output.write(mpegFrame)
            }
            
            val checksum = MD5AudioCalculator.calculate(tempFile)
            assertNotNull(checksum)
            assertEquals(32, checksum?.length)
            assertTrue(ID3TagsHelper.isValidMD5(checksum))
            
            // Checksum should be lowercase
            assertEquals(checksum, checksum?.lowercase())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `test calculate same audio data returns same checksum`() {
        val tempFile1 = File.createTempFile("test1", ".mp3")
        val tempFile2 = File.createTempFile("test2", ".mp3")
        
        try {
            // Same audio data
            val audioData = byteArrayOf(
                0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte(),
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08
            )
            
            // File 1: No ID3 tags
            tempFile1.writeBytes(audioData)
            
            // File 2: With ID3 tags (different) + same audio
            val id3Header = byteArrayOf(
                'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
                0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
            )
            tempFile2.outputStream().use { output ->
                output.write(id3Header)
                output.write(audioData)
            }
            
            val checksum1 = MD5AudioCalculator.calculate(tempFile1)
            val checksum2 = MD5AudioCalculator.calculate(tempFile2)
            
            assertNotNull(checksum1)
            assertNotNull(checksum2)
            
            // Both should have same checksum (ID3 tags ignored)
            assertEquals("Checksums should match (ID3 tags ignored)", checksum1, checksum2)
        } finally {
            tempFile1.delete()
            tempFile2.delete()
        }
    }

    @Test
    fun `test getAudioDataOffset with ID3 tags`() {
        val tempFile = File.createTempFile("test_offset", ".mp3")
        try {
            // ID3 header with tag_size = 100
            val id3Header = byteArrayOf(
                'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
                0x04, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x64  // tag_size = 100 (syncsafe: 0x64 = 100)
            )
            val dummyTags = ByteArray(100) { 0x00 }
            val audioData = byteArrayOf(0xFF.toByte(), 0xFB.toByte())
            
            tempFile.outputStream().use { output ->
                output.write(id3Header)
                output.write(dummyTags)
                output.write(audioData)
            }
            
            val offset = MD5AudioCalculator.getAudioDataOffset(tempFile)
            assertEquals(110, offset)  // 10 (header) + 100 (tags)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `test getAudioDataOffset without ID3 tags`() {
        val tempFile = File.createTempFile("test_no_id3", ".mp3")
        try {
            val audioData = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte())
            tempFile.writeBytes(audioData)
            
            val offset = MD5AudioCalculator.getAudioDataOffset(tempFile)
            assertEquals(0, offset)  // No ID3 tags, start from beginning
        } finally {
            tempFile.delete()
        }
    }
}
