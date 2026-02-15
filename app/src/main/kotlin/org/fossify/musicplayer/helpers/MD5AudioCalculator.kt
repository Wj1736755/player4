package org.fossify.musicplayer.helpers

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Calculator for MD5 checksum of audio data only (excluding ID3v2 tags).
 * 
 * Algorithm based on CHECKSUM_MD5_AUDIO_ALGORITHM.md:
 * 1. Read first 10 bytes (ID3v2 header)
 * 2. If starts with "ID3", calculate tag size (syncsafe integer from bytes 6-9)
 * 3. audio_offset = 10 + tag_size
 * 4. Read file from audio_offset, chunk by chunk (8192 bytes)
 * 5. Update MD5 hash
 * 6. Return hexdigest (lowercase)
 */
object MD5AudioCalculator {

    private const val CHUNK_SIZE = 8192
    private const val ID3_HEADER_SIZE = 10

    /**
     * Calculate MD5 checksum of audio data only (MPEG frames), excluding ID3v2 tags.
     *
     * @param file MP3 file
     * @return MD5 hex (32 characters lowercase), or null on error
     */
    fun calculate(file: File): String? {
        if (!file.exists() || !file.isFile) return null

        return try {
            val md5 = MessageDigest.getInstance("MD5")
            
            FileInputStream(file).use { fis ->
                // Read first 10 bytes (ID3v2 header)
                val header = ByteArray(ID3_HEADER_SIZE)
                val headerRead = fis.read(header)
                
                if (headerRead < ID3_HEADER_SIZE) {
                    // File too small, hash entire file
                    fis.channel.position(0)
                } else if (hasID3v2Tags(header)) {
                    // Calculate tag size and skip to audio data
                    val tagSize = getID3v2TagSize(header)
                    val audioOffset = ID3_HEADER_SIZE + tagSize
                    
                    // Skip to audio data
                    fis.channel.position(audioOffset.toLong())
                } else {
                    // No ID3v2 tags, start from beginning
                    fis.channel.position(0)
                }
                
                // Read audio data chunk by chunk and update MD5
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    md5.update(buffer, 0, bytesRead)
                }
            }
            
            // Convert to hex string (lowercase)
            md5.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            android.util.Log.e("MD5AudioCalculator", "Error calculating MD5", e)
            null
        }
    }

    /**
     * Check if file has ID3v2 tags.
     *
     * @param header First 10 bytes of file
     * @return true if file starts with "ID3"
     */
    fun hasID3v2Tags(header: ByteArray): Boolean {
        if (header.size < 3) return false
        return header[0] == 'I'.code.toByte() &&
                header[1] == 'D'.code.toByte() &&
                header[2] == '3'.code.toByte()
    }

    /**
     * Check if file has ID3v2 tags.
     *
     * @param file MP3 file
     * @return true if file starts with "ID3"
     */
    fun hasID3v2Tags(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false

        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(3)
                val read = fis.read(header)
                read == 3 && hasID3v2Tags(header)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Calculate ID3v2 tag size (syncsafe integer from bytes 6-9).
     *
     * Syncsafe integer: Each byte uses only 7 bits (MSB is always 0).
     * Format: (byte[6] & 0x7F) << 21 | (byte[7] & 0x7F) << 14 | (byte[8] & 0x7F) << 7 | (byte[9] & 0x7F)
     *
     * @param header ID3v2 header (first 10 bytes)
     * @return Tag size in bytes, or 0 if no tags
     */
    fun getID3v2TagSize(header: ByteArray): Int {
        if (header.size < ID3_HEADER_SIZE) return 0
        if (!hasID3v2Tags(header)) return 0

        return ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)
    }

    /**
     * Calculate offset to audio data (10 + tag_size).
     *
     * @param file MP3 file
     * @return Offset in bytes
     */
    fun getAudioDataOffset(file: File): Int {
        if (!file.exists() || !file.isFile) return 0

        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(ID3_HEADER_SIZE)
                val read = fis.read(header)
                
                if (read < ID3_HEADER_SIZE) {
                    0
                } else if (hasID3v2Tags(header)) {
                    ID3_HEADER_SIZE + getID3v2TagSize(header)
                } else {
                    0
                }
            }
        } catch (e: Exception) {
            0
        }
    }
}
