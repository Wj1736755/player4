package org.fossify.musicplayer.helpers

import android.content.Context
import androidx.media3.common.MediaItem
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.extensions.getTracksDB
import org.fossify.musicplayer.extensions.toTrack
import org.fossify.musicplayer.models.PlayEvent
import org.fossify.musicplayer.models.Track

/**
 * Helper class for logging playback events to the event store.
 */
object PlayEventLogger {
    /**
     * Log a playback event for the given track.
     * The event is logged asynchronously to avoid blocking playback.
     * Uses track.guid (from ID3 TXXX tag) if available, falls back to track.path if not.
     */
    fun logPlayEvent(context: Context, track: Track?, playbackSpeed: Float? = null) {
        if (track == null) return
        
        // Use applicationContext to ensure it works even when activity/service is destroyed
        val appContext = context.applicationContext
        
        // Use track GUID (from ID3 TXXX tag) if available, fallback to path
        // GUID is more stable (survives file moves/renames)
        val trackGuid = track.guid?.toString() ?: track.path
        val timestamp = System.currentTimeMillis()
        
        ensureBackgroundThread {
            try {
                val db = appContext.getTracksDB()
                val playEventDao = db.PlayEventDao()
                playEventDao.insert(PlayEvent(trackGuid = trackGuid, timestamp = timestamp, playbackSpeed = playbackSpeed))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Log a playback event from MediaItem.
     * Extracts Track from MediaItem and logs the event with current playback speed.
     */
    fun logPlayEventFromMediaItem(context: Context, mediaItem: MediaItem?, playbackSpeed: Float? = null) {
        if (mediaItem == null) return
        
        val track = mediaItem.toTrack()
        logPlayEvent(context, track, playbackSpeed)
    }
}

