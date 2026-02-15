package org.fossify.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import org.fossify.musicplayer.models.PlayEvent

/**
 * DAO for PlayEvent - append-only event store for track playback statistics.
 */
@Dao
interface PlayEventDao {
    /**
     * Insert a new playback event (append-only).
     */
    @Insert
    fun insert(event: PlayEvent)

    /**
     * Insert multiple playback events in a batch (for efficiency).
     */
    @Insert
    fun insertAll(events: List<PlayEvent>)

    /**
     * Get all events for a specific track (by GUID).
     */
    @Query("SELECT * FROM play_events WHERE trackGuid = :trackGuid ORDER BY timestamp DESC")
    fun getEventsByTrack(trackGuid: String): List<PlayEvent>

    /**
     * Get all events, optionally limited.
     */
    @Query("SELECT * FROM play_events ORDER BY timestamp DESC LIMIT :limit")
    fun getAllEvents(limit: Int = Int.MAX_VALUE): List<PlayEvent>

    /**
     * Get events within a time range.
     */
    @Query("SELECT * FROM play_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getEventsBetween(startTime: Long, endTime: Long): List<PlayEvent>

    /**
     * Get count of playbacks for a specific track.
     */
    @Query("SELECT COUNT(*) FROM play_events WHERE trackGuid = :trackGuid")
    fun getPlayCount(trackGuid: String): Int

    /**
     * Get most played tracks (by play count).
     */
    @Query("SELECT trackGuid, COUNT(*) as playCount FROM play_events GROUP BY trackGuid ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayedTracks(limit: Int): List<PlayCountResult>

    /**
     * Get tracks played on a specific day (unique tracks, ordered by last played).
     * Returns tracks with their play count for that day.
     */
    @Query("""
        SELECT trackGuid, COUNT(*) as playCount, MAX(timestamp) as lastPlayed
        FROM play_events 
        WHERE timestamp >= :dayStartMillis AND timestamp < :dayEndMillis 
        GROUP BY trackGuid 
        ORDER BY lastPlayed DESC
    """)
    fun getTracksPlayedOnDay(dayStartMillis: Long, dayEndMillis: Long): List<DailyPlayResult>

    /**
     * Delete events older than specified timestamp (for cleanup/retention).
     */
    @Query("DELETE FROM play_events WHERE timestamp < :timestamp")
    fun deleteEventsOlderThan(timestamp: Long)

    /**
     * Get total count of all events.
     */
    @Query("SELECT COUNT(*) FROM play_events")
    fun getTotalEventCount(): Int
}

/**
 * Result class for play count queries.
 */
data class PlayCountResult(
    val trackGuid: String,
    val playCount: Int
)

/**
 * Result class for daily play queries.
 */
data class DailyPlayResult(
    val trackGuid: String,
    val playCount: Int,
    val lastPlayed: Long
)

