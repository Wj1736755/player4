package org.fossify.musicplayer.application.usecases

import org.fossify.musicplayer.application.ports.AudioRepository
import org.fossify.musicplayer.domain.model.PlaylistDomain
import org.fossify.musicplayer.domain.model.PlaylistWithTracks
import java.util.UUID

class ManagePlaylistUseCase(
    private val trackRepository: AudioRepository
) {
    suspend fun getAllPlaylists(): Result<List<PlaylistDomain>> {
        return try {
            val playlists = trackRepository.getAllPlaylists()
            Result.success(playlists)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get playlists: ${e.message}", e))
        }
    }
    
    suspend fun getPlaylistWithTracks(playlistId: Int): Result<PlaylistWithTracks> {
        return try {
            val playlist = trackRepository.getPlaylistWithTracks(playlistId)
                ?: return Result.failure(IllegalArgumentException("Playlist not found: $playlistId"))
            Result.success(playlist)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get playlist: ${e.message}", e))
        }
    }
    
    suspend fun createPlaylist(title: String): Result<PlaylistDomain> {
        if (title.isBlank()) {
            return Result.failure(IllegalArgumentException("Playlist title cannot be empty"))
        }
        
        return try {
            val playlist = trackRepository.createPlaylist(title)
            Result.success(playlist)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to create playlist: ${e.message}", e))
        }
    }
    
    suspend fun deletePlaylist(playlistId: Int): Result<Unit> {
        return try {
            trackRepository.deletePlaylist(playlistId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to delete playlist: ${e.message}", e))
        }
    }
    
    suspend fun addTracksToPlaylist(playlistId: Int, trackGuids: List<UUID>): Result<Unit> {
        if (trackGuids.isEmpty()) {
            return Result.failure(IllegalArgumentException("Track list is empty"))
        }
        
        return try {
            trackRepository.addTracksToPlaylist(playlistId, trackGuids)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to add tracks to playlist: ${e.message}", e))
        }
    }
    
    suspend fun removeTracksFromPlaylist(playlistId: Int, trackGuids: List<UUID>): Result<Unit> {
        if (trackGuids.isEmpty()) {
            return Result.failure(IllegalArgumentException("Track list is empty"))
        }
        
        return try {
            trackRepository.removeTracksFromPlaylist(playlistId, trackGuids)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to remove tracks from playlist: ${e.message}", e))
        }
    }
}
