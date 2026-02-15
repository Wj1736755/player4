package org.fossify.musicplayer.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.extensions.audioHelper

/**
 * Background audio player for playing ambient/background tracks alongside main player.
 *
 * Features:
 * - Independent ExoPlayer instance
 * - Loads tracks from specified playlist
 * - Adjustable volume (0-100%)
 * - Optional loop mode
 * - Silent failure if playlist not found or empty
 */
class BackgroundAudioPlayer(private val context: Context) {

    private var player: ExoPlayer? = null
    private var isInitialized = false
    private var currentPlaylistName: String? = null

    /**
     * Initialize the background player with playlist and settings.
     * If playlist not found or empty, silently does nothing.
     *
     * @param playlistName Name of playlist to play
     * @param volume Volume level 0-100 (percentage)
     * @param loop Whether to loop the playlist
     */
    fun initialize(playlistName: String, volume: Int, loop: Boolean) {
        ensureBackgroundThread {
            try {
                Log.d(TAG, "Initializing background player: playlist='$playlistName', volume=$volume%, loop=$loop")

                // Find playlist by name (case-insensitive)
                val playlists = context.audioHelper.getAllPlaylists()
                val playlist = playlists.find { it.title.equals(playlistName, ignoreCase = true) }

                if (playlist == null) {
                    Log.d(TAG, "Playlist '$playlistName' not found - background audio disabled")
                    release()
                    return@ensureBackgroundThread
                }

                // Get tracks from playlist
                val tracks = context.audioHelper.getPlaylistTracks(playlist.id)
                if (tracks.isEmpty()) {
                    Log.d(TAG, "Playlist '$playlistName' is empty - background audio disabled")
                    release()
                    return@ensureBackgroundThread
                }

                Log.i(TAG, "Found playlist '${playlist.title}' with ${tracks.size} tracks")

                // Create or reuse player
                if (player == null) {
                    player = ExoPlayer.Builder(context).build()
                }

                // Configure player
                player?.apply {
                    // Set repeat mode
                    repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF

                    // Set volume (convert 0-100 to 0.0-1.0)
                    setVolume(volume / 100f)

                    // Clear and add tracks
                    clearMediaItems()
                    val mediaItems = tracks.map { track ->
                        MediaItem.Builder()
                            .setMediaId(track.guid.toString())
                            .setUri(track.path)
                            .build()
                    }
                    setMediaItems(mediaItems)

                    // Prepare player
                    prepare()
                }

                currentPlaylistName = playlistName
                isInitialized = true
                Log.i(TAG, "Background player initialized successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing background player", e)
                release()
            }
        }
    }

    /**
     * Start playing background audio.
     * Must call initialize() first.
     */
    fun start() {
        if (!isInitialized) {
            Log.w(TAG, "Cannot start: player not initialized")
            return
        }

        try {
            player?.play()
            Log.d(TAG, "Background player started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting background player", e)
        }
    }

    /**
     * Pause background audio.
     */
    fun pause() {
        try {
            player?.pause()
            Log.d(TAG, "Background player paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing background player", e)
        }
    }

    /**
     * Resume background audio playback.
     */
    fun resume() {
        if (!isInitialized) {
            Log.w(TAG, "Cannot resume: player not initialized")
            return
        }

        try {
            player?.play()
            Log.d(TAG, "Background player resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming background player", e)
        }
    }

    /**
     * Stop background audio and reset position.
     */
    fun stop() {
        try {
            player?.apply {
                pause()
                seekTo(0)
            }
            Log.d(TAG, "Background player stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping background player", e)
        }
    }

    /**
     * Release player resources.
     */
    fun release() {
        try {
            player?.release()
            player = null
            isInitialized = false
            currentPlaylistName = null
            Log.d(TAG, "Background player released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing background player", e)
        }
    }

    /**
     * Set volume level.
     * @param volume Volume 0-100 (percentage)
     */
    fun setVolume(volume: Int) {
        try {
            val volumeFloat = (volume.coerceIn(0, 100)) / 100f
            player?.setVolume(volumeFloat)
            Log.d(TAG, "Background volume set to $volume% ($volumeFloat)")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting background volume", e)
        }
    }

    /**
     * Set loop mode.
     * @param loop True to loop playlist, false to play once
     */
    fun setLoop(loop: Boolean) {
        try {
            player?.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            Log.d(TAG, "Background loop set to $loop")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting background loop", e)
        }
    }

    /**
     * Check if background player is currently playing.
     */
    fun isPlaying(): Boolean {
        return player?.isPlaying == true
    }

    /**
     * Check if player is initialized and ready.
     */
    fun isReady(): Boolean {
        return isInitialized && player != null
    }

    companion object {
        private const val TAG = "BackgroundAudioPlayer"
    }
}
