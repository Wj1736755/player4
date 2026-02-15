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
    fun removeTrackFromPlaylist(playlistId: Int, trackGuid: UUID)
    
    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    fun removeAllTracksFromPlaylist(playlistId: Int)
    
    @Query("DELETE FROM playlist_tracks WHERE track_guid = :trackGuid")
    fun removeTrackFromAllPlaylists(trackGuid: UUID)
    
    // Query operations
    
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = :playlistId")
    fun getPlaylistTrackCount(playlistId: Int): Int
    
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
    
    @Transaction
    fun reorderPlaylist(playlistId: Int, orderedGuids: List<UUID>) {
        orderedGuids.forEachIndexed { index, guid ->
            updateTrackPosition(playlistId, guid, index)
        }
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
