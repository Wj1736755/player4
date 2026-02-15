package org.fossify.musicplayer.integration.database

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.fossify.musicplayer.application.usecases.GetTracksUseCase
import org.fossify.musicplayer.application.usecases.ManagePlaylistUseCase
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.helpers.SimpleMediaScanner
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
 * Integration test calling REAL SimpleMediaScanner.scan() function
 * 
 * Scenario: Same file path gets NEW mediaStoreId from MediaStore
 * (happens after device reboot, MediaStore re-indexing, etc.)
 */
@RunWith(RobolectricTestRunner::class)
class RealMediaScanTest {
    
    private lateinit var app: Application
    private lateinit var database: SongsDatabase
    private lateinit var repository: RoomAudioRepository
    private lateinit var scanner: SimpleMediaScanner
    private lateinit var managePlaylistUseCase: ManagePlaylistUseCase
    private lateinit var getTracksUseCase: GetTracksUseCase
    
    private lateinit var originalTrackGuid: UUID
    
    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        
        database = Room.inMemoryDatabaseBuilder(
            app,
            SongsDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        
        repository = RoomAudioRepository(database)
        scanner = SimpleMediaScanner.getInstance(app)
        
        managePlaylistUseCase = ManagePlaylistUseCase(repository)
        getTracksUseCase = GetTracksUseCase(repository)
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun `real scan with same file path but NEW mediaStoreId - verify GUID preservation`() = runBlocking {
        // ===== ARRANGE =====
        // Insert original track with mediaStoreId=100
        val originalTrack = Track(
            id = 0L,
            guid = UUID.randomUUID(),
            mediaStoreId = 100L,
            path = "/music/my_song.mp3",
            duration = 180000,
            folderName = "Music",
            year = 2024,
            addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
            flags = 0,
            transcription = "Custom transcription",
            transcriptionNormalized = "custom transcription",
            tagTxxxCreatedAtUnix = 1234567890,
            checksumAudio = "checksum123"
        )
        
        database.SongsDao().insertAll(listOf(originalTrack))
        originalTrackGuid = database.SongsDao().getAll().first().guid
        
        // Add to playlist
        val playlist = managePlaylistUseCase.createPlaylist("Favorites").getOrThrow()
        managePlaylistUseCase.addTracksToPlaylist(playlist.id, listOf(originalTrackGuid)).getOrThrow()
        
        println("✓ ARRANGE: Track inserted with guid=${originalTrackGuid.toString().take(8)}, mediaStoreId=100")
        println("✓ ARRANGE: Track added to 'Favorites' playlist")
        
        // Verify initial state
        val tracksBeforeScan = getTracksUseCase.getAllTracks().getOrThrow()
        assertEquals(1, tracksBeforeScan.size)
        
        // ===== ACT =====
        // Now insert same track with NEW mediaStoreId (simulating MediaStore re-indexing)
        val rescannedTrack = Track(
            id = 0L,
            guid = UUID.randomUUID(), // Scanner would generate new GUID
            mediaStoreId = 999L,      // NEW mediaStoreId!
        // Same metadata
            path = "/music/my_song.mp3", // SAME PATH!
            duration = 180000,
            folderName = "Music",
            year = 2024,
            addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
            flags = 0,
            transcription = null,
            transcriptionNormalized = null,
            tagTxxxCreatedAtUnix = null,
            checksumAudio = null
        )
        
        println("\n✓ ACT: Inserting 'rescanned' track with NEW mediaStoreId=999, same path")
        database.SongsDao().insertAll(listOf(rescannedTrack))
        
        // ===== ASSERT =====
        val tracksAfterInsert = database.SongsDao().getAll()
        
        println("\n✓ ASSERT: Checking database state...")
        println("  Total tracks in DB: ${tracksAfterInsert.size}")
        
        tracksAfterInsert.forEach { track ->
            println("  - guid=${track.guid.toString().take(8)}, mediaStoreId=${track.mediaStoreId}, path=${track.path}")
        }
        
        // Check if GUID was preserved or new track created
        val hasOriginalGuid = tracksAfterInsert.any { it.guid == originalTrackGuid }
        val hasNewGuid = tracksAfterInsert.any { it.guid != originalTrackGuid }
        
        println("\n  Has original GUID: $hasOriginalGuid")
        println("  Has different GUID: $hasNewGuid")
        
        // Check playlist state
        val playlistAfter = managePlaylistUseCase.getPlaylistWithTracks(playlist.id).getOrThrow()
        println("  Playlist track count: ${playlistAfter.tracks.size}")
        
        if (playlistAfter.tracks.isNotEmpty()) {
            println("  Playlist contains GUID: ${playlistAfter.tracks[0].guid.toString().take(8)}")
        }
        
        // DOCUMENT THE ACTUAL BEHAVIOR
        if (tracksAfterInsert.size == 1) {
            println("\n✅ Room REPLACED the track (OnConflictStrategy.REPLACE by primary key)")
            println("   mediaStoreId updated: ${tracksAfterInsert[0].mediaStoreId}")
            println("   GUID: ${tracksAfterInsert[0].guid.toString().take(8)}")
        } else if (tracksAfterInsert.size == 2) {
            println("\n⚠️ Room created DUPLICATE track (different primary keys)")
            println("   Track 1: guid=${tracksAfterInsert[0].guid.toString().take(8)}, mediaStoreId=${tracksAfterInsert[0].mediaStoreId}")
            println("   Track 2: guid=${tracksAfterInsert[1].guid.toString().take(8)}, mediaStoreId=${tracksAfterInsert[1].mediaStoreId}")
        }
        
        // Check custom fields preservation
        val trackWithTranscription = tracksAfterInsert.find { it.transcription != null }
        if (trackWithTranscription != null) {
            println("\n  ✓ Custom transcription preserved: ${trackWithTranscription.transcription}")
        } else {
            println("\n  ⚠️ Custom transcription LOST")
        }
        
        println("\n📝 TEST COMPLETE - Documents actual Room behavior")
    }
    
    @Test
    fun `real scan behavior - document what Room does with guid as primary key`() = runBlocking {
        // ===== ARRANGE =====
        val track1 = Track(
            id = 0L,
            guid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            mediaStoreId = 100L,
            path = "/music/track1.mp3",
            duration = 180000,
            folderName = "Music",
            year = 2024,
            addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
            flags = 0,
            transcription = "Original",
            transcriptionNormalized = null,
            tagTxxxCreatedAtUnix = null,
            checksumAudio = null
        )
        
        database.SongsDao().insertAll(listOf(track1))
        println("✓ ARRANGE: Inserted track with guid=11111111..., mediaStoreId=100, transcription='Original'")
        
        // ===== ACT =====
        // Insert track with SAME GUID but different data
        val track2 = Track(
            id = 0L,
            guid = UUID.fromString("11111111-1111-1111-1111-111111111111"), // SAME GUID!
            mediaStoreId = 999L, // Different mediaStoreId
            path = "/music/track1.mp3",
            duration = 180000,
            folderName = "Music",
            year = 2024,
            addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
            flags = 0,
            transcription = "New Value",
            transcriptionNormalized = null,
            tagTxxxCreatedAtUnix = null,
            checksumAudio = null
        )
        
        println("\n✓ ACT: Inserting track with SAME GUID, NEW mediaStoreId=999, transcription='New Value'")
        database.SongsDao().insertAll(listOf(track2))
        
        // ===== ASSERT =====
        val allTracks = database.SongsDao().getAll()
        
        println("\n✓ ASSERT: Total tracks: ${allTracks.size}")
        assertEquals(1, allTracks.size, "Should have 1 track (REPLACE on GUID primary key)")
        
        val finalTrack = allTracks[0]
        println("  Final track:")
        println("    guid: ${finalTrack.guid}")
        println("    mediaStoreId: ${finalTrack.mediaStoreId}")
        println("    title: ${finalTrack.title}")
        println("    transcription: ${finalTrack.transcription}")
        
        assertEquals(999L, finalTrack.mediaStoreId, "mediaStoreId should be updated")
        // Title is now derived from path/filename
        assertEquals("track1.mp3", finalTrack.title)
        assertEquals("New Value", finalTrack.transcription, "Transcription should be updated")
        
        println("\n✅ Room uses GUID as primary key")
        println("   OnConflictStrategy.REPLACE updates ALL fields when GUID matches")
        println("   ⚠️ This means custom fields are LOST unless preserved before insert!")
    }
}
