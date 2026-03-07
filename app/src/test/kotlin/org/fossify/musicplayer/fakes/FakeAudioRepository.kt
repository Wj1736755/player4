package org.fossify.musicplayer.fakes

import org.fossify.musicplayer.application.ports.AudioRepository
import org.fossify.musicplayer.domain.model.PlaylistDomain
import org.fossify.musicplayer.domain.model.PlaylistWithTracks
import org.fossify.musicplayer.domain.model.TrackDomain
import java.util.UUID

class FakeAudioRepository : AudioRepository {
    
    private val tracks = mutableMapOf<UUID, TrackDomain>()
    private val playlists = mutableMapOf<Int, PlaylistDomain>()
    private val playlistTracks = mutableMapOf<Int, MutableList<UUID>>()
    private var nextPlaylistId = 1
    
    override suspend fun getAllTracks(): List<TrackDomain> {
        return tracks.values.toList()
    }
    
    override suspend fun getTrackByGuid(guid: UUID): TrackDomain? {
        return tracks[guid]
    }
    
    override suspend fun getTrackByMediaStoreId(mediaStoreId: Long): TrackDomain? {
        return tracks.values.firstOrNull { it.mediaStoreId == mediaStoreId }
    }
    
    override suspend fun getTracksByFolder(folderName: String): List<TrackDomain> {
        return tracks.values.filter { it.folderName == folderName }
    }
    
    override suspend fun getTracksByArtist(artistId: Long): List<TrackDomain> {
        // Artist removed - return empty list
        return emptyList()
    }
    
    override suspend fun getTracksByAlbum(albumId: Long): List<TrackDomain> {
        // Album removed - return empty list
        return emptyList()
    }
    
    override suspend fun saveTracks(tracks: List<TrackDomain>) {
        tracks.forEach { track ->
            this.tracks[track.guid] = track
        }
    }
    
    override suspend fun saveTrack(track: TrackDomain) {
        tracks[track.guid] = track
    }
    
    override suspend fun deleteTrack(guid: UUID) {
        tracks.remove(guid)
        playlistTracks.values.forEach { it.remove(guid) }
    }
    
    override suspend fun deleteTracks(guids: List<UUID>) {
        guids.forEach { deleteTrack(it) }
    }
    
    override suspend fun updateTrackInfo(guid: UUID, path: String) {
        tracks[guid]?.let { track ->
            tracks[guid] = track.copy(path = path)
        }
    }
    
    override suspend fun getAllPlaylists(): List<PlaylistDomain> {
        return playlists.values.toList()
    }
    
    override suspend fun getPlaylistWithTracks(playlistId: Int): PlaylistWithTracks? {
        val playlist = playlists[playlistId] ?: return null
        val trackGuids = playlistTracks[playlistId] ?: emptyList()
        val playlistTracksList = trackGuids.mapNotNull { tracks[it] }
        return PlaylistWithTracks(
            playlist.copy(trackCount = playlistTracksList.size),
            playlistTracksList
        )
    }
    
    override suspend fun createPlaylist(title: String): PlaylistDomain {
        val id = nextPlaylistId++
        val playlist = PlaylistDomain(id, title, 0)
        playlists[id] = playlist
        playlistTracks[id] = mutableListOf()
        return playlist
    }
    
    override suspend fun deletePlaylist(playlistId: Int) {
        playlists.remove(playlistId)
        playlistTracks.remove(playlistId)
    }
    
    override suspend fun addTracksToPlaylist(playlistId: Int, trackGuids: List<UUID>) {
        val trackList = playlistTracks.getOrPut(playlistId) { mutableListOf() }
        trackGuids.forEach { guid ->
            if (!trackList.contains(guid)) {
                trackList.add(guid)
            }
        }
    }
    
    override suspend fun removeTracksFromPlaylist(playlistId: Int, trackGuids: List<UUID>) {
        playlistTracks[playlistId]?.removeAll(trackGuids.toSet())
    }
    
    fun clear() {
        tracks.clear()
        playlists.clear()
        playlistTracks.clear()
        nextPlaylistId = 1
    }
}
