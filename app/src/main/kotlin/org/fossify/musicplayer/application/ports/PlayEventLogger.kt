package org.fossify.musicplayer.application.ports

import org.fossify.musicplayer.domain.model.TrackDomain
import java.util.UUID

interface PlayEventLogger {
    suspend fun logPlayEvent(track: TrackDomain)
    
    suspend fun getPlayCount(trackGuid: UUID): Int
    
    suspend fun getLastPlayedTime(trackGuid: UUID): Long?
    
    suspend fun getMostPlayedTracks(limit: Int = 20): List<Pair<TrackDomain, Int>>
}
