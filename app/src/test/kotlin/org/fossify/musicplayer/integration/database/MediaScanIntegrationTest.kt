package org.fossify.musicplayer.integration.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.fossify.musicplayer.application.usecases.DeleteTracksUseCase
import org.fossify.musicplayer.application.usecases.GetTracksUseCase
import org.fossify.musicplayer.application.usecases.ManagePlaylistUseCase
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.fakes.FakeFileStorage
import org.fossify.musicplayer.infrastructure.adapters.RoomAudioRepository
import org.fossify.musicplayer.models.Track
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for Media Scan operations using REAL Room database
 * 
 * Tests simulate scan behavior:
 * - Tracks in DB and playlists
 * - Files deleted from disk → tracks removed from DB and playlists (cascade)
 * - New files discovered → new tracks added
 * - Mixed scenarios
 */
@RunWith(RobolectricTestRunner::class)
class MediaScanIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var database: SongsDatabase
    private lateinit var repository: RoomAudioRepository
    private lateinit var managePlaylistUseCase: ManagePlaylistUseCase
    private lateinit var getTracksUseCase: GetTracksUseCase
    private lateinit var deleteTracksUseCase: DeleteTracksUseCase
    private lateinit var fakeFileStorage: FakeFileStorage
    
    private lateinit var initialTrackGuids: List<UUID>
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        database = Room.inMemoryDatabaseBuilder(
            context,
            SongsDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        
        repository = RoomAudioRepository(database)
        fakeFileStorage = org.fossify.musicplayer.fakes.FakeFileStorage()
        
        managePlaylistUseCase = ManagePlaylistUseCase(repository)
        getTracksUseCase = GetTracksUseCase(repository)
        deleteTracksUseCase = DeleteTracksUseCase(repository, fakeFileStorage)
        
        insertInitialTracks()
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    private fun insertInitialTracks() = runBlocking {
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
        
        database.SongsDao().insertAll(tracks)
        
        initialTrackGuids = database.SongsDao().getAll().map { it.guid }
        
        println("✓ Inserted 5 initial tracks into REAL database")
    }
    
    @Test
    fun `scan with all files present - tracks remain in DB and playlists`() = runBlocking {
        // ARRANGE: Create playlists with tracks
        val playlist1 = managePlaylistUseCase.createPlaylist("Playlist 1").getOrThrow()
        val playlist2 = managePlaylistUseCase.createPlaylist("Playlist 2").getOrThrow()
        
        // Add tracks 1-2 to playlist 1, tracks 3-5 to playlist 2
        managePlaylistUseCase.addTracksToPlaylist(playlist1.id, initialTrackGuids.take(2)).getOrThrow()
        managePlaylistUseCase.addTracksToPlaylist(playlist2.id, initialTrackGuids.drop(2)).getOrThrow()
        
        println("✓ Setup: Playlist 1 has 2 tracks, Playlist 2 has 3 tracks")
        
        // ACT: Simulate scan where ALL files still exist
        // (In real scan, no files would be deleted, so DB remains unchanged)
        // We simulate this by NOT calling deleteTracksByGuid
        val allTracksAfterScan = getTracksUseCase.getAllTracks().getOrThrow()
        
        // ASSERT: All 5 tracks still in database
        assertEquals(5, allTracksAfterScan.size, "All tracks should remain in DB")
        println("✓ All 5 tracks still in database")
        
        // ASSERT: Playlists unchanged
        val playlist1AfterScan = managePlaylistUseCase.getPlaylistWithTracks(playlist1.id).getOrThrow()
        val playlist2AfterScan = managePlaylistUseCase.getPlaylistWithTracks(playlist2.id).getOrThrow()
        
        assertEquals(2, playlist1AfterScan.tracks.size, "Playlist 1 should still have 2 tracks")
        assertEquals(3, playlist2AfterScan.tracks.size, "Playlist 2 should still have 3 tracks")
        
        assertEquals("track1.mp3", playlist1AfterScan.tracks[0].title)
        assertEquals("track2.mp3", playlist1AfterScan.tracks[1].title)
        assertEquals("track3.mp3", playlist2AfterScan.tracks[0].title)
        
        println("✓ Playlists unchanged after scan")
        println("✅ TEST PASSED: Scan with all files present preserves everything")
    }
    
    @Test
    fun `scan with deleted files - tracks removed from DB and playlists cascade`() = runBlocking {
        // ARRANGE: Create playlists
        val playlist1 = managePlaylistUseCase.createPlaylist("Playlist 1").getOrThrow()
        val playlist2 = managePlaylistUseCase.createPlaylist("Playlist 2").getOrThrow()
        
        // Playlist 1: tracks 1, 2, 3
        managePlaylistUseCase.addTracksToPlaylist(playlist1.id, initialTrackGuids.take(3)).getOrThrow()
        // Playlist 2: tracks 3, 4, 5
        managePlaylistUseCase.addTracksToPlaylist(playlist2.id, initialTrackGuids.drop(2)).getOrThrow()
        
        println("✓ Setup: Playlist 1 has tracks [1,2,3], Playlist 2 has tracks [3,4,5]")
        
        // ACT: Simulate scan where files for tracks 2 and 4 are DELETED from disk
        // In real scan, cleanupDatabase() would call deleteTracksByGuid()
        val deletedTrackGuids = listOf(initialTrackGuids[1], initialTrackGuids[3]) // Track 2 and 4
        println("✓ Simulating deletion of Track 2 and Track 4 (files not found during scan)")
        
        deleteTracksUseCase.execute(deletedTrackGuids, deleteFiles = false).getOrThrow()
        
        // ASSERT: Only 3 tracks remain (tracks 1, 3, 5)
        val allTracksAfterScan = getTracksUseCase.getAllTracks().getOrThrow()
        assertEquals(3, allTracksAfterScan.size, "Should have 3 tracks remaining")
        
        val remainingTitles = allTracksAfterScan.map { it.title }.sorted()
        assertEquals(listOf("track1.mp3", "track3.mp3", "track5.mp3"), remainingTitles)
        println("✓ DB now has 3 tracks: [track1.mp3, track3.mp3, track5.mp3]")
        
        // ASSERT: Playlists updated (CASCADE delete removed playlist entries)
        val playlist1AfterScan = managePlaylistUseCase.getPlaylistWithTracks(playlist1.id).getOrThrow()
        val playlist2AfterScan = managePlaylistUseCase.getPlaylistWithTracks(playlist2.id).getOrThrow()
        
        // Playlist 1 had [1,2,3], Track 2 deleted → now has [1,3]
        assertEquals(2, playlist1AfterScan.tracks.size, "Playlist 1 should have 2 tracks after cascade delete")
        val playlist1Titles = playlist1AfterScan.tracks.map { it.title }
        assertTrue(playlist1Titles.contains("track1.mp3"))
        assertTrue(playlist1Titles.contains("track3.mp3"))
        assertFalse(playlist1Titles.contains("track2.mp3"), "track2.mp3 should be removed from playlist")
        println("✓ Playlist 1 now has tracks [track1.mp3, track3.mp3] (track2.mp3 removed)")
        
        // Playlist 2 had [3,4,5], Track 4 deleted → now has [3,5]
        assertEquals(2, playlist2AfterScan.tracks.size, "Playlist 2 should have 2 tracks after cascade delete")
        val playlist2Titles = playlist2AfterScan.tracks.map { it.title }
        assertTrue(playlist2Titles.contains("track3.mp3"))
        assertTrue(playlist2Titles.contains("track5.mp3"))
        assertFalse(playlist2Titles.contains("track4.mp3"), "track4.mp3 should be removed from playlist")
        println("✓ Playlist 2 now has tracks [track3.mp3, track5.mp3] (track4.mp3 removed)")
        
        println("✅ TEST PASSED: Cascade delete correctly removed tracks from playlists")
    }
    
    @Test
    fun `scan with all files deleted - all tracks removed, playlists become empty`() = runBlocking {
        // ARRANGE: Create playlists with all tracks
        val playlist1 = managePlaylistUseCase.createPlaylist("Favorites").getOrThrow()
        val playlist2 = managePlaylistUseCase.createPlaylist("Rock Hits").getOrThrow()
        
        managePlaylistUseCase.addTracksToPlaylist(playlist1.id, initialTrackGuids.take(3)).getOrThrow()
        managePlaylistUseCase.addTracksToPlaylist(playlist2.id, initialTrackGuids).getOrThrow()
        
        println("✓ Setup: Playlist 1 has 3 tracks, Playlist 2 has 5 tracks")
        
        // ACT: Simulate scan where ALL files are deleted
        println("✓ Simulating deletion of ALL files (complete library wipe)")
        deleteTracksUseCase.execute(initialTrackGuids, deleteFiles = false).getOrThrow()
        
        // ASSERT: No tracks in database
        val allTracksAfterScan = getTracksUseCase.getAllTracks().getOrThrow()
        assertEquals(0, allTracksAfterScan.size, "All tracks should be deleted")
        println("✓ Database is now empty (0 tracks)")
        
        // ASSERT: Both playlists are empty (but still exist)
        val playlist1AfterScan = managePlaylistUseCase.getPlaylistWithTracks(playlist1.id).getOrThrow()
        val playlist2AfterScan = managePlaylistUseCase.getPlaylistWithTracks(playlist2.id).getOrThrow()
        
        assertEquals(0, playlist1AfterScan.tracks.size, "Playlist 1 should be empty")
        assertEquals(0, playlist2AfterScan.tracks.size, "Playlist 2 should be empty")
        
        // Playlists themselves still exist
        assertEquals("Favorites", playlist1AfterScan.playlist.title)
        assertEquals("Rock Hits", playlist2AfterScan.playlist.title)
        
        println("✓ Both playlists are empty but still exist")
        println("✅ TEST PASSED: All files deleted scenario handled correctly")
    }
    
    @Test
    fun `scan with new files - new tracks added, existing playlists unchanged`() = runBlocking {
        // ARRANGE: Create playlist with first 3 tracks
        val playlist = managePlaylistUseCase.createPlaylist("Original Playlist").getOrThrow()
        managePlaylistUseCase.addTracksToPlaylist(playlist.id, initialTrackGuids.take(3)).getOrThrow()
        
        println("✓ Setup: Playlist has 3 tracks, DB has 5 tracks")
        
        // ACT: Simulate scan finding 2 NEW audio files
        val newTracks = listOf(
            Track(
                id = 0L,
                guid = UUID.randomUUID(),
                mediaStoreId = 100L,
                path = "/music/new_track1.mp3",
                duration = 200000,
                folderName = "Music",
                year = 2024,
                addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
                flags = 0,
                transcription = null,
                transcriptionNormalized = null,
                tagTxxxCreatedAtUnix = null,
                checksumAudio = null
            ),
            Track(
                id = 0L,
                guid = UUID.randomUUID(),
                mediaStoreId = 101L,
                path = "/music/new_track2.mp3",
                duration = 210000,
                folderName = "Music",
                year = 2024,
                addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
                flags = 0,
                transcription = null,
                transcriptionNormalized = null,
                tagTxxxCreatedAtUnix = null,
                checksumAudio = null
            )
        )
        
        println("✓ Simulating scan finding 2 new audio files")
        database.SongsDao().insertAll(newTracks)
        
        // ASSERT: Database now has 7 tracks (5 original + 2 new)
        val allTracksAfterScan = getTracksUseCase.getAllTracks().getOrThrow()
        assertEquals(7, allTracksAfterScan.size, "Should have 7 tracks (5 original + 2 new)")
        println("✓ Database now has 7 tracks")
        
        // ASSERT: Original playlist UNCHANGED (new tracks not automatically added to playlists)
        val playlistAfterScan = managePlaylistUseCase.getPlaylistWithTracks(playlist.id).getOrThrow()
        assertEquals(3, playlistAfterScan.tracks.size, "Playlist should still have 3 tracks")
        
        val playlistTitles = playlistAfterScan.tracks.map { it.title }
        assertTrue(playlistTitles.contains("track1.mp3"))
        assertTrue(playlistTitles.contains("track2.mp3"))
        assertTrue(playlistTitles.contains("track3.mp3"))
        assertFalse(playlistTitles.contains("new_track1.mp3"))
        assertFalse(playlistTitles.contains("new_track2.mp3"))
        
        println("✓ Original playlist unchanged (still has 3 original tracks)")
        println("✓ New tracks exist in DB but NOT in any playlist")
        println("✅ TEST PASSED: New files added without affecting existing playlists")
    }
    
    @Test
    fun `mixed scan scenario - some deleted, some new, some unchanged`() = runBlocking {
        // ARRANGE: Create 2 playlists
        val playlist1 = managePlaylistUseCase.createPlaylist("Playlist 1").getOrThrow()
        val playlist2 = managePlaylistUseCase.createPlaylist("Playlist 2").getOrThrow()
        
        // Playlist 1: tracks 1, 2, 3
        managePlaylistUseCase.addTracksToPlaylist(playlist1.id, initialTrackGuids.take(3)).getOrThrow()
        // Playlist 2: tracks 3, 4, 5
        managePlaylistUseCase.addTracksToPlaylist(playlist2.id, initialTrackGuids.drop(2)).getOrThrow()
        
        println("✓ Setup: Playlist 1 = [Track 1,2,3], Playlist 2 = [Track 3,4,5]")
        println("✓ Initial DB: 5 tracks")
        
        // ACT 1: Delete track 2 (simulating file deletion)
        println("✓ Scan detects: Track 2 file deleted")
        deleteTracksUseCase.execute(listOf(initialTrackGuids[1]), deleteFiles = false).getOrThrow()
        
        // ACT 2: Add 2 new tracks (simulating new files discovered)
        val newTracks = (1..2).map { index ->
            Track(
                id = 0L,
                guid = UUID.randomUUID(),
                mediaStoreId = (200 + index).toLong(),
                path = "/music/new_track$index.mp3",
                duration = 220000,
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
        
        println("✓ Scan detects: 2 new audio files found")
        database.SongsDao().insertAll(newTracks)
        
        // ASSERT: Database now has 6 tracks (5 original - 1 deleted + 2 new = 6)
        val allTracksAfterScan = getTracksUseCase.getAllTracks().getOrThrow()
        assertEquals(6, allTracksAfterScan.size, "Should have 6 tracks (4 original + 2 new)")
        
        val allTitles = allTracksAfterScan.map { it.title }.sorted()
        assertTrue(allTitles.contains("track1.mp3"))
        assertFalse(allTitles.contains("track2.mp3"), "track2.mp3 should be deleted")
        assertTrue(allTitles.contains("track3.mp3"))
        assertTrue(allTitles.contains("track4.mp3"))
        assertTrue(allTitles.contains("track5.mp3"))
        assertTrue(allTitles.contains("new_track1.mp3"))
        assertTrue(allTitles.contains("new_track2.mp3"))
        
        println("✓ DB now has 6 tracks: [track1.mp3,track3.mp3,track4.mp3,track5.mp3, new_track1.mp3,new_track2.mp3]")
        
        // ASSERT: Playlist 1 updated (Track 2 removed via cascade)
        val playlist1AfterScan = managePlaylistUseCase.getPlaylistWithTracks(playlist1.id).getOrThrow()
        assertEquals(2, playlist1AfterScan.tracks.size, "Playlist 1 should have 2 tracks")
        
        val playlist1Titles = playlist1AfterScan.tracks.map { it.title }
        assertTrue(playlist1Titles.contains("track1.mp3"))
        assertTrue(playlist1Titles.contains("track3.mp3"))
        assertFalse(playlist1Titles.contains("track2.mp3"))
        
        println("✓ Playlist 1 now has [track1.mp3, track3.mp3] (track2.mp3 removed)")
        
        // ASSERT: Playlist 2 unchanged (tracks 3, 4, 5 still exist)
        val playlist2AfterScan = managePlaylistUseCase.getPlaylistWithTracks(playlist2.id).getOrThrow()
        assertEquals(3, playlist2AfterScan.tracks.size, "Playlist 2 should still have 3 tracks")
        
        val playlist2Titles = playlist2AfterScan.tracks.map { it.title }
        assertTrue(playlist2Titles.contains("track3.mp3"))
        assertTrue(playlist2Titles.contains("track4.mp3"))
        assertTrue(playlist2Titles.contains("track5.mp3"))
        
        println("✓ Playlist 2 unchanged: [track3.mp3, track4.mp3, track5.mp3]")
        
        // ASSERT: New tracks NOT in any playlist
        assertFalse(playlist1Titles.contains("new_track1.mp3"))
        assertFalse(playlist2Titles.contains("new_track1.mp3"))
        
        println("✓ New tracks exist in DB but NOT in any playlist")
        println("✅ TEST PASSED: Mixed scan scenario handled correctly")
    }
    
    @Test
    fun `scan preserves tracks that appear in multiple playlists when deleted`() = runBlocking {
        // ARRANGE: Track 3 is in BOTH playlists
        val playlist1 = managePlaylistUseCase.createPlaylist("Playlist 1").getOrThrow()
        val playlist2 = managePlaylistUseCase.createPlaylist("Playlist 2").getOrThrow()
        
        val track3Guid = initialTrackGuids[2] // Track 3
        
        managePlaylistUseCase.addTracksToPlaylist(playlist1.id, listOf(initialTrackGuids[0], track3Guid)).getOrThrow()
        managePlaylistUseCase.addTracksToPlaylist(playlist2.id, listOf(track3Guid, initialTrackGuids[4])).getOrThrow()
        
        println("✓ Setup: Track 3 is in BOTH playlists")
        println("  Playlist 1 = [Track 1, Track 3]")
        println("  Playlist 2 = [Track 3, Track 5]")
        
        // ACT: Delete Track 3 (file deleted from disk)
        println("✓ Scan detects: Track 3 file deleted")
        deleteTracksUseCase.execute(listOf(track3Guid), deleteFiles = false).getOrThrow()
        
        // ASSERT: Track 3 removed from BOTH playlists via cascade
        val playlist1AfterScan = managePlaylistUseCase.getPlaylistWithTracks(playlist1.id).getOrThrow()
        val playlist2AfterScan = managePlaylistUseCase.getPlaylistWithTracks(playlist2.id).getOrThrow()
        
        assertEquals(1, playlist1AfterScan.tracks.size, "Playlist 1 should have 1 track")
        assertEquals("track1.mp3", playlist1AfterScan.tracks[0].title)
        
        assertEquals(1, playlist2AfterScan.tracks.size, "Playlist 2 should have 1 track")
        assertEquals("track5.mp3", playlist2AfterScan.tracks[0].title)
        
        println("✓ Track 3 removed from BOTH playlists")
        println("✓ Playlist 1 now has [Track 1]")
        println("✓ Playlist 2 now has [Track 5]")
        println("✅ TEST PASSED: Cascade delete works for tracks in multiple playlists")
    }
}
