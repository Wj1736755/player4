package org.fossify.musicplayer.helpers

import android.content.Context
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.extensions.getTracksDB
import org.fossify.musicplayer.interfaces.PlayCountResult
import org.fossify.musicplayer.interfaces.DailyPlayResult
import org.fossify.musicplayer.models.Track
import java.util.Calendar
import java.util.UUID

/**
 * Helper class for retrieving playback statistics from the event store.
 */
object StatisticsHelper {
    
    /**
     * Data class for track statistics.
     */
    data class TrackStatistics(
        val track: Track,
        val playCount: Int,
        val lastPlayedTimestamp: Long?,
        val averagePlaybackSpeed: Float? = null  // Average playback speed (null if no speed data available)
    )
    
    /**
     * Get most played tracks with their play counts.
     */
    fun getMostPlayedTracks(
        context: Context,
        limit: Int = 50,
        callback: (List<TrackStatistics>) -> Unit
    ) {
        ensureBackgroundThread {
            try {
                val db = context.getTracksDB()
                val playEventDao = db.PlayEventDao()
                val songsDao = db.SongsDao()
                
                // Get most played track GUIDs with counts
                val mostPlayed = playEventDao.getMostPlayedTracks(limit)
                
                // Get only the tracks we need (by their GUIDs) - much more efficient than loading all 8000+ tracks!
                val trackGuids = mostPlayed.mapNotNull { result ->
                    try { UUID.fromString(result.trackGuid) } catch (e: Exception) { null }
                }
                val tracksMap = songsDao.getTracksByGuids(trackGuids).associateBy { it.guid }
                
                // Get last played timestamps for each track
                val trackStats = mostPlayed.mapNotNull { playCountResult ->
                    val trackGuid = playCountResult.trackGuid
                    val trackUuid = try { UUID.fromString(trackGuid) } catch (e: Exception) { null }
                    
                    // Find track by GUID
                    val track = tracksMap[trackUuid] ?: return@mapNotNull null
                    
                    // Get last played timestamp and calculate average playback speed
                    val events = playEventDao.getEventsByTrack(trackGuid)
                    val lastPlayedTimestamp = events.firstOrNull()?.timestamp
                    
                    // Calculate average playback speed from events with speed data
                    val speedsWithData = events.mapNotNull { it.playbackSpeed }
                    val averagePlaybackSpeed = if (speedsWithData.isNotEmpty()) {
                        speedsWithData.average().toFloat()
                    } else {
                        null
                    }
                    
                    TrackStatistics(
                        track = track,
                        playCount = playCountResult.playCount,
                        lastPlayedTimestamp = lastPlayedTimestamp,
                        averagePlaybackSpeed = averagePlaybackSpeed
                    )
                }
                
                callback(trackStats)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(emptyList())
            }
        }
    }
    
    /**
     * Get total play count for a specific track.
     */
    /**
     * Get total play count for a specific track by path (stable identifier).
     */
    fun getPlayCountForTrack(
        context: Context,
        trackPath: String,
        callback: (Int) -> Unit
    ) {
        ensureBackgroundThread {
            try {
                val db = context.getTracksDB()
                val playEventDao = db.PlayEventDao()
                val count = playEventDao.getPlayCount(trackPath)
                callback(count)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(0)
            }
        }
    }
    
    /**
     * Get total number of playback events.
     */
    fun getTotalPlayCount(
        context: Context,
        callback: (Int) -> Unit
    ) {
        ensureBackgroundThread {
            try {
                val db = context.getTracksDB()
                val playEventDao = db.PlayEventDao()
                val count = playEventDao.getTotalEventCount()
                callback(count)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(0)
            }
        }
    }
    
    /**
     * Get tracks played on a specific day (unique tracks, ordered by last played).
     * @param dayTimestamp Any timestamp within the target day (milliseconds)
     */
    fun getTracksPlayedOnDay(
        context: Context,
        dayTimestamp: Long,
        callback: (List<TrackStatistics>) -> Unit
    ) {
        ensureBackgroundThread {
            try {
                val db = context.getTracksDB()
                val playEventDao = db.PlayEventDao()
                val songsDao = db.SongsDao()
                
                // Calculate day boundaries (start of day to end of day)
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = dayTimestamp
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dayStartMillis = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                val dayEndMillis = calendar.timeInMillis
                
                // Get tracks played on this day (ordered by last played DESC)
                val dailyPlays = playEventDao.getTracksPlayedOnDay(dayStartMillis, dayEndMillis)
                
                // Get only the tracks we need (by their GUIDs)
                val trackGuids = dailyPlays.mapNotNull { result ->
                    try { UUID.fromString(result.trackGuid) } catch (e: Exception) { null }
                }
                val tracksMap = songsDao.getTracksByGuids(trackGuids).associateBy { it.guid }
                
                // Build TrackStatistics list
                val trackStats = dailyPlays.mapNotNull { dailyResult ->
                    val trackUuid = try { UUID.fromString(dailyResult.trackGuid) } catch (e: Exception) { null }
                    val track = tracksMap[trackUuid] ?: return@mapNotNull null
                    
                    // Get average playback speed for this day
                    val events = playEventDao.getEventsBetween(dayStartMillis, dayEndMillis)
                        .filter { it.trackGuid == dailyResult.trackGuid }
                    val speedsWithData = events.mapNotNull { it.playbackSpeed }
                    val averagePlaybackSpeed = if (speedsWithData.isNotEmpty()) {
                        speedsWithData.average().toFloat()
                    } else {
                        null
                    }
                    
                    TrackStatistics(
                        track = track,
                        playCount = dailyResult.playCount,
                        lastPlayedTimestamp = dailyResult.lastPlayed,
                        averagePlaybackSpeed = averagePlaybackSpeed
                    )
                }
                
                callback(trackStats)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(emptyList())
            }
        }
    }
}

