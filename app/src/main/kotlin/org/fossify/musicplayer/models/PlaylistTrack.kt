package org.fossify.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlist_id", "track_guid"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Track::class,
            parentColumns = ["guid"],
            childColumns = ["track_guid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlist_id"]),
        Index(value = ["track_guid"])
    ]
)
data class PlaylistTrack(
    @ColumnInfo(name = "playlist_id") val playlistId: Int,
    @ColumnInfo(name = "track_guid") val trackGuid: UUID,
    @ColumnInfo(name = "position") val position: Int
)
