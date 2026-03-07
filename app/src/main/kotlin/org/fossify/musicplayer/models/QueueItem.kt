package org.fossify.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "queue_items", primaryKeys = ["track_id"])
data class QueueItem(
    @ColumnInfo(name = "track_id") var trackId: String,  // UUID string
    @ColumnInfo(name = "track_order") var trackOrder: Int,
    @ColumnInfo(name = "is_current") var isCurrent: Boolean,
    @ColumnInfo(name = "last_position") var lastPosition: Int
) {
    companion object {
        fun from(guid: String, position: Int = 0): QueueItem {
            return QueueItem(guid, 0, true, position)
        }
    }
}
