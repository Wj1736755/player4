package org.fossify.musicplayer.services.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.models.AudioGenerationResult
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for ElevenLabsAudioService
 * These tests verify basic functionality without making real API calls
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ElevenLabsAudioServiceTest {
    
    private lateinit var context: Context
    private lateinit var database: SongsDatabase
    private lateinit var service: ElevenLabsAudioService
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Delete any existing database to ensure fresh state
        context.deleteDatabase("songs.db")
        database = androidx.room.Room.databaseBuilder(
            context,
            SongsDatabase::class.java,
            "songs.db"
        )
            .allowMainThreadQueries()  // Allow queries on main thread for testing
            .build()
        service = ElevenLabsAudioService(context, database)
    }
    
    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase("songs.db")
    }
    
    @Test
    fun `getProviderName returns ElevenLabs`() {
        // When
        val name = service.getProviderName()
        
        // Then
        assertEquals("ElevenLabs", name)
    }
    
    @Test
    fun `generateAudio returns error when no API key configured`() = runBlocking(Dispatchers.IO) {
        // Given: No API key in database (fresh test database is empty)
        
        // When
        val result = service.generateAudio("Test text")
        
        // Then
        when (result) {
            is AudioGenerationResult.Error -> {
                assertTrue(
                    "Error should mention missing API key. Actual message: '${result.message}'",
                    result.message.contains("API key", ignoreCase = true)
                )
            }
            is AudioGenerationResult.Success -> {
                fail("Expected error but got success with ${result.audios.size} audios")
            }
        }
    }
    
    @Test
    fun `isConfigured returns false when no API key in database`() = runBlocking(Dispatchers.IO) {
        // Given: Empty database (no API keys)
        
        // When
        val result = service.isConfigured()
        
        // Then
        assertFalse("Should not be configured without API key", result)
    }
}
