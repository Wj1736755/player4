package org.fossify.musicplayer.application.ports

import org.fossify.musicplayer.domain.model.TrackDomain
import java.util.UUID

interface AudioPlayerPort {
    suspend fun play(track: TrackDomain)
    
    suspend fun playTracks(tracks: List<TrackDomain>, startIndex: Int = 0)
    
    suspend fun pause()
    
    suspend fun resume()
    
    suspend fun stop()
    
    suspend fun seekToNext()
    
    suspend fun seekToPrevious()
    
    suspend fun seekTo(positionMs: Long)
    
    fun getCurrentTrack(): TrackDomain?
    
    fun getCurrentTrackGuid(): UUID?
    
    fun isPlaying(): Boolean
    
    fun getCurrentPosition(): Long
    
    fun getDuration(): Long
}
