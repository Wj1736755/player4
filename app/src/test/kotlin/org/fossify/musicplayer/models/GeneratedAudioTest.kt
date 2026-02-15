package org.fossify.musicplayer.models

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for GeneratedAudio model
 */
class GeneratedAudioTest {
    
    @Test
    fun `GeneratedAudio equals works correctly`() {
        // Given
        val audio1 = GeneratedAudio(
            variantIndex = 0,
            audioData = "test data".toByteArray(),
            originalText = "Test text"
        )
        
        val audio2 = GeneratedAudio(
            variantIndex = 0,
            audioData = "test data".toByteArray(),
            originalText = "Test text"
        )
        
        // Then
        assertEquals("Same audio data should be equal", audio1, audio2)
    }
    
    @Test
    fun `GeneratedAudio different variants are not equal`() {
        // Given
        val audio1 = GeneratedAudio(
            variantIndex = 0,
            audioData = "test data".toByteArray(),
            originalText = "Test"
        )
        
        val audio2 = GeneratedAudio(
            variantIndex = 1,
            audioData = "test data".toByteArray(),
            originalText = "Test"
        )
        
        // Then
        assertNotEquals("Different variant index should not be equal", audio1, audio2)
    }
    
    @Test
    fun `AudioGenerationResult Success contains audios list`() {
        // Given
        val audios = listOf(
            GeneratedAudio(0, "data1".toByteArray(), originalText = "Text"),
            GeneratedAudio(1, "data2".toByteArray(), originalText = "Text"),
            GeneratedAudio(2, "data3".toByteArray(), originalText = "Text")
        )
        
        // When
        val result = AudioGenerationResult.Success(audios)
        
        // Then
        assertEquals(3, result.audios.size)
        assertEquals(0, result.audios[0].variantIndex)
        assertEquals(1, result.audios[1].variantIndex)
        assertEquals(2, result.audios[2].variantIndex)
    }
    
    @Test
    fun `AudioGenerationResult Error contains message`() {
        // Given
        val errorMessage = "Test error message"
        
        // When
        val result = AudioGenerationResult.Error(errorMessage)
        
        // Then
        assertEquals(errorMessage, result.message)
        assertNull(result.cause)
    }
    
    @Test
    fun `AudioGenerationResult Error can contain cause`() {
        // Given
        val exception = RuntimeException("Test exception")
        
        // When
        val result = AudioGenerationResult.Error("Error occurred", exception)
        
        // Then
        assertEquals("Error occurred", result.message)
        assertEquals(exception, result.cause)
    }
}
