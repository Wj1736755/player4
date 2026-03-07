package org.fossify.musicplayer.application.usecases

import org.fossify.musicplayer.application.ports.AudioPlayerPort
import org.fossify.musicplayer.application.ports.AudioRepository
import org.fossify.musicplayer.application.ports.PlayEventLogger
import java.util.UUID

class PlayTrackUseCase(
    private val audioPlayer: AudioPlayerPort,
    private val trackRepository: AudioRepository,
    private val playEventLogger: PlayEventLogger
) {
    suspend fun execute(trackGuid: UUID): Result<Unit> {
        val track = trackRepository.getTrackByGuid(trackGuid)
            ?: return Result.failure(IllegalArgumentException("Track not found: $trackGuid"))
        
        return try {
            audioPlayer.play(track)
            playEventLogger.logPlayEvent(track)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to play track: ${e.message}", e))
        }
    }
    
    suspend fun executeMultiple(trackGuids: List<UUID>, startIndex: Int = 0): Result<Unit> {
        if (trackGuids.isEmpty()) {
            return Result.failure(IllegalArgumentException("Track list is empty"))
        }
        
        if (startIndex < 0 || startIndex >= trackGuids.size) {
            return Result.failure(IllegalArgumentException("Invalid start index: $startIndex"))
        }
        
        val tracks = trackGuids.mapNotNull { guid ->
            trackRepository.getTrackByGuid(guid)
        }
        
        if (tracks.isEmpty()) {
            return Result.failure(IllegalArgumentException("No valid tracks found"))
        }
        
        return try {
            audioPlayer.playTracks(tracks, startIndex)
            tracks[startIndex].let { playEventLogger.logPlayEvent(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to play tracks: ${e.message}", e))
        }
    }
}
