package org.fossify.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.fossify.musicplayer.models.PlaylistTrack
import org.fossify.musicplayer.models.Track
import java.util.UUID

@Dao
interface PlaylistTracksDao {
    
    // Basic CRUD operations
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(playlistTrack: PlaylistTrack)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(playlistTracks: List<PlaylistTrack>)
    
    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_guid = :trackGuid")
    fun deletePlaylistTrackRow(playlistId: Int, trackGuid: UUID)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_guid IN (:trackGuids)")
    fun deletePlaylistTrackRows(playlistId: Int, trackGuids: List<UUID>)

    @Transaction
    fun removeTrackFromPlaylist(playlistId: Int, trackGuid: UUID) {
        deletePlaylistTrackRow(playlistId, trackGuid)
        compactPositionsForPlaylist(playlistId)
    }

    @Transaction
    fun removeTracksFromPlaylist(playlistId: Int, trackGuids: List<UUID>) {
        if (trackGuids.isEmpty()) {
            return
        }
        deletePlaylistTrackRows(playlistId, trackGuids)
        compactPositionsForPlaylist(playlistId)
    }

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    fun removeAllTracksFromPlaylist(playlistId: Int)
    
    // Query operations
    
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = :playlistId")
    fun getPlaylistTrackCount(playlistId: Int): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlist_id = :playlistId")
    fun getMaxPositionForPlaylist(playlistId: Int): Int
    
    @Query("SELECT COUNT(*) > 0 FROM playlist_tracks WHERE playlist_id = :playlistId AND track_guid = :trackGuid")
    fun isTrackInPlaylist(playlistId: Int, trackGuid: UUID): Boolean
    
    @Query("""
        SELECT t.* 
        FROM tracks t
        INNER JOIN playlist_tracks pt ON t.guid = pt.track_guid
        WHERE pt.playlist_id = :playlistId
        ORDER BY pt.position ASC
    """)
    fun getTracksForPlaylist(playlistId: Int): List<Track>
    
    @Query("""
        SELECT pt.position
        FROM playlist_tracks pt
        WHERE pt.playlist_id = :playlistId AND pt.track_guid = :trackGuid
    """)
    fun getTrackPosition(playlistId: Int, trackGuid: UUID): Int?
    
    @Query("""
        SELECT pt.* 
        FROM playlist_tracks pt
        WHERE pt.playlist_id = :playlistId
        ORDER BY pt.position ASC
    """)
    fun getPlaylistTracksOrdered(playlistId: Int): List<PlaylistTrack>
    
    // Reordering operations
    
    @Query("UPDATE playlist_tracks SET position = :newPosition WHERE playlist_id = :playlistId AND track_guid = :trackGuid")
    fun updateTrackPosition(playlistId: Int, trackGuid: UUID, newPosition: Int)
    
    @Query("SELECT DISTINCT playlist_id FROM playlist_tracks WHERE track_guid = :trackGuid")
    fun getPlaylistIdsContainingTrack(trackGuid: UUID): List<Int>

    fun compactPositionsForPlaylist(playlistId: Int) {
        val ordered = getPlaylistTracksOrdered(playlistId)
        if (ordered.isEmpty()) {
            return
        }
        applyDenseZeroBasedPositions(playlistId, ordered.map { it.trackGuid })
    }

    @Transaction
    fun reorderPlaylist(playlistId: Int, orderedGuids: List<UUID>) {
        applyDenseZeroBasedPositions(playlistId, orderedGuids)
    }
    
    // Batch operations for migration/import
    
    @Transaction
    fun replaceAllTracksInPlaylist(playlistId: Int, tracks: List<Pair<UUID, Int>>) {
        removeAllTracksFromPlaylist(playlistId)
        val playlistTracks = tracks.map { (guid, position) ->
            PlaylistTrack(playlistId, guid, position)
        }
        insertAll(playlistTracks)
    }
}

private fun PlaylistTracksDao.applyDenseZeroBasedPositions(playlistId: Int, orderedGuids: List<UUID>) {
    if (orderedGuids.isEmpty()) {
        return
    }
    val stagingOffset = 1_000_000
    orderedGuids.forEachIndexed { index, guid ->
        updateTrackPosition(playlistId, guid, stagingOffset + index)
    }
    orderedGuids.forEachIndexed { index, guid ->
        updateTrackPosition(playlistId, guid, index)
    }
}
