package org.fossify.musicplayer.application.usecases

import org.fossify.musicplayer.application.ports.AudioRepository
import org.fossify.musicplayer.domain.model.TrackDomain
import java.util.UUID

class GetTracksUseCase(
    private val trackRepository: AudioRepository
) {
    suspend fun getAllTracks(): Result<List<TrackDomain>> {
        return try {
            val tracks = trackRepository.getAllTracks()
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get tracks: ${e.message}", e))
        }
    }
    
    suspend fun getTrackByGuid(guid: UUID): Result<TrackDomain> {
        return try {
            val track = trackRepository.getTrackByGuid(guid)
                ?: return Result.failure(IllegalArgumentException("Track not found: $guid"))
            Result.success(track)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get track: ${e.message}", e))
        }
    }
    
    suspend fun getTracksByFolder(folderName: String): Result<List<TrackDomain>> {
        return try {
            val tracks = trackRepository.getTracksByFolder(folderName)
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get tracks by folder: ${e.message}", e))
        }
    }
    
    suspend fun getTracksByArtist(artistId: Long): Result<List<TrackDomain>> {
        return try {
            val tracks = trackRepository.getTracksByArtist(artistId)
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get tracks by artist: ${e.message}", e))
        }
    }
    
    suspend fun getTracksByAlbum(albumId: Long): Result<List<TrackDomain>> {
        return try {
            val tracks = trackRepository.getTracksByAlbum(albumId)
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get tracks by album: ${e.message}", e))
        }
    }
}
