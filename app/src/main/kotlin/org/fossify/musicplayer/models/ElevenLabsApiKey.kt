package org.fossify.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "elevenlabs_api_keys")
data class ElevenLabsApiKey(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "api_key") val apiKey: String,
    @ColumnInfo(name = "voice_id") val voiceId: String,
    @ColumnInfo(name = "is_active") var isActive: Boolean = false,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_used_at_utc") var lastUsedAtUtc: Long? = null,
    @ColumnInfo(name = "character_limit") var characterLimit: Int? = null,
    @ColumnInfo(name = "character_count") var characterCount: Int? = null,
    @ColumnInfo(name = "character_limit_remaining") var characterLimitRemaining: Int? = null,
    @ColumnInfo(name = "next_character_count_reset_unix") var nextCharacterCountResetUnix: Long? = null
)
