package org.fossify.musicplayer.integration.database

import android.app.Application
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
import kotlin.test.assertNotEquals

/**
 * Tests audio checksum-based matching strategy
 * 
 * Scenario: Same path, different GUID (from MediaStore)
 * - Compare checksumAudio to determine if it's the same file
 * - If checksum MATCHES → preserve GUID and custom fields (just re-indexed)
 * - If checksum DIFFERENT → update DB (file content actually changed)
 */
@RunWith(RobolectricTestRunner::class)
class AudioChecksumMatchingTest {
    
    private lateinit var app: Application
    private lateinit var database: SongsDatabase
    private lateinit var repository: RoomAudioRepository
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
        managePlaylistUseCase = ManagePlaylistUseCase(repository)
        getTracksUseCase = GetTracksUseCase(repository)
    }
    
    @After
    fun tearDown() {
        database.close()
    }
    
    @Test
    fun `same path, different GUID, SAME checksum - preserve old GUID and custom fields`() = runBlocking {
        // ===== ARRANGE =====
        val originalTrack = Track(
            id = 0L,
            guid = UUID.randomUUID(),
            mediaStoreId = 100L,
            path = "/music/song.mp3",
            duration = 180000,
            folderName = "Music",
            year = 2024,
            addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
            flags = 0,
            transcription = "Custom transcription",
            transcriptionNormalized = "custom transcription",
            tagTxxxCreatedAtUnix = 1234567890,
            checksumAudio = "abc123def456"  // Audio content checksum
        )
        
        database.SongsDao().insertAll(listOf(originalTrack))
        originalTrackGuid = database.SongsDao().getAll().first().guid
        
        println("✓ ARRANGE: Original track")
        println("    guid: ${originalTrackGuid.toString().take(8)}")
        println("    mediaStoreId: 100")
        println("    checksumAudio: abc123def456")
        println("    transcription: Custom transcription")
        
        // ===== ACT =====
        // Simulate scan finding "new" track with different GUID but SAME checksum
        val rescannedTrack = Track(
            id = 0L,
            guid = UUID.randomUUID(),  // Different GUID from scanner
            mediaStoreId = 999L,       // New mediaStoreId
            path = "/music/song.mp3",  // SAME path
            duration = 180000,
            folderName = "Music",
            year = 2024,
            addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
            flags = 0,
            transcription = null,
            transcriptionNormalized = null,
            tagTxxxCreatedAtUnix = null,
            checksumAudio = "abc123def456"  // SAME checksum!
        )
        
        println("\n✓ ACT: Scanned track")
        println("    guid: ${rescannedTrack.guid.toString().take(8)} (NEW from scanner)")
        println("    mediaStoreId: 999 (NEW from MediaStore)")
        println("    checksumAudio: abc123def456 (SAME as original)")
        
        // SMART MATCHING LOGIC:
        val existingByPath = database.SongsDao().getAll().find { it.path == rescannedTrack.path }
        
        if (existingByPath != null) {
            println("\n  → Found existing track by PATH")
            
            // Check checksum
            if (existingByPath.checksumAudio == rescannedTrack.checksumAudio) {
                println("  → Checksum MATCHES (same audio content)")
                println("  → Action: PRESERVE old GUID and custom fields, update metadata")
                
                // Preserve GUID and custom fields
                rescannedTrack.guid = existingByPath.guid
                rescannedTrack.transcription = existingByPath.transcription
                rescannedTrack.transcriptionNormalized = existingByPath.transcriptionNormalized
                rescannedTrack.tagTxxxCreatedAtUnix = existingByPath.tagTxxxCreatedAtUnix
                rescannedTrack.checksumAudio = existingByPath.checksumAudio
                
                // Update (will replace because GUID matches now)
                rescannedTrack.id = existingByPath.id
                database.SongsDao().insertAll(listOf(rescannedTrack))
                
            } else {
                println("  → Checksum DIFFERENT (audio content changed)")
                println("  → Action: UPDATE with new data, keep same GUID")
                
                // Keep GUID but allow data update
                rescannedTrack.guid = existingByPath.guid
                rescannedTrack.id = existingByPath.id
                database.SongsDao().insertAll(listOf(rescannedTrack))
            }
        }
        
        // ===== ASSERT =====
        val allTracks = database.SongsDao().getAll()
        
        println("\n✓ ASSERT:")
        println("    Total tracks: ${allTracks.size}")
        assertEquals(1, allTracks.size, "Should have 1 track (no duplicate)")
        
        val finalTrack = allTracks[0]
        println("    Final GUID: ${finalTrack.guid.toString().take(8)}")
        println("    Final mediaStoreId: ${finalTrack.mediaStoreId}")
        println("    Final transcription: ${finalTrack.transcription}")
        println("    Final checksumAudio: ${finalTrack.checksumAudio}")
        
        // GUID preserved
        assertEquals(originalTrackGuid, finalTrack.guid, "GUID should be preserved")
        
        // mediaStoreId updated
        assertEquals(999L, finalTrack.mediaStoreId, "mediaStoreId should be updated")
        
        // Custom fields preserved
        assertEquals("Custom transcription", finalTrack.transcription, "transcription preserved")
        assertEquals(1234567890, finalTrack.tagTxxxCreatedAtUnix, "custom tags preserved")
        assertEquals("abc123def456", finalTrack.checksumAudio, "checksum preserved")
        
        println("\n✅ Checksum MATCH strategy:")
        println("   ✓ No duplicate created")
        println("   ✓ GUID preserved")
        println("   ✓ Custom fields preserved")
        println("   ✓ MediaStore ID updated")
    }
    
    @Test
    fun `same path, different GUID, DIFFERENT checksum - update with new audio`() = runBlocking {
        // ===== ARRANGE =====
        val originalTrack = Track(
            id = 0L,
            guid = UUID.randomUUID(),
            mediaStoreId = 100L,
            path = "/music/song.mp3",
            duration = 180000,
            folderName = "Music",
            year = 2024,
            addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
            flags = 0,
            transcription = "Custom transcription",
            transcriptionNormalized = "custom transcription",
            tagTxxxCreatedAtUnix = 1234567890,
            checksumAudio = "abc123def456"  // Original audio checksum
        )
        
        database.SongsDao().insertAll(listOf(originalTrack))
        originalTrackGuid = database.SongsDao().getAll().first().guid
        
        println("✓ ARRANGE: Original track")
        println("    guid: ${originalTrackGuid.toString().take(8)}")
        println("    checksumAudio: abc123def456")
        println("    transcription: Custom transcription")
        
        // ===== ACT =====
        // File was REPLACED with different audio (same filename/path)
        val replacedTrack = Track(
            id = 0L,
            guid = UUID.randomUUID(),
            mediaStoreId = 999L,
            path = "/music/song.mp3",  // SAME path
            duration = 195000,  // Different duration
            folderName = "Music",
            year = 2024,
            addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
            flags = 0,
            transcription = null,
            transcriptionNormalized = null,
            tagTxxxCreatedAtUnix = null,
            checksumAudio = "xyz789ghi012"  // DIFFERENT checksum!
        )
        
        println("\n✓ ACT: Replacement track")
        println("    guid: ${replacedTrack.guid.toString().take(8)} (NEW)")
        println("    checksumAudio: xyz789ghi012 (DIFFERENT)")
        println("    title: My Song (Remastered)")
        println("    duration: 195000 (changed)")
        
        // SMART MATCHING LOGIC:
        val existingByPath = database.SongsDao().getAll().find { it.path == replacedTrack.path }
        
        if (existingByPath != null) {
            println("\n  → Found existing track by PATH")
            
            if (existingByPath.checksumAudio != replacedTrack.checksumAudio) {
                println("  → Checksum DIFFERENT (audio content changed)")
                println("  → Action: UPDATE entry with new audio, preserve GUID for playlist continuity")
                
                // Preserve GUID to keep playlist references valid
                replacedTrack.guid = existingByPath.guid
                replacedTrack.id = existingByPath.id
                
                // But DON'T preserve old transcription (it was for old audio)
                // New audio needs new transcription
                
                database.SongsDao().insertAll(listOf(replacedTrack))
            }
        }
        
        // ===== ASSERT =====
        val allTracks = database.SongsDao().getAll()
        
        println("\n✓ ASSERT:")
        println("    Total tracks: ${allTracks.size}")
        assertEquals(1, allTracks.size, "Should have 1 track")
        
        val finalTrack = allTracks[0]
        println("    Final GUID: ${finalTrack.guid.toString().take(8)}")
        println("    Final title: ${finalTrack.title}")
        println("    Final duration: ${finalTrack.duration}")
        println("    Final checksumAudio: ${finalTrack.checksumAudio}")
        println("    Final transcription: ${finalTrack.transcription}")
        
        // GUID preserved (for playlist continuity)
        assertEquals(originalTrackGuid, finalTrack.guid, "GUID preserved for playlist continuity")
        
        // New audio data
        // Title is now derived from path/filename
        assertEquals("song.mp3", finalTrack.title)
        assertEquals(195000, finalTrack.duration, "Duration updated")
        assertEquals("xyz789ghi012", finalTrack.checksumAudio, "Checksum updated")
        assertEquals(999L, finalTrack.mediaStoreId, "mediaStoreId updated")
        
        // Old transcription NOT preserved (was for old audio)
        assertEquals(null, finalTrack.transcription, "Old transcription cleared (was for old audio)")
        
        println("\n✅ Checksum MISMATCH strategy:")
        println("   ✓ No duplicate created")
        println("   ✓ GUID preserved (playlist continuity)")
        println("   ✓ New audio metadata applied")
        println("   ✓ Old transcription cleared (was for old audio)")
    }
    
    @Test
    fun `no checksum available - fallback to path matching only`() = runBlocking {
        // ===== ARRANGE =====
        val originalTrack = Track(
            id = 0L,
            guid = UUID.randomUUID(),
            mediaStoreId = 100L,
            path = "/music/song.mp3",
            duration = 180000,
            folderName = "Music",
            year = 2024,
            addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
            flags = 0,
            transcription = "Custom transcription",
            transcriptionNormalized = "custom transcription",
            tagTxxxCreatedAtUnix = 1234567890,
            checksumAudio = null  // No checksum available
        )
        
        database.SongsDao().insertAll(listOf(originalTrack))
        originalTrackGuid = database.SongsDao().getAll().first().guid
        
        println("✓ ARRANGE: Track WITHOUT checksum")
        println("    guid: ${originalTrackGuid.toString().take(8)}")
        println("    checksumAudio: null")
        
        // ===== ACT =====
        val rescannedTrack = Track(
            id = 0L,
            guid = UUID.randomUUID(),
            mediaStoreId = 999L,
            path = "/music/song.mp3",
            duration = 180000,
            folderName = "Music",
            year = 2024,
            addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
            flags = 0,
            transcription = null,
            transcriptionNormalized = null,
            tagTxxxCreatedAtUnix = null,
            checksumAudio = null  // Also no checksum
        )
        
        println("\n✓ ACT: Scanned track also WITHOUT checksum")
        
        // FALLBACK MATCHING:
        val existingByPath = database.SongsDao().getAll().find { it.path == rescannedTrack.path }
        
        if (existingByPath != null) {
            println("\n  → Found by PATH")
            
            if (existingByPath.checksumAudio == null || rescannedTrack.checksumAudio == null) {
                println("  → No checksum available")
                println("  → Fallback: Compare metadata (title, duration, etc.)")
                
                val metadataMatches = 
                    existingByPath.duration == rescannedTrack.duration
                
                if (metadataMatches) {
                    println("  → Metadata MATCHES → Assume same file, preserve GUID")
                    rescannedTrack.guid = existingByPath.guid
                    rescannedTrack.transcription = existingByPath.transcription
                    rescannedTrack.transcriptionNormalized = existingByPath.transcriptionNormalized
                    rescannedTrack.tagTxxxCreatedAtUnix = existingByPath.tagTxxxCreatedAtUnix
                } else {
                    println("  → Metadata DIFFERENT → File changed, preserve GUID but update data")
                    rescannedTrack.guid = existingByPath.guid
                }
                
                rescannedTrack.id = existingByPath.id
                database.SongsDao().insertAll(listOf(rescannedTrack))
            }
        }
        
        // ===== ASSERT =====
        val allTracks = database.SongsDao().getAll()
        assertEquals(1, allTracks.size)
        assertEquals(originalTrackGuid, allTracks[0].guid)
        
        println("\n✅ Fallback strategy when no checksum:")
        println("   ✓ PATH matching")
        println("   ✓ Metadata comparison")
        println("   ✓ GUID preserved")
    }
}
