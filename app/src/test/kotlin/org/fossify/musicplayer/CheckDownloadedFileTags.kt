package org.fossify.musicplayer

import org.fossify.musicplayer.helpers.TXXXTagsReader
import org.junit.Test
import java.io.File

class CheckDownloadedFileTags {
    
    @Test
    fun checkTagsInDownloadedFile() {
        println("=== Checking CACHE file (before Save2) ===")
        val file = File("D:\\Cursor\\Android\\MusicPlayer\\test_file_cache.mp3")
        
        if (!file.exists()) {
            println("❌ File not found: ${file.absolutePath}")
            return
        }
        
        println("✅ File exists: ${file.name}")
        println("📦 File size: ${file.length()} bytes")
        println()
        
        // Read all TXXX tags
        val tags = TXXXTagsReader.readAllTags(file)
        
        if (tags == null) {
            println("❌ Failed to read tags!")
            return
        }
        
        println("📋 TXXX Tags found:")
        println("  Content (transcription): ${tags.transcription}")
        println("  GUID: ${tags.guid}")
        println("  CREATED_ON_DATE: ${tags.createdOnDate}")
        println("  CREATED_ON_TS: ${tags.createdOnTimestamp}")
        println("  DURATION_SEC: ${tags.duration}")
        println("  CHECKSUM_MD5_AUDIO: ${tags.checksumAudio}")
        println("  GENERATION_PARAMS: ${tags.generationParams}")
        println()
        
        if (tags.transcription.isNullOrEmpty()) {
            println("⚠️ WARNING: Transcription is empty!")
        } else {
            println("✅ Transcription OK: ${tags.transcription?.length} characters")
        }
    }
}
