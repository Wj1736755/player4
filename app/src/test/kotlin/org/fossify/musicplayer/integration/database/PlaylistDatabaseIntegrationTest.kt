package org.fossify.musicplayer.integration.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.fossify.musicplayer.application.usecases.GetTracksUseCase
import org.fossify.musicplayer.application.usecases.ManagePlaylistUseCase
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.infrastructure.adapters.RoomAudioRepository
import org.fossify.musicplayer.models.Track
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test using REAL Room database (not mocked)
 * Tests playlist management with actual database operations
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistDatabaseIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var database: SongsDatabase
    private lateinit var repository: RoomAudioRepository
    private lateinit var managePlaylistUseCase: ManagePlaylistUseCase
    private lateinit var getTracksUseCase: GetTracksUseCase
    
    private lateinit var trackGuids: List<UUID>
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Create REAL in-memory Room database
        database = Room.inMemoryDatabaseBuilder(
            context,
            SongsDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        
        // Create REAL repository using the real database
        repository = RoomAudioRepository(database)
        
        // Create use cases with real repository
        managePlaylistUseCase = ManagePlaylistUseCase(repository)
        getTracksUseCase = GetTracksUseCase(repository)
        
        // Setup: Insert 5 tracks into REAL database
        insertFiveTracksToDatabase()
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    private fun insertFiveTracksToDatabase() = runBlocking {
        val tracks = (1..5).map { index ->
            Track(
                id = 0L,
                guid = UUID.randomUUID(),
                mediaStoreId = index.toLong(),
                path = "/music/track$index.mp3",
                duration = 180000 + (index * 1000),
                folderName = "Music",
                year = 2024,
                addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
                flags = 0,
                transcription = null,
                transcriptionNormalized = null,
                tagTxxxCreatedAtUnix = null,
                checksumAudio = null
            )
        }
        
        // Insert into REAL database
        database.SongsDao().insertAll(tracks)
        
        // Store GUIDs for later use
        trackGuids = database.SongsDao().getAll().map { it.guid }
        
        println("✓ Inserted 5 tracks into REAL database: ${trackGuids.map { it.toString().take(8) }}")
    }
    
    @Test
    fun `test playlist management with real database - 5 tracks, 2 playlists`() = runBlocking {
        // ARRANGE: Verify we have 5 tracks in database
        val allTracks = getTracksUseCase.getAllTracks().getOrThrow()
        assertEquals(5, allTracks.size, "Should have 5 tracks in database")
        println("✓ Verified 5 tracks in database")
        
        // ARRANGE: Create 2 empty playlists in REAL database
        val playlist1 = managePlaylistUseCase.createPlaylist("Playlist 1").getOrThrow()
        val playlist2 = managePlaylistUseCase.createPlaylist("Playlist 2").getOrThrow()
        println("✓ Created 2 playlists: '${playlist1.title}' (ID=${playlist1.id}), '${playlist2.title}' (ID=${playlist2.id})")
        
        // Verify playlists are empty
        val emptyPlaylist1 = managePlaylistUseCase.getPlaylistWithTracks(playlist1.id).getOrThrow()
        val emptyPlaylist2 = managePlaylistUseCase.getPlaylistWithTracks(playlist2.id).getOrThrow()
        assertEquals(0, emptyPlaylist1.tracks.size, "Playlist 1 should be empty")
        assertEquals(0, emptyPlaylist2.tracks.size, "Playlist 2 should be empty")
        println("✓ Both playlists are empty")
        
        // ACT: Add first 2 tracks to Playlist 1
        val tracksForPlaylist1 = trackGuids.take(2)
        managePlaylistUseCase.addTracksToPlaylist(playlist1.id, tracksForPlaylist1).getOrThrow()
        println("✓ Added first 2 tracks to Playlist 1: ${tracksForPlaylist1.map { it.toString().take(8) }}")
        
        // ACT: Add next 3 tracks to Playlist 2
        val tracksForPlaylist2 = trackGuids.drop(2).take(3)
        managePlaylistUseCase.addTracksToPlaylist(playlist2.id, tracksForPlaylist2).getOrThrow()
        println("✓ Added 3 tracks to Playlist 2: ${tracksForPlaylist2.map { it.toString().take(8) }}")
        
        // ASSERT: Verify Playlist 1 contains exactly the first 2 tracks
        val playlist1WithTracks = managePlaylistUseCase.getPlaylistWithTracks(playlist1.id).getOrThrow()
        assertEquals(2, playlist1WithTracks.tracks.size, "Playlist 1 should have 2 tracks")
        
        val playlist1TrackGuids = playlist1WithTracks.tracks.map { it.guid }
        assertTrue(playlist1TrackGuids.containsAll(tracksForPlaylist1), 
            "Playlist 1 should contain the first 2 tracks")
        assertEquals("track1.mp3", playlist1WithTracks.tracks[0].title)
        assertEquals("track2.mp3", playlist1WithTracks.tracks[1].title)
        println("✓ Playlist 1 contains expected tracks: track1.mp3, track2.mp3")
        
        // ASSERT: Verify Playlist 2 contains exactly 3 tracks
        val playlist2WithTracks = managePlaylistUseCase.getPlaylistWithTracks(playlist2.id).getOrThrow()
        assertEquals(3, playlist2WithTracks.tracks.size, "Playlist 2 should have 3 tracks")
        
        val playlist2TrackGuids = playlist2WithTracks.tracks.map { it.guid }
        assertTrue(playlist2TrackGuids.containsAll(tracksForPlaylist2), 
            "Playlist 2 should contain 3 tracks")
        assertEquals("track3.mp3", playlist2WithTracks.tracks[0].title)
        assertEquals("track4.mp3", playlist2WithTracks.tracks[1].title)
        assertEquals("track5.mp3", playlist2WithTracks.tracks[2].title)
        println("✓ Playlist 2 contains expected tracks: Track 3, Track 4, Track 5")
        
        // ASSERT: Verify database integrity - all 5 tracks still exist
        val finalTracks = getTracksUseCase.getAllTracks().getOrThrow()
        assertEquals(5, finalTracks.size, "All 5 tracks should still exist in database")
        println("✓ All 5 tracks still exist in database")
        
        println("\n✅ TEST PASSED: Successfully managed playlists with REAL database!")
    }
    
    @Test
    fun `verify track order in playlists matches insertion order`() = runBlocking {
        // Create playlist
        val playlist = managePlaylistUseCase.createPlaylist("Ordered Playlist").getOrThrow()
        
        // Add tracks in specific order: track 5, 2, 4, 1, 3
        val orderedGuids = listOf(
            trackGuids[4], // Track 5
            trackGuids[1], // Track 2
            trackGuids[3], // Track 4
            trackGuids[0], // Track 1
            trackGuids[2]  // Track 3
        )
        
        managePlaylistUseCase.addTracksToPlaylist(playlist.id, orderedGuids).getOrThrow()
        
        // Verify order is preserved
        val playlistWithTracks = managePlaylistUseCase.getPlaylistWithTracks(playlist.id).getOrThrow()
        assertEquals(5, playlistWithTracks.tracks.size)
        
        assertEquals("track5.mp3", playlistWithTracks.tracks[0].title)
        assertEquals("track2.mp3", playlistWithTracks.tracks[1].title)
        assertEquals("track4.mp3", playlistWithTracks.tracks[2].title)
        assertEquals("track1.mp3", playlistWithTracks.tracks[3].title)
        assertEquals("track3.mp3", playlistWithTracks.tracks[4].title)
        
        println("✅ Track order preserved in playlist!")
    }
    
    @Test
    fun `test removing tracks from playlist using real database`() = runBlocking {
        // Create playlist and add all 5 tracks
        val playlist = managePlaylistUseCase.createPlaylist("Test Playlist").getOrThrow()
        managePlaylistUseCase.addTracksToPlaylist(playlist.id, trackGuids).getOrThrow()
        
        // Verify all 5 tracks added
        var playlistWithTracks = managePlaylistUseCase.getPlaylistWithTracks(playlist.id).getOrThrow()
        assertEquals(5, playlistWithTracks.tracks.size)
        
        // Remove 2 tracks (first and last)
        val tracksToRemove = listOf(trackGuids[0], trackGuids[4])
        managePlaylistUseCase.removeTracksFromPlaylist(playlist.id, tracksToRemove).getOrThrow()
        
        // Verify only 3 tracks remain
        playlistWithTracks = managePlaylistUseCase.getPlaylistWithTracks(playlist.id).getOrThrow()
        assertEquals(3, playlistWithTracks.tracks.size)
        
        // Verify correct tracks remain (tracks 2, 3, 4)
        val remainingTitles = playlistWithTracks.tracks.map { it.title }
        assertTrue(remainingTitles.contains("track2.mp3"))
        assertTrue(remainingTitles.contains("track3.mp3"))
        assertTrue(remainingTitles.contains("track4.mp3"))
        
        println("✅ Successfully removed tracks from playlist in REAL database!")
    }
    
    @Test
    fun `test same track can be in multiple playlists`() = runBlocking {
        // Create 2 playlists
        val playlist1 = managePlaylistUseCase.createPlaylist("Rock Playlist").getOrThrow()
        val playlist2 = managePlaylistUseCase.createPlaylist("Favorites").getOrThrow()
        
        // Add the same track (Track 1) to both playlists
        val sharedTrackGuid = trackGuids[0]
        managePlaylistUseCase.addTracksToPlaylist(playlist1.id, listOf(sharedTrackGuid)).getOrThrow()
        managePlaylistUseCase.addTracksToPlaylist(playlist2.id, listOf(sharedTrackGuid)).getOrThrow()
        
        // Verify Track 1 exists in both playlists
        val playlist1Tracks = managePlaylistUseCase.getPlaylistWithTracks(playlist1.id).getOrThrow()
        val playlist2Tracks = managePlaylistUseCase.getPlaylistWithTracks(playlist2.id).getOrThrow()
        
        assertEquals(1, playlist1Tracks.tracks.size)
        assertEquals(1, playlist2Tracks.tracks.size)
        
        assertEquals("track1.mp3", playlist1Tracks.tracks[0].title)
        assertEquals("track1.mp3", playlist2Tracks.tracks[0].title)
        
        assertEquals(sharedTrackGuid, playlist1Tracks.tracks[0].guid)
        assertEquals(sharedTrackGuid, playlist2Tracks.tracks[0].guid)
        
        println("✅ Same track successfully shared across multiple playlists!")
    }
    
    @Test
    fun `test deleting playlist does not delete tracks from database`() = runBlocking {
        // Create playlist and add all tracks
        val playlist = managePlaylistUseCase.createPlaylist("Temporary Playlist").getOrThrow()
        managePlaylistUseCase.addTracksToPlaylist(playlist.id, trackGuids).getOrThrow()
        
        // Verify playlist has tracks
        val playlistBefore = managePlaylistUseCase.getPlaylistWithTracks(playlist.id).getOrThrow()
        assertEquals(5, playlistBefore.tracks.size)
        
        // Delete the playlist
        managePlaylistUseCase.deletePlaylist(playlist.id).getOrThrow()
        
        // Verify playlist is deleted
        val allPlaylists = managePlaylistUseCase.getAllPlaylists().getOrThrow()
        assertEquals(0, allPlaylists.size)
        
        // Verify all 5 tracks still exist in database
        val allTracks = getTracksUseCase.getAllTracks().getOrThrow()
        assertEquals(5, allTracks.size, "All tracks should still exist after playlist deletion")
        
        println("✅ Deleting playlist does NOT delete tracks from database!")
    }
}
