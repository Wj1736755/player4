package org.fossify.musicplayer.application.usecases

import org.fossify.musicplayer.application.ports.AudioRepository
import org.fossify.musicplayer.application.ports.FileStorage
import java.util.UUID

class DeleteTracksUseCase(
    private val trackRepository: AudioRepository,
    private val fileStorage: FileStorage
) {
    suspend fun execute(trackGuids: List<UUID>, deleteFiles: Boolean = false): Result<Unit> {
        if (trackGuids.isEmpty()) {
            return Result.failure(IllegalArgumentException("Track list is empty"))
        }
        
        return try {
            if (deleteFiles) {
                trackGuids.forEach { guid ->
                    val track = trackRepository.getTrackByGuid(guid)
                    track?.let { fileStorage.deleteFile(it.path) }
                }
            }
            
            trackRepository.deleteTracks(trackGuids)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to delete tracks: ${e.message}", e))
        }
    }
    
    suspend fun executeSingle(trackGuid: UUID, deleteFile: Boolean = false): Result<Unit> {
        return execute(listOf(trackGuid), deleteFile)
    }
}
