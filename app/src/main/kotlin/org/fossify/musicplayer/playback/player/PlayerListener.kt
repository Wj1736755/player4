package org.fossify.musicplayer.playback.player

import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.fossify.commons.extensions.toast
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.currentMediaItems
import org.fossify.musicplayer.extensions.getPlaybackSetting
import org.fossify.musicplayer.helpers.PlayEventLogger
import org.fossify.musicplayer.helpers.PlaybackSetting
import org.fossify.musicplayer.playback.PlaybackService
import java.util.UUID

@UnstableApi
internal fun PlaybackService.getPlayerListener() = object : Player.Listener {
    
    private var lastLoggedMediaItemId: String? = null
    private var lastLoggedTimestamp: Long = 0
    private val MIN_LOG_INTERVAL_FOR_SAME_TRACK_MS = 1000L // Don't log the same track more than once per second
    
    override fun onPlayerError(error: PlaybackException) {
        android.util.Log.e("PlayerListener", "Player error: ${error.message}", error)
        
        // Check if this is a file not found error
        val causeMessage = error.cause?.message
        val errorMessage = error.message
        android.util.Log.e("PlayerListener", "DEBUG: error.message='$errorMessage', error.cause.message='$causeMessage'")
        
        val isFileNotFound = causeMessage?.contains("FileNotFoundException") == true ||
                             causeMessage?.contains("No item at content://") == true ||
                             errorMessage?.contains("Source error") == true
        
        android.util.Log.e("PlayerListener", "DEBUG: isFileNotFound=$isFileNotFound")
        
        if (isFileNotFound) {
            // Get current media ID - try from currentMediaItem first (can be guid or mediaStoreId)
            var currentMediaId: String? = null
            withPlayer { 
                currentMediaId = currentMediaItem?.mediaId
            }
            
            // If currentMediaItem is null, try to extract mediaStoreId from error message
            // Error format: "No item at content://media/external/audio/media/1000041306"
            if (currentMediaId == null) {
                val regex = Regex("content://media/external/audio/media/(\\d+)")
                val matchResult = regex.find(causeMessage ?: "")
                currentMediaId = matchResult?.groupValues?.get(1)
                android.util.Log.e("PlayerListener", "DEBUG: Extracted mediaId from error message: $currentMediaId")
            }
            
            android.util.Log.e("PlayerListener", "DEBUG: currentMediaId=$currentMediaId")
            
            currentMediaId?.let { mediaId ->
                android.util.Log.w("PlayerListener", "⚠️ File not found error for media ID: $mediaId")
                
                // Verify if file actually doesn't exist
                Thread {
                    try {
                        // Try to get track - first by guid (if mediaId is UUID), then by mediaStoreId (legacy numeric)
                        val track = if (mediaId.contains("-")) {
                            // mediaId is guid (UUID format with dashes)
                            val uuid = try { UUID.fromString(mediaId) } catch (e: Exception) { null }
                            uuid?.let { this@getPlayerListener.audioHelper.getTrackByGuid(it) }
                        } else {
                            // mediaId is mediaStoreId (legacy numeric)
                            val mediaStoreId = mediaId.toLongOrNull()
                            mediaStoreId?.let { this@getPlayerListener.audioHelper.getTrack(it) }
                        }
                        
                        if (track != null) {
                            android.util.Log.e("PlayerListener", "═══════════════════════════════════════════════════════")
                            android.util.Log.e("PlayerListener", "FILE NOT FOUND ERROR - Track Details:")
                            android.util.Log.e("PlayerListener", "  GUID: ${track.guid}")
                            android.util.Log.e("PlayerListener", "  Media Store ID: ${track.mediaStoreId}")
                            android.util.Log.e("PlayerListener", "  Title: ${track.title}")
                            android.util.Log.e("PlayerListener", "  Path: ${track.path}")
                            
                            // Check if file physically exists
                            val file = java.io.File(track.path)
                            val fileExists = file.exists()
                            val fileCanRead = if (fileExists) file.canRead() else false
                            val fileSize = if (fileExists) file.length() else 0
                            
                            android.util.Log.e("PlayerListener", "  File exists: $fileExists")
                            android.util.Log.e("PlayerListener", "  File readable: $fileCanRead")
                            android.util.Log.e("PlayerListener", "  File size: $fileSize bytes")
                            android.util.Log.e("PlayerListener", "═══════════════════════════════════════════════════════")
                            
                            if (!fileExists && track.guid != null) {
                                android.util.Log.e("PlayerListener", "✅ VERIFIED: File does NOT exist - deleting from database by GUID")
                                // TODO: Uncomment to enable deletion
                                // this@getPlayerListener.audioHelper.deleteTrackByGuid(track.guid!!)
                                // android.util.Log.i("PlayerListener", "Successfully removed track ${track.guid} from database")
                            } else if (!fileExists) {
                                android.util.Log.e("PlayerListener", "⚠️ File does NOT exist but track has no GUID - cannot delete safely")
                            } else {
                                android.util.Log.e("PlayerListener", "⚠️ File EXISTS but ExoPlayer can't read it - possible permission issue?")
                            }
                        } else {
                            android.util.Log.e("PlayerListener", "Media ID $mediaId not found in database!")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerListener", "Failed to verify track", e)
                    }
                }.start()
                
                // Skip to next track
                withPlayer {
                    seekToNextMediaItem()
                }
            } ?: run {
                this@getPlayerListener.toast(org.fossify.commons.R.string.unknown_error_occurred, Toast.LENGTH_LONG)
            }
        } else {
            this@getPlayerListener.toast(org.fossify.commons.R.string.unknown_error_occurred, Toast.LENGTH_LONG)
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (
            events.containsAny(
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TRACKS_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYLIST_METADATA_CHANGED
            )
        ) {
            updatePlaybackState()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        android.util.Log.d("PlayerListener", "onMediaItemTransition: newTrack=${mediaItem?.mediaId}, reason=$reason, trackToRemove=$trackToRemoveOnNext")
        
        // 1. Remove previous auto-added track from queue
        if (trackToRemoveOnNext != null) {
            val trackGuidToRemove = trackToRemoveOnNext!!  // Save value before it gets cleared
            
            withPlayer {
                android.util.Log.d("PlayerListener", "Looking for track to remove: $trackGuidToRemove in queue of ${currentMediaItems.size} items")
                
                val indexToRemove = currentMediaItems.indexOfFirst { 
                    it.mediaId == trackGuidToRemove.toString() 
                }
                
                android.util.Log.d("PlayerListener", "Index to remove: $indexToRemove")
                
                if (indexToRemove >= 0) {
                    removeMediaItem(indexToRemove)
                    android.util.Log.d("PlayerListener", "✅ Removed auto-added track: $trackGuidToRemove at index $indexToRemove")
                } else {
                    android.util.Log.w("PlayerListener", "⚠️ Track $trackGuidToRemove NOT FOUND in queue (already removed?)")
                }
            }
            
            // Remove from tracking set (in-memory only)
            autoAddedTrackGuids.remove(trackGuidToRemove)
            
            // Reset timestamp - track was removed, so interval can start fresh
            config.autoQueueLastAddedTimestamp = System.currentTimeMillis()
            android.util.Log.d("PlayerListener", "Reset auto-queue timestamp after track removal")
            
            trackToRemoveOnNext = null
        }
        
        // 2. Check if current track is auto-added, mark for removal next time
        val currentMediaId = mediaItem?.mediaId
        val currentGuid = currentMediaId?.let { try { UUID.fromString(it) } catch (e: Exception) { null } }
        android.util.Log.d("PlayerListener", "Checking if track $currentGuid is auto-added. autoAddedTrackGuids=${autoAddedTrackGuids.joinToString()}")
        if (currentGuid != null && currentGuid in autoAddedTrackGuids) {
            trackToRemoveOnNext = currentGuid
            android.util.Log.d("PlayerListener", "✅ Current track $currentGuid IS auto-added, will remove on next transition")
        } else {
            android.util.Log.d("PlayerListener", "❌ Current track $currentGuid is NOT in autoAddedTrackGuids")
        }
        
        // 3. Check if we should add new auto-queue tracks
        checkAutoQueue()
        
        // 4. customize repeat mode behaviour as the default behaviour doesn't align with our requirements.
        withPlayer {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                if (config.playbackSetting == PlaybackSetting.STOP_AFTER_CURRENT_TRACK) {
                    seekTo(0)
                    pause()
                }
            }
        }
        
        // 5. Log playback event when transitioning to a new media item (playback started)
        if (mediaItem != null) {
            logPlaybackIfNeeded(mediaItem)
        }
    }
    
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        // Log playback event when playback actually starts (including after resume from background)
        if (isPlaying) {
            withPlayer {
                val currentMediaItem = currentMediaItem
                if (currentMediaItem != null) {
                    logPlaybackIfNeeded(currentMediaItem)
                }
            }
        }
    }
    
    private fun logPlaybackIfNeeded(mediaItem: MediaItem) {
        val now = System.currentTimeMillis()
        val mediaItemId = mediaItem.mediaId
        
        // Log if:
        // 1. Different track than last logged (always log new tracks immediately), OR
        // 2. Same track but at least 1 second has passed (to avoid multiple logs for pause/resume of same track)
        val isDifferentTrack = mediaItemId != lastLoggedMediaItemId
        val enoughTimePassed = (now - lastLoggedTimestamp) >= MIN_LOG_INTERVAL_FOR_SAME_TRACK_MS
        
        if (isDifferentTrack || enoughTimePassed) {
            val playbackSpeed = config.playbackSpeed
            // Use applicationContext to ensure it works even when service is in background
            PlayEventLogger.logPlayEventFromMediaItem(
                this@getPlayerListener.applicationContext, 
                mediaItem, 
                playbackSpeed
            )
            lastLoggedMediaItemId = mediaItemId
            lastLoggedTimestamp = now
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        if (config.playbackSetting != PlaybackSetting.STOP_AFTER_CURRENT_TRACK) {
            config.playbackSetting = getPlaybackSetting(repeatMode)
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        config.isShuffleEnabled = shuffleModeEnabled
    }
}
