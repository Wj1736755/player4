package org.fossify.musicplayer.extensions

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import android.util.Size
import androidx.core.net.toUri
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.helpers.*
import org.fossify.musicplayer.interfaces.*
import org.fossify.musicplayer.models.Album
import org.fossify.musicplayer.models.Artist
import org.fossify.musicplayer.models.Genre
import org.fossify.musicplayer.models.Track
import java.io.File

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.playlistDAO: PlaylistsDao get() = getTracksDB().PlaylistsDao()

val Context.tracksDAO: SongsDao get() = getTracksDB().SongsDao()

val Context.queueDAO: QueueItemsDao get() = getTracksDB().QueueItemsDao()

val Context.artistDAO: ArtistsDao get() = getTracksDB().ArtistsDao()

val Context.albumsDAO: AlbumsDao get() = getTracksDB().AlbumsDao()

val Context.genresDAO: GenresDao get() = getTracksDB().GenresDao()

val Context.playlistTracksDAO: PlaylistTracksDao get() = getTracksDB().PlaylistTracksDao()

val Context.audioHelper: AudioHelper get() = AudioHelper(this)

val Context.mediaScanner: SimpleMediaScanner get() = SimpleMediaScanner.getInstance(applicationContext as Application)

fun Context.getTracksDB() = SongsDatabase.getInstance(this)

fun Context.getPlaylistIdWithTitle(title: String) = playlistDAO.getPlaylistWithTitle(title)?.id ?: -1

fun Context.broadcastUpdateWidgetState() {
    Intent(this, MyWidgetProvider::class.java).apply {
        action = TRACK_STATE_CHANGED
        sendBroadcast(this)
    }
}

fun Context.getMediaStoreIdFromPath(path: String): Long {
    var id = 0L
    val projection = arrayOf(
        Audio.Media._ID
    )

    val uri = getFileUri(path)
    val selection = "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs = arrayOf(path)

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                id = cursor.getLongValue(Audio.Media._ID)
            }
        }
    } catch (ignored: Exception) {
    }

    return id
}

fun Context.getFolderTracks(path: String, rescanWrongPaths: Boolean, callback: (tracks: ArrayList<Track>) -> Unit) {
    val folderTracks = getFolderTrackPaths(File(path))
    val allTracks = audioHelper.getAllTracks()
    val wantedTracks = ArrayList<Track>()
    val wrongPaths = ArrayList<String>()    // rescan paths that are not present in the MediaStore

    folderTracks.forEach { trackPath ->
        // Match by path (most direct identifier for file-based lookup)
        val existingTrack = allTracks.firstOrNull { it.path == trackPath }
        if (existingTrack != null) {
            existingTrack.id = 0
            wantedTracks.add(existingTrack)
        } else {
            // Track not in memory - try database
            val track = RoomHelper(this).getTrackFromPath(trackPath)
            if (track != null && track.mediaStoreId != 0L) {
                wantedTracks.add(track)
            } else {
                wrongPaths.add(trackPath)
            }
        }
    }

    if (wrongPaths.isEmpty() || !rescanWrongPaths) {
        callback(wantedTracks)
    } else {
        rescanPaths(wrongPaths) {
            getFolderTracks(path, false) { tracks ->
                callback(tracks)
            }
        }
    }
}

private fun getFolderTrackPaths(folder: File): ArrayList<String> {
    val trackFiles = ArrayList<String>()
    val files = folder.listFiles() ?: return trackFiles
    files.forEach {
        if (it.isDirectory) {
            trackFiles.addAll(getFolderTrackPaths(it))
        } else if (it.isAudioFast()) {
            trackFiles.add(it.absolutePath)
        }
    }
    return trackFiles
}

fun Context.getArtistCoverArt(artist: Artist, callback: (coverArt: Any?) -> Unit) {
    // Artist support removed - not needed for AI-generated audio
    Handler(Looper.getMainLooper()).post {
        callback(null)
    }
}

fun Context.getAlbumCoverArt(album: Album, callback: (coverArt: Any?) -> Unit) {
    // Album support removed - not needed for AI-generated audio
    Handler(Looper.getMainLooper()).post {
        callback(null)
    }
}

fun Context.getGenreCoverArt(genre: Genre, callback: (coverArt: Any?) -> Unit) {
    // Genre support removed - not needed for AI-generated audio
    Handler(Looper.getMainLooper()).post {
        callback(null)
    }
}

fun Context.getTrackCoverArt(track: Track?, callback: (coverArt: Any?) -> Unit) {
    ensureBackgroundThread {
        if (track == null) {
            Handler(Looper.getMainLooper()).post {
                callback(null)
            }
            return@ensureBackgroundThread
        }

        // Cover art removed - not needed for AI-generated audio
        Handler(Looper.getMainLooper()).post {
            callback("")
        }
    }
}

fun Context.loadTrackCoverArt(track: Track?): Bitmap? {
    // Cover art removed - not needed for AI-generated audio
    return null
}

fun Context.loadGlideResource(
    model: Any?,
    options: RequestOptions,
    size: Size,
    onLoadFailed: (e: Exception?) -> Unit,
    onResourceReady: (resource: Drawable) -> Unit,
) {
    ensureBackgroundThread {
        try {
            Glide.with(this)
                .load(model)
                .apply(options)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        onLoadFailed(e)
                        return true
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        onResourceReady(resource)
                        return false
                    }
                })
                .submit(size.width, size.height)
                .get()
        } catch (e: Exception) {
            onLoadFailed(e)
        }
    }
}

fun Context.getTrackFromUri(uri: Uri?, callback: (track: Track?) -> Unit) {
    if (uri == null) {
        callback(null)
        return
    }

    ensureBackgroundThread {
        val path = getRealPathFromURI(uri)
        if (path == null) {
            callback(null)
            return@ensureBackgroundThread
        }

        val allTracks = audioHelper.getAllTracks()
        val track = allTracks.find { it.path == path } ?: RoomHelper(this).getTrackFromPath(path) ?: return@ensureBackgroundThread
        callback(track)
    }
}

fun Context.isTabVisible(flag: Int) = config.showTabs and flag != 0

fun Context.getVisibleTabs() = tabsList.filter { isTabVisible(it) }

fun Context.getPlaybackSetting(repeatMode: @Player.RepeatMode Int): PlaybackSetting {
    return when (repeatMode) {
        Player.REPEAT_MODE_OFF -> PlaybackSetting.REPEAT_OFF
        Player.REPEAT_MODE_ONE -> PlaybackSetting.REPEAT_TRACK
        Player.REPEAT_MODE_ALL -> PlaybackSetting.REPEAT_PLAYLIST
        else -> config.playbackSetting
    }
}

fun Context.getFriendlyFolder(path: String): String {
    return when (val parentPath = path.getParentPath()) {
        internalStoragePath -> getString(org.fossify.commons.R.string.internal)
        sdCardPath -> getString(org.fossify.commons.R.string.sd_card)
        else -> parentPath.getFilenameFromPath()
    }
}
