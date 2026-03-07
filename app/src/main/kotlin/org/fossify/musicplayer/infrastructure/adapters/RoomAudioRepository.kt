package org.fossify.musicplayer.infrastructure.adapters

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.musicplayer.application.ports.AudioRepository
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.domain.model.PlaylistDomain
import org.fossify.musicplayer.domain.model.PlaylistWithTracks
import org.fossify.musicplayer.domain.model.TrackDomain
import org.fossify.musicplayer.models.Playlist
import org.fossify.musicplayer.models.PlaylistTrack
import org.fossify.musicplayer.models.Track
import java.util.UUID

class RoomAudioRepository(
    private val database: SongsDatabase
) : AudioRepository {
    
    override suspend fun getAllTracks(): List<TrackDomain> = withContext(Dispatchers.IO) {
        database.SongsDao().getAll().map { it.toDomain() }
    }
    
    override suspend fun getTrackByGuid(guid: UUID): TrackDomain? = withContext(Dispatchers.IO) {
        database.SongsDao().getTrackByGuid(guid)?.toDomain()
    }
    
    override suspend fun getTrackByMediaStoreId(mediaStoreId: Long): TrackDomain? = withContext(Dispatchers.IO) {
        database.SongsDao().getTrackWithMediaStoreId(mediaStoreId)?.toDomain()
    }
    
    override suspend fun getTracksByFolder(folderName: String): List<TrackDomain> = withContext(Dispatchers.IO) {
        database.SongsDao().getTracksFromFolder(folderName).map { it.toDomain() }
    }
    
    override suspend fun getTracksByArtist(artistId: Long): List<TrackDomain> = withContext(Dispatchers.IO) {
        // Artist removed - return empty list
        emptyList()
    }
    
    override suspend fun getTracksByAlbum(albumId: Long): List<TrackDomain> = withContext(Dispatchers.IO) {
        // Album removed - return empty list
        emptyList()
    }
    
    override suspend fun saveTracks(tracks: List<TrackDomain>) = withContext(Dispatchers.IO) {
        database.SongsDao().insertAll(tracks.map { it.toEntity() })
    }
    
    override suspend fun saveTrack(track: TrackDomain) = withContext(Dispatchers.IO) {
        database.SongsDao().insert(track.toEntity())
    }
    
    override suspend fun deleteTrack(guid: UUID) = withContext(Dispatchers.IO) {
        database.SongsDao().removeTrackByGuid(guid)
        // playlist_tracks removed by FK CASCADE
    }
    
    override suspend fun deleteTracks(guids: List<UUID>) = withContext(Dispatchers.IO) {
        guids.forEach { guid ->
            database.SongsDao().removeTrackByGuid(guid)
        }
    }
    
    override suspend fun updateTrackInfo(guid: UUID, path: String) = withContext(Dispatchers.IO) {
        val track = database.SongsDao().getTrackByGuid(guid)
        if (track != null) {
            database.SongsDao().updateSongInfo(path, track.path)
        }
    }
    
    override suspend fun getAllPlaylists(): List<PlaylistDomain> = withContext(Dispatchers.IO) {
        database.PlaylistsDao().getAll().map { it.toDomain() }
    }
    
    override suspend fun createPlaylist(title: String): PlaylistDomain = withContext(Dispatchers.IO) {
        val playlistId = database.PlaylistsDao().insert(Playlist(0, title))
        PlaylistDomain(playlistId.toInt(), title, 0)
    }
    
    override suspend fun deletePlaylist(playlistId: Int) = withContext(Dispatchers.IO) {
        val playlist = database.PlaylistsDao().getAll().find { it.id == playlistId }
        if (playlist != null) {
            database.PlaylistsDao().deletePlaylists(listOf(playlist))
        }
        database.PlaylistTracksDao().removeAllTracksFromPlaylist(playlistId)
    }
    
    override suspend fun getPlaylistWithTracks(playlistId: Int): PlaylistWithTracks? = withContext(Dispatchers.IO) {
        val playlist = database.PlaylistsDao().getAll().find { it.id == playlistId } ?: return@withContext null
        val tracks = database.PlaylistTracksDao().getTracksForPlaylist(playlistId).map { it.toDomain() }
        PlaylistWithTracks(
            playlist = playlist.toDomain(),
            tracks = tracks
        )
    }
    
    override suspend fun addTracksToPlaylist(playlistId: Int, trackGuids: List<UUID>) = withContext(Dispatchers.IO) {
        val existingTracks = database.PlaylistTracksDao().getPlaylistTracksOrdered(playlistId)
        val currentMaxPosition = existingTracks.maxOfOrNull { it.position } ?: -1
        val playlistTracks = trackGuids.mapIndexed { index, guid ->
            PlaylistTrack(playlistId = playlistId, trackGuid = guid, position = currentMaxPosition + index)
        }
        database.PlaylistTracksDao().insertAll(playlistTracks)
    }
    
    override suspend fun removeTracksFromPlaylist(playlistId: Int, trackGuids: List<UUID>) = withContext(Dispatchers.IO) {
        trackGuids.forEach { guid ->
            database.PlaylistTracksDao().removeTrackFromPlaylist(playlistId, guid)
        }
    }
    
    private fun Track.toDomain() = TrackDomain(
        guid = guid,
        mediaStoreId = mediaStoreId,
        path = path,
        duration = duration,
        folderName = folderName,
        year = year,
        playlistId = 0
    )
    
    private fun TrackDomain.toEntity() = Track(
        id = 0L,
        guid = guid,
        mediaStoreId = mediaStoreId,
        path = path,
        duration = duration,
        folderName = folderName,
        year = year,
        addedAtTimestampUnix = (System.currentTimeMillis() / 1000).toInt(),
        flags = 0,
        transcription = null,
        transcriptionNormalized = null,
        tagTxxxCreatedAtUnix = null,
        checksumAudio = null
    )
    
    private fun Playlist.toDomain() = PlaylistDomain(
        id = id,
        title = title,
        trackCount = 0
    )
}
