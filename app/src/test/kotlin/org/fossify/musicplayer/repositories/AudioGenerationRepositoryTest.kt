package org.fossify.musicplayer.repositories

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.fossify.musicplayer.models.GeneratedAudio
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit tests for AudioGenerationRepository
 */
class AudioGenerationRepositoryTest {
    
    private lateinit var context: Context
    private lateinit var repository: AudioGenerationRepository
    private lateinit var tempDir: File
    
    @Before
    fun setup() {
        context = mockk(relaxed = true)
        tempDir = createTempDir("test_elevenlabs")
        
        every { context.cacheDir } returns tempDir.parentFile
        
        repository = AudioGenerationRepository(context)
    }
    
    @Test
    fun `saveAudioToTemp creates file with correct name format`() {
        // Given
        val audio = GeneratedAudio(
            variantIndex = 0,
            audioData = "test audio data".toByteArray(),
            originalText = "Test text"
        )
        
        // When
        val file = repository.saveAudioToTemp(audio)
        
        // Then
        assertTrue(file.exists())
        assertTrue(file.name.startsWith("tts_v0_"))
        assertTrue(file.name.endsWith(".mp3"))
        assertEquals("test audio data", file.readText())
    }
    
    @Test
    fun `saveAllAudios saves multiple files`() {
        // Given
        val audios = listOf(
            GeneratedAudio(0, "audio 1".toByteArray(), originalText = "Text 1"),
            GeneratedAudio(1, "audio 2".toByteArray(), originalText = "Text 2"),
            GeneratedAudio(2, "audio 3".toByteArray(), originalText = "Text 3")
        )
        
        // When
        val files = repository.saveAllAudios(audios)
        
        // Then
        assertEquals(3, files.size)
        files.forEach { file ->
            assertTrue(file.exists())
            assertTrue(file.name.endsWith(".mp3"))
        }
    }
    
    @Test
    fun `clearTempAudios removes all files`() {
        // Given
        val audios = listOf(
            GeneratedAudio(0, "audio 1".toByteArray(), originalText = "Text 1"),
            GeneratedAudio(1, "audio 2".toByteArray(), originalText = "Text 2")
        )
        repository.saveAllAudios(audios)
        
        // When
        repository.clearTempAudios()
        
        // Then
        assertEquals(0, repository.getTempAudios().size)
    }
}
