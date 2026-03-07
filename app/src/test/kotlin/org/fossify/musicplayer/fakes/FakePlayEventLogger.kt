package org.fossify.musicplayer.fakes

import org.fossify.musicplayer.application.ports.PlayEventLogger
import org.fossify.musicplayer.domain.model.TrackDomain
import java.util.UUID

class FakePlayEventLogger : PlayEventLogger {
    
    private val playEvents = mutableMapOf<UUID, MutableList<Long>>()
    
    override suspend fun logPlayEvent(track: TrackDomain) {
        val events = playEvents.getOrPut(track.guid) { mutableListOf() }
        events.add(System.currentTimeMillis())
    }
    
    override suspend fun getPlayCount(trackGuid: UUID): Int {
        return playEvents[trackGuid]?.size ?: 0
    }
    
    override suspend fun getLastPlayedTime(trackGuid: UUID): Long? {
        return playEvents[trackGuid]?.maxOrNull()
    }
    
    override suspend fun getMostPlayedTracks(limit: Int): List<Pair<TrackDomain, Int>> {
        return emptyList()
    }
    
    fun getLoggedTracks(): List<UUID> = playEvents.keys.toList()
    
    fun clear() {
        playEvents.clear()
    }
}
