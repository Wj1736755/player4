package org.fossify.musicplayer.integration

import kotlinx.coroutines.runBlocking
import org.fossify.musicplayer.application.usecases.*
import org.fossify.musicplayer.fakes.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompleteWorkflowIntegrationTest {
    
    private lateinit var generateAudioUseCase: GenerateAudioUseCase
    private lateinit var playTrackUseCase: PlayTrackUseCase
    private lateinit var managePlaylistUseCase: ManagePlaylistUseCase
    private lateinit var getTracksUseCase: GetTracksUseCase
    private lateinit var deleteTracksUseCase: DeleteTracksUseCase
    
    private lateinit var fakeAudioService: FakeAudioGenerationService
    private lateinit var fakeFileStorage: FakeFileStorage
    private lateinit var fakeRepository: FakeAudioRepository
    private lateinit var fakePlayer: FakeAudioPlayer
    private lateinit var fakeLogger: FakePlayEventLogger
    
    @Before
    fun setup() {
        fakeAudioService = FakeAudioGenerationService()
        fakeFileStorage = FakeFileStorage()
        fakeRepository = FakeAudioRepository()
        fakePlayer = FakeAudioPlayer()
        fakeLogger = FakePlayEventLogger()
        
        generateAudioUseCase = GenerateAudioUseCase(
            fakeAudioService,
            fakeFileStorage,
            fakeRepository
        )
        
        playTrackUseCase = PlayTrackUseCase(
            fakePlayer,
            fakeRepository,
            fakeLogger
        )
        
        managePlaylistUseCase = ManagePlaylistUseCase(fakeRepository)
        getTracksUseCase = GetTracksUseCase(fakeRepository)
        deleteTracksUseCase = DeleteTracksUseCase(fakeRepository, fakeFileStorage)
    }
    
    @After
    fun tearDown() {
        fakeAudioService.clear()
        fakeFileStorage.clear()
        fakeRepository.clear()
        fakePlayer.clear()
        fakeLogger.clear()
    }
    
    @Test
    fun `complete workflow - generate, playlist, play, delete`() = runBlocking {
        fakeAudioService.setConfigured(true)
        fakeAudioService.setSuccessResponse("Generated audio track", variantCount = 2)
        
        val generatedTracks = generateAudioUseCase.execute(
            "Generated audio track",
            variantCount = 2
        ).getOrThrow()
        
        assertEquals(2, generatedTracks.size)
        assertEquals(2, fakeFileStorage.getFileCount())
        
        val allTracks = getTracksUseCase.getAllTracks().getOrThrow()
        assertEquals(2, allTracks.size)
        
        val playlist = managePlaylistUseCase.createPlaylist("My Generated Audio").getOrThrow()
        
        managePlaylistUseCase.addTracksToPlaylist(
            playlist.id,
            generatedTracks.map { it.guid }
        )
        
        val playlistWithTracks = managePlaylistUseCase.getPlaylistWithTracks(playlist.id).getOrThrow()
        assertEquals(2, playlistWithTracks.tracks.size)
        
        playTrackUseCase.execute(generatedTracks[0].guid)
        
        assertTrue(fakePlayer.isPlaying())
        assertEquals(generatedTracks[0].guid, fakePlayer.getCurrentTrackGuid())
        assertEquals(1, fakeLogger.getPlayCount(generatedTracks[0].guid))
        
        playTrackUseCase.execute(generatedTracks[1].guid)
        
        assertEquals(1, fakeLogger.getPlayCount(generatedTracks[1].guid))
        assertEquals(2, fakePlayer.playHistory.size)
        
        managePlaylistUseCase.removeTracksFromPlaylist(
            playlist.id,
            listOf(generatedTracks[0].guid)
        )
        
        val updatedPlaylist = managePlaylistUseCase.getPlaylistWithTracks(playlist.id).getOrThrow()
        assertEquals(1, updatedPlaylist.tracks.size)
        
        deleteTracksUseCase.executeSingle(generatedTracks[0].guid, deleteFile = true)
        
        val remainingTracks = getTracksUseCase.getAllTracks().getOrThrow()
        assertEquals(1, remainingTracks.size)
        assertEquals(1, fakeFileStorage.getFileCount())
        
        managePlaylistUseCase.deletePlaylist(playlist.id)
        
        val allPlaylists = managePlaylistUseCase.getAllPlaylists().getOrThrow()
        assertEquals(0, allPlaylists.size)
    }
    
    @Test
    fun `workflow - multiple audio generations accumulate correctly`() = runBlocking {
        fakeAudioService.setConfigured(true)
        
        fakeAudioService.setSuccessResponse("First generation", variantCount = 2)
        val firstBatch = generateAudioUseCase.execute("First generation", variantCount = 2).getOrThrow()
        
        fakeAudioService.setSuccessResponse("Second generation", variantCount = 3)
        val secondBatch = generateAudioUseCase.execute("Second generation", variantCount = 3).getOrThrow()
        
        val allTracks = getTracksUseCase.getAllTracks().getOrThrow()
        assertEquals(5, allTracks.size)
        assertEquals(5, fakeFileStorage.getFileCount())
        
        val requests = fakeAudioService.getGeneratedRequests()
        assertEquals(2, requests.size)
        assertEquals("First generation", requests[0].text)
        assertEquals("Second generation", requests[1].text)
    }
    
    @Test
    fun `workflow - play events persist across multiple sessions`() = runBlocking {
        fakeAudioService.setConfigured(true)
        fakeAudioService.setSuccessResponse("Test track", variantCount = 1)
        
        val track = generateAudioUseCase.execute("Test track", variantCount = 1)
            .getOrThrow()[0]
        
        repeat(5) {
            playTrackUseCase.execute(track.guid)
        }
        
        assertEquals(5, fakeLogger.getPlayCount(track.guid))
        
        val lastPlayedTime = fakeLogger.getLastPlayedTime(track.guid)
        assertTrue(lastPlayedTime!! > 0)
    }
}
