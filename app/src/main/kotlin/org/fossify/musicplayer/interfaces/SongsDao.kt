package org.fossify.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.musicplayer.models.Track
import java.util.UUID

@Dao
interface SongsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracks: List<Track>)

    @Query("SELECT * FROM tracks")
    fun getAll(): List<Track>

    // DEPRECATED: Use PlaylistTracksDao.getTracksForPlaylist() instead (junction table)
    // @Query("SELECT * FROM tracks WHERE playlist_id = :playlistId")
    // fun getTracksFromPlaylist(playlistId: Int): List<Track>

    @Query("SELECT * FROM tracks WHERE artist_id = :artistId")
    fun getTracksFromArtist(artistId: Long): List<Track>

    @Query("SELECT * FROM tracks WHERE album_id = :albumId")
    fun getTracksFromAlbum(albumId: Long): List<Track>

    // DEPRECATED: Use PlaylistTracksDao.getPlaylistTrackCount() instead (junction table)
    // @Query("SELECT COUNT(*) FROM tracks WHERE playlist_id = :playlistId")
    // fun getTracksCountFromPlaylist(playlistId: Int): Int

    @Query("SELECT * FROM tracks WHERE folder_name = :folderName COLLATE NOCASE GROUP BY COALESCE(guid, path)")
    fun getTracksFromFolder(folderName: String): List<Track>

    @Query("SELECT * FROM tracks WHERE media_store_id = :mediaStoreId")
    fun getTrackWithMediaStoreId(mediaStoreId: Long): Track?

    @Query("SELECT * FROM tracks WHERE genre_id = :genreId")
    fun getGenreTracks(genreId: Long): List<Track>

    @Query("DELETE FROM tracks WHERE media_store_id = :mediaStoreId")
    fun removeTrack(mediaStoreId: Long)

    @Query("DELETE FROM tracks WHERE guid = :guid")
    fun removeTrackByGuid(guid: UUID)

    // DEPRECATED: Use PlaylistTracksDao.removeAllTracksFromPlaylist() instead (junction table)
    // @Query("DELETE FROM tracks WHERE playlist_id = :playlistId")
    // fun removePlaylistSongs(playlistId: Int)

    // DEPRECATED: Use PlaylistTracksDao.removeTrackFromPlaylist() instead (junction table)
    // @Query("DELETE FROM tracks WHERE media_store_id = :mediaStoreId AND playlist_id = :playlistId")
    // fun removeTrackFromPlaylist(mediaStoreId: Long, playlistId: Int)

    @Query("UPDATE tracks SET path = :newPath, artist = :artist, title = :title WHERE path = :oldPath")
    fun updateSongInfo(newPath: String, artist: String, title: String, oldPath: String)

    @Query("UPDATE tracks SET cover_art = :coverArt WHERE media_store_id = :id")
    fun updateCoverArt(coverArt: String, id: Long)

    // DEPRECATED: Use PlaylistTracksDao.updateTrackPosition() instead (junction table)
    // @Query("UPDATE tracks SET order_in_playlist = :index WHERE id = :id")
    // fun updateOrderInPlaylist(index: Int, id: Long)

    @Query("UPDATE tracks SET tag_txxx_created_at_unix = :timestamp WHERE id = :id")
    fun updateCreatedOnTimestamp(timestamp: Long, id: Long)

    @Query("UPDATE tracks SET tag_txxx_created_at_unix = :timestamp, added_at_timestamp_unix = :dateAdded WHERE id = :id")
    fun updateTimestamps(timestamp: Long, dateAdded: Int, id: Long)

    @Query("SELECT * FROM tracks WHERE guid = :guid LIMIT 1")
    fun getTrackByGuid(guid: UUID): Track?

    @Query("SELECT * FROM tracks WHERE checksum_audio = :checksum LIMIT 1")
    fun getTrackByAudioChecksum(checksum: String): Track?

    @Query("SELECT COUNT(*) > 0 FROM tracks WHERE guid = :guid")
    fun existsByGuid(guid: UUID): Boolean

    @Query("SELECT COUNT(*) > 0 FROM tracks WHERE checksum_audio = :checksum")
    fun existsByAudioChecksum(checksum: String): Boolean

    @Query("SELECT * FROM tracks WHERE tag_txxx_created_at_unix IS NOT NULL ORDER BY tag_txxx_created_at_unix DESC")
    fun getTracksByRecordingDateDesc(): List<Track>

    @Query("SELECT * FROM tracks WHERE tag_txxx_created_at_unix >= :startTimestamp AND tag_txxx_created_at_unix <= :endTimestamp")
    fun getTracksByDateRange(startTimestamp: Long, endTimestamp: Long): List<Track>

    @Query("SELECT * FROM tracks WHERE transcription IS NOT NULL AND transcription != ''")
    fun getTracksWithTranscription(): List<Track>

    @Query("SELECT * FROM tracks WHERE guid IN (:guids)")
    fun getTracksByGuids(guids: List<UUID>): List<Track>
}
