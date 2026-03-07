package org.fossify.musicplayer.fakes

import org.fossify.musicplayer.application.ports.AudioPlayerPort
import org.fossify.musicplayer.domain.model.TrackDomain
import java.util.UUID

class FakeAudioPlayer : AudioPlayerPort {
    
    private var currentTrack: TrackDomain? = null
    private var currentTracks: List<TrackDomain> = emptyList()
    private var playing = false
    private var currentPositionMs = 0L
    
    val playHistory = mutableListOf<TrackDomain>()
    
    override suspend fun play(track: TrackDomain) {
        currentTrack = track
        currentTracks = listOf(track)
        playing = true
        playHistory.add(track)
    }
    
    override suspend fun playTracks(tracks: List<TrackDomain>, startIndex: Int) {
        if (tracks.isNotEmpty() && startIndex in tracks.indices) {
            currentTracks = tracks
            currentTrack = tracks[startIndex]
            playing = true
            playHistory.add(tracks[startIndex])
        }
    }
    
    override suspend fun pause() {
        playing = false
    }
    
    override suspend fun resume() {
        if (currentTrack != null) {
            playing = true
        }
    }
    
    override suspend fun stop() {
        playing = false
        currentTrack = null
        currentTracks = emptyList()
        currentPositionMs = 0L
    }
    
    override suspend fun seekToNext() {
        val currentIndex = currentTracks.indexOf(currentTrack)
        if (currentIndex >= 0 && currentIndex < currentTracks.size - 1) {
            currentTrack = currentTracks[currentIndex + 1]
            playHistory.add(currentTrack!!)
        }
    }
    
    override suspend fun seekToPrevious() {
        val currentIndex = currentTracks.indexOf(currentTrack)
        if (currentIndex > 0) {
            currentTrack = currentTracks[currentIndex - 1]
            playHistory.add(currentTrack!!)
        }
    }
    
    override suspend fun seekTo(positionMs: Long) {
        currentPositionMs = positionMs
    }
    
    override fun getCurrentTrack(): TrackDomain? = currentTrack
    
    override fun getCurrentTrackGuid(): UUID? = currentTrack?.guid
    
    override fun isPlaying(): Boolean = playing
    
    override fun getCurrentPosition(): Long = currentPositionMs
    
    override fun getDuration(): Long = currentTrack?.duration?.toLong() ?: 0L
    
    fun clear() {
        currentTrack = null
        currentTracks = emptyList()
        playing = false
        currentPositionMs = 0L
        playHistory.clear()
    }
}
