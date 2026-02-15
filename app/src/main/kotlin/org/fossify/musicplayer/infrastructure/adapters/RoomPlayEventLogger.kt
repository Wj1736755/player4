package org.fossify.musicplayer.infrastructure.adapters

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.musicplayer.application.ports.PlayEventLogger
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.domain.model.TrackDomain
import org.fossify.musicplayer.models.PlayEvent
import java.util.UUID

class RoomPlayEventLogger(
    private val database: SongsDatabase
) : PlayEventLogger {
    
    override suspend fun logPlayEvent(track: TrackDomain) = withContext(Dispatchers.IO) {
        val event = PlayEvent(
            id = 0,
            trackGuid = track.guid.toString(),
            timestamp = System.currentTimeMillis(),
            playbackSpeed = null
        )
        database.PlayEventDao().insert(event)
    }
    
    override suspend fun getPlayCount(trackGuid: UUID): Int = withContext(Dispatchers.IO) {
        database.PlayEventDao().getPlayCount(trackGuid.toString())
    }
    
    override suspend fun getLastPlayedTime(trackGuid: UUID): Long? = withContext(Dispatchers.IO) {
        val events = database.PlayEventDao().getEventsByTrack(trackGuid.toString())
        events.firstOrNull()?.timestamp
    }
    
    override suspend fun getMostPlayedTracks(limit: Int): List<Pair<TrackDomain, Int>> = withContext(Dispatchers.IO) {
        val topTracks = database.PlayEventDao().getMostPlayedTracks(limit)
        topTracks.mapNotNull { result ->
            val guid = try {
                UUID.fromString(result.trackGuid)
            } catch (e: IllegalArgumentException) {
                null
            }
            
            if (guid != null) {
                val track = database.SongsDao().getTrackByGuid(guid)
                if (track != null) {
                    TrackDomain(
                        guid = track.guid,
                        mediaStoreId = track.mediaStoreId,
                        path = track.path,
                        duration = track.duration,
                        folderName = track.folderName,
                        year = track.year,
                        playlistId = 0
                    ) to result.playCount
                } else {
                    null
                }
            } else {
                null
            }
        }
    }
}
