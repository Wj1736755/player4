package org.fossify.musicplayer.integration

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.fossify.musicplayer.application.usecases.ManagePlaylistUseCase
import org.fossify.musicplayer.domain.model.TrackDomain
import org.fossify.musicplayer.fakes.FakeAudioRepository
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ManagePlaylistIntegrationTest {

    private lateinit var repository: FakeAudioRepository
    private lateinit var useCase: ManagePlaylistUseCase

    @Before
    fun setup() {
        repository = FakeAudioRepository()
        useCase = ManagePlaylistUseCase(repository)
    }

    @Test
    fun `createPlaylist should return new playlist`() = runBlocking {
        val result = useCase.createPlaylist("My Playlist")
        
        assertTrue(result.isSuccess)
        val playlist = result.getOrThrow()
        assertEquals("My Playlist", playlist.title)
        assertTrue(playlist.id > 0)
    }

    @Test
    fun `deletePlaylist should remove playlist`() = runBlocking {
        val playlist = useCase.createPlaylist("Test Playlist").getOrThrow()
        
        val result = useCase.deletePlaylist(playlist.id)
        
        assertTrue(result.isSuccess)
        val playlists = repository.getAllPlaylists()
        assertEquals(0, playlists.size)
    }

    @Test
    fun `getPlaylistWithTracks should return empty list for new playlist`() = runBlocking {
        val playlist = useCase.createPlaylist("Empty Playlist").getOrThrow()
        
        val result = useCase.getPlaylistWithTracks(playlist.id)
        
        assertTrue(result.isSuccess)
        val playlistWithTracks = result.getOrThrow()
        assertEquals("Empty Playlist", playlistWithTracks.playlist.title)
        assertEquals(0, playlistWithTracks.tracks.size)
    }

    @Test
    fun `addTracksToPlaylist should add tracks to playlist`() = runBlocking {
        val playlist = useCase.createPlaylist("Test Playlist").getOrThrow()
        
        val tracks = (1..3).map { index ->
            val guid = UUID.randomUUID()
            val track = TrackDomain(
                guid = guid,
                mediaStoreId = index.toLong(),
                path = "/path/track$index.mp3",
                duration = 180000,
                folderName = "Music",
                year = 2024,
                playlistId = 0
            )
            repository.saveTrack(track)
            guid
        }
        
        val result = useCase.addTracksToPlaylist(playlist.id, tracks)
        
        assertTrue(result.isSuccess)
        
        val playlistWithTracks = useCase.getPlaylistWithTracks(playlist.id).getOrThrow()
        assertEquals(3, playlistWithTracks.tracks.size)
        assertEquals(3, playlistWithTracks.playlist.trackCount)
    }

    @Test
    fun `removeTracksFromPlaylist should remove tracks from playlist`() = runBlocking {
        val playlist = useCase.createPlaylist("Test Playlist").getOrThrow()
        
        val trackGuids = (1..3).map { index ->
            val guid = UUID.randomUUID()
            val track = TrackDomain(
                guid = guid,
                mediaStoreId = index.toLong(),
                path = "/path/track$index.mp3",
                duration = 180000,
                folderName = "Music",
                year = 2024,
                playlistId = 0
            )
            repository.saveTrack(track)
            guid
        }
        
        useCase.addTracksToPlaylist(playlist.id, trackGuids)
        
        val result = useCase.removeTracksFromPlaylist(playlist.id, listOf(trackGuids[1]))
        
        assertTrue(result.isSuccess)
        
        val playlistWithTracks = useCase.getPlaylistWithTracks(playlist.id).getOrThrow()
        assertEquals(2, playlistWithTracks.tracks.size)
    }

    @Test
    fun `addTracksToPlaylist should handle multiple playlists correctly`() = runBlocking {
        val playlist1 = useCase.createPlaylist("Playlist 1").getOrThrow()
        val playlist2 = useCase.createPlaylist("Playlist 2").getOrThrow()
        
        val tracks = (1..5).map { index ->
            val guid = UUID.randomUUID()
            val track = TrackDomain(
                guid = guid,
                mediaStoreId = index.toLong(),
                path = "/path/song$index.mp3",
                duration = 200000,
                folderName = "Music",
                year = 2024,
                playlistId = 0
            )
            repository.saveTrack(track)
            guid
        }
        
        useCase.addTracksToPlaylist(playlist1.id, tracks.take(3))
        useCase.addTracksToPlaylist(playlist2.id, tracks.drop(2).take(3))
        
        val playlist1WithTracks = useCase.getPlaylistWithTracks(playlist1.id).getOrThrow()
        val playlist2WithTracks = useCase.getPlaylistWithTracks(playlist2.id).getOrThrow()
        
        assertEquals(3, playlist1WithTracks.tracks.size)
        assertEquals(3, playlist2WithTracks.tracks.size)
    }

    @Test
    fun `getAllPlaylists should return all playlists`() = runBlocking {
        useCase.createPlaylist("Playlist 1")
        useCase.createPlaylist("Playlist 2")
        useCase.createPlaylist("Playlist 3")
        
        val playlists = repository.getAllPlaylists()
        
        assertEquals(3, playlists.size)
    }
}
