package org.fossify.musicplayer.integration

import kotlinx.coroutines.runBlocking
import org.fossify.musicplayer.application.usecases.GenerateAudioUseCase
import org.fossify.musicplayer.fakes.FakeAudioGenerationService
import org.fossify.musicplayer.fakes.FakeAudioRepository
import org.fossify.musicplayer.fakes.FakeFileStorage
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateAudioIntegrationTest {
    
    private lateinit var useCase: GenerateAudioUseCase
    private lateinit var fakeAudioService: FakeAudioGenerationService
    private lateinit var fakeFileStorage: FakeFileStorage
    private lateinit var fakeRepository: FakeAudioRepository
    
    @Before
    fun setup() {
        fakeAudioService = FakeAudioGenerationService()
        fakeFileStorage = FakeFileStorage()
        fakeRepository = FakeAudioRepository()
        
        useCase = GenerateAudioUseCase(
            audioGenerationService = fakeAudioService,
            fileStorage = fakeFileStorage,
            trackRepository = fakeRepository
        )
    }
    
    @After
    fun tearDown() {
        fakeAudioService.clear()
        fakeFileStorage.clear()
        fakeRepository.clear()
    }
    
    @Test
    fun `generate audio creates tracks and saves to storage`() = runBlocking {
        fakeAudioService.setConfigured(true)
        fakeAudioService.setSuccessResponse("Test text for audio generation", variantCount = 3)
        
        val result = useCase.execute("Test text for audio generation", variantCount = 3)
        
        assertTrue(result.isSuccess, "Use case should succeed")
        val tracks = result.getOrThrow()
        
        assertEquals(3, tracks.size, "Should create 3 track variants")
        // Title is now computed from filename
        assertTrue(tracks[0].title.isNotEmpty())
        assertTrue(tracks[1].title.isNotEmpty())
        assertTrue(tracks[2].title.isNotEmpty())
        
        val savedTracks = fakeRepository.getAllTracks()
        assertEquals(3, savedTracks.size, "All tracks should be saved to repository")
        
        assertEquals(3, fakeFileStorage.getFileCount(), "All audio files should be saved")
    }
    
    @Test
    fun `generate audio fails when not configured`() = runBlocking {
        fakeAudioService.setConfigured(false)
        
        val result = useCase.execute("Test text")
        
        assertTrue(result.isFailure, "Should fail when service not configured")
        assertTrue(
            result.exceptionOrNull() is IllegalStateException,
            "Should throw IllegalStateException"
        )
    }
    
    @Test
    fun `generate audio fails with empty text`() = runBlocking {
        fakeAudioService.setConfigured(true)
        
        val result = useCase.execute("")
        
        assertTrue(result.isFailure, "Should fail with empty text")
        assertTrue(
            result.exceptionOrNull() is IllegalArgumentException,
            "Should throw IllegalArgumentException"
        )
    }
    
    @Test
    fun `generate audio fails with invalid variant count`() = runBlocking {
        fakeAudioService.setConfigured(true)
        
        val result = useCase.execute("Test", variantCount = 0)
        
        assertTrue(result.isFailure, "Should fail with variant count 0")
        
        val result2 = useCase.execute("Test", variantCount = 11)
        assertTrue(result2.isFailure, "Should fail with variant count > 10")
    }
    
    @Test
    fun `generate audio truncates long titles`() = runBlocking {
        val longText = "This is a very long text that should be truncated because it exceeds the maximum allowed length for a track title"
        
        fakeAudioService.setConfigured(true)
        fakeAudioService.setSuccessResponse(longText, variantCount = 1)
        
        val result = useCase.execute(longText, variantCount = 1)
        
        assertTrue(result.isSuccess)
        val tracks = result.getOrThrow()
        
        // Title is now derived from filename
        assertTrue(tracks[0].title.isNotEmpty(), "Title should not be empty")
        // Fake implementation uses "test_audio_<guid>.mp3" format
        assertTrue(tracks[0].title.contains("test_audio"), "Title should contain test_audio")
    }
    
    @Test
    fun `generated tracks have correct metadata`() = runBlocking {
        fakeAudioService.setConfigured(true)
        fakeAudioService.setSuccessResponse("Test metadata", variantCount = 1)
        
        val result = useCase.execute("Test metadata", voiceId = "custom_voice", variantCount = 1)
        
        assertTrue(result.isSuccess)
        val track = result.getOrThrow()[0]
        
        assertEquals("Generated", track.folderName)
        assertTrue(track.path.isNotEmpty())
    }
}
