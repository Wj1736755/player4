package org.fossify.musicplayer.playback

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.os.postDelayed
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.showErrorToast
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.currentMediaItems
import org.fossify.musicplayer.extensions.getPlaylistIdWithTitle
import org.fossify.musicplayer.extensions.isReallyPlaying
import org.fossify.musicplayer.extensions.nextMediaItem
import org.fossify.musicplayer.extensions.runOnPlayerThread
import org.fossify.musicplayer.helpers.NotificationHelper
import org.fossify.musicplayer.helpers.getPermissionToRequest
import org.fossify.musicplayer.models.toMediaItems
import org.fossify.musicplayer.playback.library.MediaItemProvider
import org.fossify.musicplayer.playback.player.SimpleMusicPlayer
import org.fossify.musicplayer.playback.player.initializeSessionAndPlayer
import java.util.UUID

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService(), MediaSessionService.Listener {
    internal lateinit var player: SimpleMusicPlayer
    internal lateinit var playerListener: Player.Listener
    internal lateinit var mediaSession: MediaLibrarySession
    internal lateinit var mediaItemProvider: MediaItemProvider

    internal var currentRoot = ""

    // Auto-queue tracking (in-memory only, cleared on service restart)
    internal val autoAddedTrackGuids: MutableSet<UUID> = mutableSetOf()
    
    internal var trackToRemoveOnNext: UUID?
        get() = config.autoQueueTrackToRemoveNext
        set(value) {
            config.autoQueueTrackToRemoveNext = value
        }

    private val autoQueueReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "org.fossify.musicplayer.CLEAR_AUTO_QUEUE") {
                android.util.Log.d("PlaybackService", "Received CLEAR_AUTO_QUEUE broadcast")
                removeAutoAddedTracksFromQueue()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        setListener(this)
        initializeSessionAndPlayer(handleAudioFocus = true, handleAudioBecomingNoisy = true)
        initializeLibrary()
        
        // Register broadcast receiver for auto-queue control
        val filter = android.content.IntentFilter("org.fossify.musicplayer.CLEAR_AUTO_QUEUE")
        registerReceiver(autoQueueReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        android.util.Log.d("PlaybackService", "Registered auto-queue broadcast receiver")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(autoQueueReceiver)
            android.util.Log.d("PlaybackService", "Unregistered auto-queue broadcast receiver")
        } catch (e: Exception) {
            android.util.Log.w("PlaybackService", "Failed to unregister receiver", e)
        }
        clearAutoQueueTracking()
        releaseMediaSession()
        clearListener()
        stopSleepTimer()
        SimpleEqualizer.release()
    }

    fun stopService() {
        withPlayer {
            pause()
            stop()
        }

        stopSelf()
    }

    private fun initializeLibrary() {
        mediaItemProvider = MediaItemProvider(this)
        if (hasPermission(getPermissionToRequest())) {
            mediaItemProvider.reload()
        } else {
            showNoPermissionNotification()
        }
    }

    private fun releaseMediaSession() {
        mediaSession.release()
        withPlayer {
            removeListener(playerListener)
            release()
        }
    }

    internal fun withPlayer(callback: SimpleMusicPlayer.() -> Unit) {
        player.runOnPlayerThread { callback(this) }
    }

    private fun showNoPermissionNotification() {
        Handler(Looper.getMainLooper()).postDelayed(delayInMillis = 100L) {
            try {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    NotificationHelper.createInstance(this).createNoPermissionNotification()
                )
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * This method is only required to be implemented on Android 12 or above when an attempt is made
     * by a media controller to resume playback when the {@link MediaSessionService} is in the
     * background.
     */
    override fun onForegroundServiceStartNotAllowedException() {
        showErrorToast(getString(org.fossify.commons.R.string.unknown_error_occurred))
        // todo: show a notification instead.
    }

    internal fun clearAutoQueueTracking() {
        autoAddedTrackGuids.clear()
        config.autoQueueTrackToRemoveNext = null
        android.util.Log.d("PlaybackService", "Cleared auto-queue tracking")
    }
    
    private fun removeAutoAddedTracksFromQueue() {
        withPlayer {
            val tracksToRemove = autoAddedTrackGuids.toList()
            android.util.Log.d("PlaybackService", "Removing ${tracksToRemove.size} auto-added tracks from queue")
            
            // Remove from back to front to avoid index shifting issues
            val indicesToRemove = mutableListOf<Int>()
            currentMediaItems.forEachIndexed { index, mediaItem ->
                val trackGuid = try { UUID.fromString(mediaItem.mediaId) } catch (e: Exception) { null }
                if (trackGuid != null && trackGuid in tracksToRemove) {
                    indicesToRemove.add(index)
                }
            }
            
            android.util.Log.d("PlaybackService", "Found ${indicesToRemove.size} auto-added tracks in queue at indices: $indicesToRemove")
            
            // Remove from back to front
            indicesToRemove.sortedDescending().forEach { index ->
                removeMediaItem(index)
                android.util.Log.d("PlaybackService", "  Removed track at index $index")
            }
            
            android.util.Log.d("PlaybackService", "✅ Removed ${indicesToRemove.size} auto-added tracks from queue")
        }
        
        // Clear tracking after removal
        clearAutoQueueTracking()
    }

    internal fun checkAutoQueue() {
        android.util.Log.d("PlaybackService", "checkAutoQueue() called")
        
        if (!config.autoQueueEnabled) {
            android.util.Log.d("PlaybackService", "Auto-queue DISABLED, skipping")
            return
        }
        
        
        
        val now = System.currentTimeMillis()
        val interval = config.autoQueueIntervalMinutes * 60 * 1000L
        val lastAdded = config.autoQueueLastAddedTimestamp
        val elapsed = now - lastAdded
        val remaining = interval - elapsed
        
        android.util.Log.d("PlaybackService", "Auto-queue check: interval=${interval}ms (${config.autoQueueIntervalMinutes}min), elapsed=${elapsed}ms, remaining=${remaining}ms")
        
        if (now - lastAdded >= interval) {
            android.util.Log.d("PlaybackService", "Interval passed, adding auto-queue tracks")
            
            // Run on background thread to avoid "Cannot access database on main thread" error
            Thread {
                // Clean up stale track GUIDs (tracks that are no longer in player queue)
                withPlayer {
                    val currentQueueTrackGuids = currentMediaItems.mapNotNull { mediaItem ->
                        try { UUID.fromString(mediaItem.mediaId) } catch (e: Exception) { null }
                    }.toSet()
                    val staleGuids = autoAddedTrackGuids.filter { it !in currentQueueTrackGuids }
                    
                    if (staleGuids.isNotEmpty()) {
                        android.util.Log.d("PlaybackService", "Found ${staleGuids.size} stale track GUIDs no longer in queue: $staleGuids")
                        autoAddedTrackGuids.removeAll(staleGuids.toSet())
                        android.util.Log.d("PlaybackService", "Removed stale GUIDs, autoAddedTrackGuids now has ${autoAddedTrackGuids.size} entries")
                    }
                }
                addAutoQueueTracks()
            }.start()
        } else {
            android.util.Log.d("PlaybackService", "Interval NOT passed yet, skipping (${remaining}ms remaining)")
        }
    }

    private fun addAutoQueueTracks() {
        android.util.Log.d("PlaybackService", "addAutoQueueTracks() called")
        
        // Hardcoded playlist name "morn"
        val playlistId = getPlaylistIdWithTitle("morn")
        if (playlistId == -1) {
            android.util.Log.d("PlaybackService", "Playlist 'morn' not found, skipping auto-queue")
            return
        }
        
        android.util.Log.d("PlaybackService", "Found playlist 'morn' with ID: $playlistId")
        
        val playlistTracks = audioHelper.getPlaylistTracks(playlistId)
        if (playlistTracks.isEmpty()) {
            android.util.Log.d("PlaybackService", "Playlist 'morn' is empty, skipping auto-queue")
            return
        }
        
        android.util.Log.d("PlaybackService", "Playlist 'morn' has ${playlistTracks.size} tracks")
        
        withPlayer {
            // Check which tracks were already added by auto-queue (not just what's in queue)
            val alreadyAutoAdded = autoAddedTrackGuids
            val tracksToAdd = playlistTracks.filter { track ->
                track.guid?.let { it !in alreadyAutoAdded } ?: false
            }
            
            android.util.Log.d("PlaybackService", "Current queue size: ${currentMediaItems.size}, already auto-added: ${alreadyAutoAdded.size}, tracks to add: ${tracksToAdd.size}")
            
            if (tracksToAdd.isNotEmpty()) {
                // Mark as auto-added (in-memory only)
                tracksToAdd.forEach { track ->
                    track.guid?.let { guid ->
                        autoAddedTrackGuids.add(guid)
                        android.util.Log.d("PlaybackService", "  Adding track ${guid} to auto-queue")
                    }
                }
                
                // Add to player queue AFTER current track (not at the end)
                val insertPosition = currentMediaItemIndex + 1
                android.util.Log.d("PlaybackService", "Inserting ${tracksToAdd.size} tracks at position $insertPosition (after current track)")
                addMediaItems(insertPosition, tracksToAdd.toMediaItems())
                
                android.util.Log.d("PlaybackService", "✅ Auto-added ${tracksToAdd.size} tracks from playlist $playlistId at position $insertPosition (total auto-added: ${autoAddedTrackGuids.size})")
            } else {
                android.util.Log.d("PlaybackService", "All tracks from 'morn' were already auto-added, nothing new to add")
            }
        }
    }

    companion object {
        // Initializing a media controller might take a noticeable amount of time thus we expose current playback info here to keep things as quick as possible.
        var isPlaying: Boolean = false
            private set
        var currentMediaItem: MediaItem? = null
            private set
        var nextMediaItem: MediaItem? = null
            private set

        fun updatePlaybackInfo(player: Player) {
            currentMediaItem = player.currentMediaItem
            nextMediaItem = player.nextMediaItem
            isPlaying = player.isReallyPlaying
        }
    }
}

