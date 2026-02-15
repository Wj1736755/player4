package org.fossify.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.musicplayer.models.QueueItem

@Dao
interface QueueItemsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(queueItems: List<QueueItem>)

    @Query("SELECT * FROM queue_items ORDER BY track_order")
    fun getAll(): List<QueueItem>

    @Query("UPDATE queue_items SET is_current = 0")
    fun resetCurrent()

    @Query("SELECT * FROM queue_items WHERE is_current = 1")
    fun getCurrent(): QueueItem?

    @Query("UPDATE queue_items SET is_current = 1 WHERE track_id = :trackId")
    fun saveCurrentTrack(trackId: String)

    @Query("UPDATE queue_items SET is_current = 1, last_position = :lastPosition WHERE track_id = :trackId")
    fun saveCurrentTrackProgress(trackId: String, lastPosition: Int)

    @Query("UPDATE queue_items SET track_order = :order WHERE track_id = :trackId")
    fun setOrder(trackId: String, order: Int)

    @Query("DELETE FROM queue_items WHERE track_id = :trackId")
    fun removeQueueItem(trackId: String)

    @Query("DELETE FROM queue_items")
    fun deleteAllItems()
}
