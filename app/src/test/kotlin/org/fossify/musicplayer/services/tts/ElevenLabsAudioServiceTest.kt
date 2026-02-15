package org.fossify.musicplayer.services.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.fossify.musicplayer.models.AudioGenerationResult
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ElevenLabsAudioService
 * These tests verify basic functionality without making real API calls
 */
@RunWith(RobolectricTestRunner::class)
class ElevenLabsAudioServiceTest {
    
    private lateinit var context: Context
    private lateinit var service: ElevenLabsAudioService
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        service = ElevenLabsAudioService(context)
    }
    
    @Test
    fun `getProviderName returns ElevenLabs`() {
        // When
        val name = service.getProviderName()
        
        // Then
        assertEquals("ElevenLabs", name)
    }
    
    @Test
    fun `generateAudio returns error when no API key configured`() = runBlocking {
        // Given: No API key in database (fresh test database is empty)
        
        // When
        val result = service.generateAudio("Test text")
        
        // Then
        assertTrue("Should return error result", result is AudioGenerationResult.Error)
        val error = result as AudioGenerationResult.Error
        assertTrue(
            "Error should mention missing API key",
            error.message.contains("No active API key", ignoreCase = true) ||
            error.message.contains("not configured", ignoreCase = true)
        )
    }
    
    @Test
    fun `isConfigured returns false when no API key in database`() = runBlocking {
        // Given: Empty database (no API keys)
        
        // When
        val result = service.isConfigured()
        
        // Then
        assertFalse("Should not be configured without API key", result)
    }
}
