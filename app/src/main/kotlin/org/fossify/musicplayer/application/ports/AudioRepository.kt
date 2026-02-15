package org.fossify.musicplayer.application.ports

import org.fossify.musicplayer.domain.model.TrackDomain
import org.fossify.musicplayer.domain.model.PlaylistDomain
import org.fossify.musicplayer.domain.model.PlaylistWithTracks
import java.util.UUID

interface AudioRepository {
    suspend fun getAllTracks(): List<TrackDomain>
    
    suspend fun getTrackByGuid(guid: UUID): TrackDomain?
    
    suspend fun getTrackByMediaStoreId(mediaStoreId: Long): TrackDomain?
    
    suspend fun getTracksByFolder(folderName: String): List<TrackDomain>
    
    suspend fun getTracksByArtist(artistId: Long): List<TrackDomain>
    
    suspend fun getTracksByAlbum(albumId: Long): List<TrackDomain>
    
    suspend fun saveTracks(tracks: List<TrackDomain>)
    
    suspend fun saveTrack(track: TrackDomain)
    
    suspend fun deleteTrack(guid: UUID)
    
    suspend fun deleteTracks(guids: List<UUID>)
    
    suspend fun updateTrackInfo(guid: UUID, path: String)
    
    suspend fun getAllPlaylists(): List<PlaylistDomain>
    
    suspend fun getPlaylistWithTracks(playlistId: Int): PlaylistWithTracks?
    
    suspend fun createPlaylist(title: String): PlaylistDomain
    
    suspend fun deletePlaylist(playlistId: Int)
    
    suspend fun addTracksToPlaylist(playlistId: Int, trackGuids: List<UUID>)
    
    suspend fun removeTracksFromPlaylist(playlistId: Int, trackGuids: List<UUID>)
}
