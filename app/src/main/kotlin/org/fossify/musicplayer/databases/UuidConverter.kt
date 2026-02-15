package org.fossify.musicplayer.databases

import androidx.room.TypeConverter
import java.util.UUID

/**
 * Room TypeConverter for UUID <-> String mapping.
 * Allows storing UUID as String in database while using UUID type in code.
 */
class UuidConverter {
    @TypeConverter
    fun fromUuid(uuid: UUID?): String? {
        return uuid?.toString()
    }

    @TypeConverter
    fun toUuid(uuidString: String?): UUID? {
        return uuidString?.let { 
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
