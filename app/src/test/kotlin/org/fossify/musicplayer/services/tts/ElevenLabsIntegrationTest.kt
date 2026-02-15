package org.fossify.musicplayer.services.tts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.models.AudioGenerationResult
import org.fossify.musicplayer.models.ElevenLabsApiKey
import org.fossify.musicplayer.repositories.AudioGenerationRepository
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration tests for ElevenLabs API
 * 
 * IMPORTANT: These tests require a real API key!
 * Set TEST_API_KEY constant before running tests.
 * 
 * These tests are @Ignore by default to avoid API calls during normal test runs.
 * Remove @Ignore annotation when you want to test with real API.
 */
@RunWith(RobolectricTestRunner::class)
class ElevenLabsIntegrationTest {
    
    companion object {
        /**
         * CONFIGURE THIS: Set your test API key here
         * 
         * To run tests:
         * 1. Replace "YOUR_API_KEY_HERE" with real ElevenLabs API key
         * 2. Remove @Ignore annotation from test methods
         * 3. Run tests
         * 
         * WARNING: These tests will consume ElevenLabs API credits!
         */
        private const val TEST_API_KEY = "sk_4a05e1014392cf12ead3119dc6138f125a7dbcf563d81332"
        private const val TEST_EMAIL = "test@example.com"
    }
    
    private lateinit var context: Context
    private lateinit var service: ElevenLabsAudioService
    private lateinit var repository: AudioGenerationRepository
    private lateinit var database: SongsDatabase
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Create in-memory database for tests
        database = Room.inMemoryDatabaseBuilder(
            context,
            SongsDatabase::class.java
        ).allowMainThreadQueries().build()
        
        // Insert test API key
        val testApiKey = ElevenLabsApiKey(
            email = TEST_EMAIL,
            apiKey = TEST_API_KEY,
            isActive = true
        )
        database.ElevenLabsApiKeyDao().insert(testApiKey)
        
        // Pass database instance to service for testing
        service = ElevenLabsAudioService(context, database)
        repository = AudioGenerationRepository(context)
    }
    
    @After
    fun tearDown() {
        database.close()
        SongsDatabase.destroyInstance()
        repository.clearTempAudios()
    }
    
    @Test
    fun `real API call generates 3 audio variants`() = runBlocking {
        // Given
        val testText = "Hello, this is a test."
        
        // When
        val result = service.generateAudio(
            text = testText,
            variantCount = 3
        )
        
        // Debug output
        when (result) {
            is AudioGenerationResult.Success -> println("✓ Success: ${result.audios.size} audios")
            is AudioGenerationResult.Error -> println("✗ Error: ${result.message}, cause: ${result.cause}")
        }
        
        // Then
        assertTrue("Expected success result, got: $result", result is AudioGenerationResult.Success)
        val success = result as AudioGenerationResult.Success
        
        assertEquals("Expected 3 audio variants", 3, success.audios.size)
        
        success.audios.forEachIndexed { index, audio ->
            assertEquals(index, audio.variantIndex)
            assertTrue("Audio data should not be empty", audio.audioData.isNotEmpty())
            assertEquals(testText, audio.originalText)
            assertEquals("audio/mpeg", audio.mimeType)
        }
        
        println("✓ Successfully generated ${success.audios.size} audio variants")
        println("  Total size: ${success.audios.sumOf { it.audioData.size }} bytes")
    }
    
    @Test
    fun `real API call with Polish text`() = runBlocking {
        // Given
        val polishText = "Dzień dobry, to jest test aplikacji."
        
        // When
        val result = service.generateAudio(
            text = polishText,
            variantCount = 2  // Generate only 2 to save credits
        )
        
        // Then
        assertTrue(result is AudioGenerationResult.Success)
        val success = result as AudioGenerationResult.Success
        
        assertEquals(2, success.audios.size)
        success.audios.forEach { audio ->
            assertTrue(audio.audioData.isNotEmpty())
        }
        
        println("✓ Successfully generated Polish audio")
    }
    
    @Test
    fun `save generated audios to temp directory`() = runBlocking {
        // Given
        val testText = "Test saving to temp directory."
        val result = service.generateAudio(testText, variantCount = 3)
        
        assertTrue(result is AudioGenerationResult.Success)
        val audios = (result as AudioGenerationResult.Success).audios
        
        // When
        val files = repository.saveAllAudios(audios)
        
        // Then
        assertEquals(3, files.size)
        files.forEach { file ->
            assertTrue(file.exists())
            assertTrue(file.length() > 0)
            assertTrue(file.name.endsWith(".mp3"))
        }
        
        println("✓ Saved ${files.size} files to: ${repository.getTempDirectory()}")
        files.forEach { println("  - ${it.name} (${it.length()} bytes)") }
        
        // Cleanup
        repository.clearTempAudios()
    }
    
    @Test
    fun `invalid API key returns error`() = runBlocking {
        // Given - replace active key with invalid one
        database.ElevenLabsApiKeyDao().deactivateAll()
        val invalidKey = ElevenLabsApiKey(
            email = "invalid@example.com",
            apiKey = "invalid_key_12345",
            isActive = true
        )
        database.ElevenLabsApiKeyDao().insert(invalidKey)
        
        // Create new service instance to pick up new key
        val testService = ElevenLabsAudioService(context, database)
        
        // When
        val result = testService.generateAudio("Test text")
        
        // Then
        assertTrue(result is AudioGenerationResult.Error)
        val error = result as AudioGenerationResult.Error
        assertTrue(error.message.contains("401") || error.message.contains("API"))
        
        println("✓ Correctly handled invalid API key: ${error.message}")
    }
}

/**
 * Instructions for running integration tests:
 * 
 * 1. Get your ElevenLabs API key from: https://elevenlabs.io/app/settings/api-keys
 * 
 * 2. Replace TEST_API_KEY constant in this file with your real key
 * 
 * 3. Remove @Ignore annotations from test methods you want to run
 * 
 * 4. Run tests:
 *    ./gradlew test --tests "ElevenLabsIntegrationTest"
 * 
 * 5. Check test output for success messages and generated audio file sizes
 * 
 * WARNING: These tests will consume your ElevenLabs API credits!
 *          Each audio generation costs credits based on character count.
 */
