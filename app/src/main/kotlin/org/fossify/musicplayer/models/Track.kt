package org.fossify.musicplayer.models

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.commons.helpers.AlphanumericComparator
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.musicplayer.extensions.sortSafely
import org.fossify.musicplayer.extensions.toMediaItem
import org.fossify.musicplayer.helpers.*
import java.io.File
import java.io.Serializable
import java.util.UUID

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["media_store_id"], unique = true),
        Index(value = ["guid"], unique = true),
        Index(value = ["checksum_audio"], unique = true),
        Index(value = ["transcription"]),
        Index(value = ["transcription_normalized"])
    ]
)
data class Track(
    @PrimaryKey(autoGenerate = true) var id: Long,
    @ColumnInfo(name = "media_store_id") var mediaStoreId: Long,
    @ColumnInfo(name = "path") var path: String,
    @ColumnInfo(name = "duration") var duration: Int,
    @ColumnInfo(name = "folder_name") var folderName: String,
    @ColumnInfo(name = "year") var year: Int,
    @ColumnInfo(name = "added_at_timestamp_unix") var addedAtTimestampUnix: Int,
    @ColumnInfo(name = "flags") var flags: Int = 0,
    @ColumnInfo(name = "transcription") var transcription: String? = null,
    @ColumnInfo(name = "transcription_normalized") var transcriptionNormalized: String? = null,
    @ColumnInfo(name = "guid") var guid: UUID,
    @ColumnInfo(name = "tag_txxx_created_at_unix") var tagTxxxCreatedAtUnix: Long? = null,
    @ColumnInfo(name = "checksum_audio") var checksumAudio: String? = null
) : Serializable, ListItem() {
    
    val title: String
        get() = path.substringAfterLast('/')

    companion object {
        private const val serialVersionUID = 6717978793256852245L

        fun getComparator(sorting: Int) = Comparator<Track> { first, second ->
            var result = when {
                sorting and PLAYER_SORT_BY_TITLE != 0 -> {
                    AlphanumericComparator().compare(first.title.lowercase(), second.title.lowercase())
                }

                sorting and PLAYER_SORT_BY_ARTIST_TITLE != 0 -> {
                    AlphanumericComparator().compare(first.title.lowercase(), second.title.lowercase())
                }

                sorting and PLAYER_SORT_BY_TRACK_ID != 0 -> {
                    AlphanumericComparator().compare(first.title.lowercase(), second.title.lowercase())
                }
                sorting and PLAYER_SORT_BY_DATE_ADDED != 0 -> first.addedAtTimestampUnix.compareTo(second.addedAtTimestampUnix)
                else -> first.duration.compareTo(second.duration)
            }

            if (sorting and SORT_DESCENDING != 0) {
                result *= -1
            }

            return@Comparator result
        }
    }

    fun getBubbleText(sorting: Int) = when {
        sorting and PLAYER_SORT_BY_TITLE != 0 -> title
        sorting and PLAYER_SORT_BY_ARTIST_TITLE != 0 -> title
        else -> duration.getFormattedDuration()
    }

    fun getProperTitle(showFilename: Int): String {
        return path.getFilenameFromPath()
    }

    fun getUri(): Uri = if (mediaStoreId == 0L || flags and FLAG_MANUAL_CACHE != 0) {
        Uri.fromFile(File(path))
    } else {
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
    }

    fun isCurrent() = flags and FLAG_IS_CURRENT != 0
}

fun ArrayList<Track>.sortSafely(sorting: Int) = sortSafely(Track.getComparator(sorting))

fun Collection<Track>.toMediaItems() = map { it.toMediaItem() }

fun Collection<Track>.toMediaItemsFast() = map {
    MediaItem.Builder()
        .setMediaId(it.guid?.toString() ?: it.mediaStoreId.toString())
        .build()
}
