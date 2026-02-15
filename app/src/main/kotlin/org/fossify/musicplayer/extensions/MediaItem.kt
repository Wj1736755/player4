package org.fossify.musicplayer.extensions

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.fossify.musicplayer.helpers.EXTRA_ALBUM
import org.fossify.musicplayer.helpers.EXTRA_ALBUM_ID
import org.fossify.musicplayer.helpers.EXTRA_ARTIST
import org.fossify.musicplayer.helpers.EXTRA_ARTIST_ID
import org.fossify.musicplayer.helpers.EXTRA_CHECKSUM_AUDIO
import org.fossify.musicplayer.helpers.EXTRA_COVER_ART
import org.fossify.musicplayer.helpers.EXTRA_CREATED_ON_TIMESTAMP
import org.fossify.musicplayer.helpers.EXTRA_DATE_ADDED
import org.fossify.musicplayer.helpers.EXTRA_DISC_NUMBER
import org.fossify.musicplayer.helpers.EXTRA_DURATION
import org.fossify.musicplayer.helpers.EXTRA_FLAGS
import org.fossify.musicplayer.helpers.EXTRA_FOLDER_NAME
import org.fossify.musicplayer.helpers.EXTRA_GENRE
import org.fossify.musicplayer.helpers.EXTRA_GENRE_ID
import org.fossify.musicplayer.helpers.EXTRA_GUID
import org.fossify.musicplayer.helpers.EXTRA_ID
import org.fossify.musicplayer.helpers.EXTRA_MEDIA_STORE_ID
import org.fossify.musicplayer.helpers.EXTRA_PATH
import org.fossify.musicplayer.helpers.EXTRA_TITLE
import org.fossify.musicplayer.helpers.EXTRA_TRACK_ID
import org.fossify.musicplayer.helpers.EXTRA_TRANSCRIPTION
import org.fossify.musicplayer.helpers.EXTRA_YEAR
import org.fossify.musicplayer.inlines.indexOfFirstOrNull
import org.fossify.musicplayer.models.*
import java.util.UUID

fun buildMediaItem(
    mediaId: String,
    title: String,
    album: String? = null,
    artist: String? = null,
    genre: String? = null,
    mediaType: @MediaMetadata.MediaType Int,
    trackCnt: Int? = null,
    trackNumber: Int? = null,
    discNumber: Int? = null,
    year: Int? = null,
    sourceUri: Uri? = null,
    artworkUri: Uri? = null,
    track: Track? = null
): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setAlbumTitle(album)
        .setArtist(artist)
        .setGenre(genre)
        .setIsBrowsable(mediaType != MediaMetadata.MEDIA_TYPE_MUSIC)
        .setIsPlayable(mediaType == MediaMetadata.MEDIA_TYPE_MUSIC)
        .setTotalTrackCount(trackCnt)
        .setTrackNumber(trackNumber)
        .setDiscNumber(discNumber)
        .setReleaseYear(year)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setArtworkUri(artworkUri)
        .apply {
            if (track != null) {
                setExtras(createBundleFromTrack(track))
            }
        }
        .build()

    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(sourceUri)
        .setMediaMetadata(metadata)
        .build()
}

fun Track.toMediaItem(): MediaItem {
    return buildMediaItem(
        mediaId = guid?.toString() ?: mediaStoreId.toString(),
        title = title,
        album = null,
        artist = null,
        genre = null,
        mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
        trackNumber = null,
        discNumber = null,
        sourceUri = getUri(),
        artworkUri = null,
        track = this
    )
}

fun Playlist.toMediaItem(): MediaItem {
    return buildMediaItem(
        mediaId = id.toString(),
        title = title,
        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
        trackCnt = trackCount
    )
}

fun Folder.toMediaItem(): MediaItem {
    return buildMediaItem(
        mediaId = title,
        title = title,
        mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
        trackCnt = trackCount
    )
}

fun Artist.toMediaItem(): MediaItem {
    return buildMediaItem(
        mediaId = id.toString(),
        title = title,
        mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
        trackCnt = trackCnt,
        artworkUri = albumArt.toUri()
    )
}

