package org.fossify.musicplayer.helpers

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.mockk.*
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.models.Playlist
import org.fossify.musicplayer.models.Track
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class DatabaseExporterTest {

    private lateinit var context: Context
    private lateinit var mockAudioHelper: AudioHelper
    private lateinit var playlistsDir: File
    private lateinit var backupDir: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        mockAudioHelper = mockk(relaxed = true)
        
        // Setup directories
        val externalDir = context.getExternalFilesDir(null)
        playlistsDir = File(externalDir, "playlists")
        backupDir = File(externalDir, "backup")
        
        // Clean up any existing files
        playlistsDir.deleteRecursively()
        backupDir.deleteRecursively()
        
        // Mock audioHelper extension property
        mockkStatic("org.fossify.musicplayer.extensions.ContextKt")
        every { any<Context>().audioHelper } returns mockAudioHelper
    }

    @After
    fun tearDown() {
        // Clean up
        playlistsDir.deleteRecursively()
        backupDir.deleteRecursively()
        unmockkAll()
    }

    @Test
    fun `exportAllPlaylists - exports single playlist successfully`() {
        // Given
        val playlist = Playlist(1, "Test Playlist", 2)
        val tracks = arrayListOf(
            createTrack(1, "Song 1", "Artist 1", "/path/to/song1.mp3", 180000, 0),
            createTrack(2, "Song 2", "Artist 2", "/path/to/song2.mp3", 240000, 1)
        )
        
        every { mockAudioHelper.getAllPlaylists() } returns arrayListOf(playlist)
        every { mockAudioHelper.getPlaylistTracks(playlist.id) } returns tracks
        
        val latch = CountDownLatch(1)
        var success: Boolean? = null
        var exportedCount: Int? = null
        var failedCount: Int? = null
        var path: String? = null
        
        // When
        DatabaseExporter.exportAllPlaylists(context) { s, e, f, p ->
            success = s
            exportedCount = e
            failedCount = f
            path = p
            latch.countDown()
        }
        
        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(success == true)
        assertEquals(1, exportedCount)
        assertEquals(0, failedCount)
        assertNotNull(path)
        
        // Verify M3U file was created
        val m3uFile = File(playlistsDir, "Test Playlist.m3u")
        assertTrue(m3uFile.exists())
        
        // Verify M3U content
        val content = m3uFile.readText()
        assertTrue(content.contains("#EXTM3U"))
        assertTrue(content.contains("Artist 1 - Song 1"))
        assertTrue(content.contains("Artist 2 - Song 2"))
        assertTrue(content.contains("/path/to/song1.mp3"))
        assertTrue(content.contains("/path/to/song2.mp3"))
    }

    @Test
    fun `exportAllPlaylists - exports multiple playlists successfully`() {
        // Given
        val playlist1 = Playlist(1, "Playlist 1", 2)
        val playlist2 = Playlist(2, "Playlist 2", 1)
        
        val tracks1 = arrayListOf(
            createTrack(1, "Song 1", "Artist 1", "/path/to/song1.mp3", 180000, 0)
        )
        val tracks2 = arrayListOf(
            createTrack(2, "Song 2", "Artist 2", "/path/to/song2.mp3", 240000, 0)
        )
        
        every { mockAudioHelper.getAllPlaylists() } returns arrayListOf(playlist1, playlist2)
        every { mockAudioHelper.getPlaylistTracks(1) } returns tracks1
        every { mockAudioHelper.getPlaylistTracks(2) } returns tracks2
        
        val latch = CountDownLatch(1)
        var exportedCount: Int? = null
        
        // When
        DatabaseExporter.exportAllPlaylists(context) { _, e, _, _ ->
            exportedCount = e
            latch.countDown()
        }
        
        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(2, exportedCount)
        
        // Verify both M3U files were created
        assertTrue(File(playlistsDir, "Playlist 1.m3u").exists())
        assertTrue(File(playlistsDir, "Playlist 2.m3u").exists())
    }

    @Test
    fun `exportAllPlaylists - skips empty playlists`() {
        // Given
        val playlist1 = Playlist(1, "Empty Playlist", 0)
        val playlist2 = Playlist(2, "Playlist with Tracks", 1)
        
        val tracks2 = arrayListOf(
            createTrack(1, "Song 1", "Artist 1", "/path/to/song1.mp3", 180000, 0)
        )
        
        every { mockAudioHelper.getAllPlaylists() } returns arrayListOf(playlist1, playlist2)
        every { mockAudioHelper.getPlaylistTracks(1) } returns arrayListOf()
        every { mockAudioHelper.getPlaylistTracks(2) } returns tracks2
        
        val latch = CountDownLatch(1)
        var exportedCount: Int? = null
        
        // When
        DatabaseExporter.exportAllPlaylists(context) { _, e, _, _ ->
            exportedCount = e
            latch.countDown()
        }
        
        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(1, exportedCount) // Only non-empty playlist exported
        
        // Verify only non-empty playlist file was created
        assertFalse(File(playlistsDir, "Empty Playlist.m3u").exists())
        assertTrue(File(playlistsDir, "Playlist with Tracks.m3u").exists())
    }

    @Test
    fun `exportAllPlaylists - sanitizes playlist title for filename`() {
        // Given
        val playlist = Playlist(1, "Test/Playlist:Name*", 1)
        val tracks = arrayListOf(
            createTrack(1, "Song 1", "Artist 1", "/path/to/song1.mp3", 180000, 0)
        )
        
        every { mockAudioHelper.getAllPlaylists() } returns arrayListOf(playlist)
        every { mockAudioHelper.getPlaylistTracks(playlist.id) } returns tracks
        
        val latch = CountDownLatch(1)
        
        // When
        DatabaseExporter.exportAllPlaylists(context) { _, _, _, _ ->
            latch.countDown()
        }
        
        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        
        // Verify filename is sanitized
        val sanitizedFile = File(playlistsDir, "Test_Playlist_Name_.m3u")
        assertTrue(sanitizedFile.exists())
        
        // Original filename should not exist
        assertFalse(File(playlistsDir, "Test/Playlist:Name*.m3u").exists())
    }

    @Test
    fun `exportAllPlaylists - sorts tracks by orderInPlaylist`() {
        // Given
        val playlist = Playlist(1, "Test Playlist", 3)
        val tracks = arrayListOf(
            createTrack(1, "Song 1", "Artist 1", "/path/to/song1.mp3", 180000, 2),
            createTrack(2, "Song 2", "Artist 2", "/path/to/song2.mp3", 240000, 0),
            createTrack(3, "Song 3", "Artist 3", "/path/to/song3.mp3", 200000, 1)
        )
        
        every { mockAudioHelper.getAllPlaylists() } returns arrayListOf(playlist)
        every { mockAudioHelper.getPlaylistTracks(playlist.id) } returns tracks
        
        val latch = CountDownLatch(1)
        
        // When
        DatabaseExporter.exportAllPlaylists(context) { _, _, _, _ ->
            latch.countDown()
        }
        
        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        
        val m3uFile = File(playlistsDir, "Test Playlist.m3u")
        val content = m3uFile.readText()
        
        // Verify tracks are in correct order (0, 1, 2)
        val song2Index = content.indexOf("Song 2")
        val song3Index = content.indexOf("Song 3")
        val song1Index = content.indexOf("Song 1")
        
        assertTrue(song2Index < song3Index)
        assertTrue(song3Index < song1Index)
    }

    @Test
    fun `exportAllPlaylists - handles exception gracefully and counts failures`() {
        // Given
        val playlist1 = Playlist(1, "Valid Playlist", 1)
        val playlist2 = Playlist(2, "Invalid Playlist", 1)
        
        val tracks1 = arrayListOf(
            createTrack(1, "Song 1", "Artist 1", "/path/to/song1.mp3", 180000, 0)
        )
        
        every { mockAudioHelper.getAllPlaylists() } returns arrayListOf(playlist1, playlist2)
        every { mockAudioHelper.getPlaylistTracks(1) } returns tracks1
        every { mockAudioHelper.getPlaylistTracks(2) } throws RuntimeException("Database error")
        
        val latch = CountDownLatch(1)
        var exportedCount: Int? = null
        var failedCount: Int? = null
        var success: Boolean? = null
        
        // When
        DatabaseExporter.exportAllPlaylists(context) { s, e, f, _ ->
            success = s
            exportedCount = e
            failedCount = f
            latch.countDown()
        }
        
        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(1, exportedCount)
        assertEquals(1, failedCount)
        assertTrue(success == true) // Success if at least one exported
    }

    @Test
    fun `exportAllPlaylists - returns failure when no playlists exist`() {
        // Given
        every { mockAudioHelper.getAllPlaylists() } returns arrayListOf()
        
        val latch = CountDownLatch(1)
        var success: Boolean? = null
        var exportedCount: Int? = null
        
        // When
        DatabaseExporter.exportAllPlaylists(context) { s, e, _, _ ->
            success = s
            exportedCount = e
            latch.countDown()
        }
        
        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertFalse(success == true)
        assertEquals(0, exportedCount)
    }

    @Test
    fun `exportAllPlaylists - formats duration correctly in M3U`() {
        // Given
        val playlist = Playlist(1, "Test Playlist", 1)
        // Duration is stored in milliseconds
        val tracks = arrayListOf(
            createTrack(1, "Song 1", "Artist 1", "/path/to/song1.mp3", 180000, 0) // 180000 ms = 180 seconds
        )
        
        every { mockAudioHelper.getAllPlaylists() } returns arrayListOf(playlist)
        every { mockAudioHelper.getPlaylistTracks(playlist.id) } returns tracks
        
        val latch = CountDownLatch(1)
        
        // When
        DatabaseExporter.exportAllPlaylists(context) { _, _, _, _ ->
            latch.countDown()
        }
        
        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        
        val m3uFile = File(playlistsDir, "Test Playlist.m3u")
        val content = m3uFile.readText()
        
        // Verify duration is included in M3U format
        // Note: In the current implementation, duration is in milliseconds
        assertTrue(content.contains("#EXTINF:180000"))
        assertTrue(content.contains(",Artist 1 - Song 1"))
    }

    @Test
    fun `exportAllPlaylistsFromOriginalApp - fails when database copy fails`() {
        // Given
        // copyFromOriginalApp will fail (no original app database)
        
        val latch = CountDownLatch(1)
        var success: Boolean? = null
        
        // When
        DatabaseExporter.exportAllPlaylistsFromOriginalApp(context) { s, _, _, _ ->
            success = s
            latch.countDown()
        }
        
        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertFalse(success == true)
    }


    // Helper function to create Track objects
    private fun createTrack(
        id: Long,
        title: String,
        artist: String,
        path: String,
        duration: Int,
        orderInPlaylist: Int,
        playlistId: Int = 1
    ): Track {
        return Track(
            id = id,
            mediaStoreId = id,
            title = title,
            artist = artist,
            path = path,
            duration = duration,
            album = "",
            genre = "",
            coverArt = "",
            playListId = playlistId,
            trackId = null,
            discNumber = null,
            folderName = "",
            albumId = 0L,
            artistId = 0L,
            genreId = 0L,
            year = 0,
            dateAdded = 0,
            orderInPlaylist = orderInPlaylist,
            flags = 0
        )
    }
}

