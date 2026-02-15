package org.fossify.musicplayer.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Event store entry for track playback events.
 * Append-only log: each event represents a single playback start.
 * 
 * @param trackGuid The GUID of the track (from ID3 TXXX tag) - most stable identifier, falls back to path if no GUID
 * @param timestamp The timestamp when playback started (in milliseconds)
 * @param playbackSpeed The playback speed when the track was played (e.g., 1.0 = normal, 1.5 = 1.5x speed)
 */
@Entity(tableName = "play_events")
data class PlayEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val trackGuid: String,  // Track.guid (ID3 TXXX tag), fallback to Track.path if no GUID
    val timestamp: Long,    // System.currentTimeMillis()
    val playbackSpeed: Float? = null  // Playback speed (nullable for backward compatibility)
)