fun Album.toMediaItem(): MediaItem {
    return buildMediaItem(
        mediaId = id.toString(),
        title = title,
        artist = artist,
        mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
        trackCnt = trackCnt,
        artworkUri = coverArt.toUri(),
        year = year
    )
}

fun Genre.toMediaItem(): MediaItem {
    return buildMediaItem(
        title = title,
        mediaId = id.toString(),
        mediaType = MediaMetadata.MEDIA_TYPE_GENRE,
        trackCnt = trackCnt,
        artworkUri = albumArt.toUri()
    )
}

fun Collection<MediaItem>.toTracks() = mapNotNull { it.toTrack() }

fun Collection<MediaItem>.indexOfTrack(track: Track) = indexOfFirst { it.isSameMedia(track) }

fun Collection<MediaItem>.indexOfTrackOrNull(track: Track) = indexOfFirstOrNull { it.isSameMedia(track) }

fun MediaItem?.isSameMedia(track: Track): Boolean {
    if (this == null) return false

    val id = this.mediaId

    // Try to interpret mediaId as UUID and compare using UUID equality
    try {
        val parsed = UUID.fromString(id)
        return track.guid == parsed
    } catch (_: Exception) {
        // not a UUID - fall back to comparing mediaStoreId string
    }

    return id == track.mediaStoreId.toString()
}

fun MediaItem.toTrack(): Track? = mediaMetadata.extras?.let { createTrackFromBundle(it) }

private fun createBundleFromTrack(track: Track) = bundleOf(
    EXTRA_ID to track.id,
    EXTRA_MEDIA_STORE_ID to track.mediaStoreId,
    EXTRA_TITLE to track.title,
    EXTRA_PATH to track.path,
    EXTRA_DURATION to track.duration,
    EXTRA_FOLDER_NAME to track.folderName,
    EXTRA_YEAR to track.year,
    EXTRA_DATE_ADDED to track.addedAtTimestampUnix,
    EXTRA_FLAGS to track.flags,
    EXTRA_TRANSCRIPTION to track.transcription,
    EXTRA_GUID to track.guid.toString(),
    EXTRA_CREATED_ON_TIMESTAMP to (track.tagTxxxCreatedAtUnix ?: Long.MIN_VALUE),
    EXTRA_CHECKSUM_AUDIO to track.checksumAudio
)

private fun createTrackFromBundle(bundle: Bundle): Track {
    var discNumber: Int? = bundle.getInt(EXTRA_DISC_NUMBER)
    if (discNumber == Int.MIN_VALUE) {
        discNumber = null
    }

    var tagTxxxCreatedAtUnix: Long? = bundle.getLong(EXTRA_CREATED_ON_TIMESTAMP)
    if (tagTxxxCreatedAtUnix == Long.MIN_VALUE) {
        tagTxxxCreatedAtUnix = null
    }

    return Track(
        id = bundle.getLong(EXTRA_ID),
        mediaStoreId = bundle.getLong(EXTRA_MEDIA_STORE_ID),
        path = bundle.getString(EXTRA_PATH) ?: "",
        duration = bundle.getInt(EXTRA_DURATION),
        folderName = bundle.getString(EXTRA_FOLDER_NAME) ?: "",
        year = bundle.getInt(EXTRA_YEAR),
        addedAtTimestampUnix = bundle.getInt(EXTRA_DATE_ADDED),
        flags = bundle.getInt(EXTRA_FLAGS),
        transcription = bundle.getString(EXTRA_TRANSCRIPTION),
        transcriptionNormalized = bundle.getString(EXTRA_TRANSCRIPTION),
        guid = run {
            val raw = bundle.get(EXTRA_GUID)
            when (raw) {
                is String -> try {
                    UUID.fromString(raw)
                } catch (e: Exception) {
                    android.util.Log.w("MediaItem", "Invalid GUID string in bundle: $raw", e)
                    null
                }
                is UUID -> raw
                else -> {
                    android.util.Log.w("MediaItem", "Unexpected type for EXTRA_GUID in bundle: ${raw?.javaClass?.name}")
                    null
                }
            } ?: UUID.randomUUID()
        },
        tagTxxxCreatedAtUnix = tagTxxxCreatedAtUnix,
        checksumAudio = bundle.getString(EXTRA_CHECKSUM_AUDIO)
    )
}
