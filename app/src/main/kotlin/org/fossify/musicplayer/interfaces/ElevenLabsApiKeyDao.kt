package org.fossify.musicplayer.interfaces

import androidx.room.*
import org.fossify.musicplayer.models.ElevenLabsApiKey

@Dao
interface ElevenLabsApiKeyDao {

    @Query("SELECT * FROM elevenlabs_api_keys ORDER BY id DESC")
    fun getAll(): List<ElevenLabsApiKey>

    @Query("SELECT * FROM elevenlabs_api_keys WHERE is_active = 1 LIMIT 1")
    fun getActive(): ElevenLabsApiKey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(key: ElevenLabsApiKey): Long

    @Update
    fun update(key: ElevenLabsApiKey)

    @Delete
    fun delete(key: ElevenLabsApiKey)

    @Query("UPDATE elevenlabs_api_keys SET is_active = 0")
    fun deactivateAll()

    @Query("UPDATE elevenlabs_api_keys SET is_active = 1 WHERE id = :id")
    fun setActive(id: Long)
}
