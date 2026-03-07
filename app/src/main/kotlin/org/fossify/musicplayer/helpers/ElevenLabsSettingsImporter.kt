package org.fossify.musicplayer.helpers

import android.content.Context
import org.fossify.musicplayer.R
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.models.ElevenLabsApiKey
import java.io.File

/**
 * Imports ElevenLabs API keys from another app's database file (e.g. backup or another device).
 * Only the [elevenlabs_api_keys] table is read; all other data in the file is ignored.
 */
object ElevenLabsSettingsImporter {

    private const val TABLE_NAME = "elevenlabs_api_keys"
    private const val DEFAULT_VOICE_ID = "ErXwobaYiN019PkySvjV"

    /**
     * Imports ElevenLabs keys from the database file at [uri] into the current app's database.
     * @return Pair(success, message) – on success message is count imported; on failure an error message.
     */
    fun importFromUri(context: Context, uri: Uri): Pair<Boolean, String> {
        var tempFile: File? = null
        var db: SQLiteDatabase? = null
        try {
            tempFile = File(context.cacheDir, "temp_elevenlabs_import_${System.currentTimeMillis()}.db")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return Pair(false, "Could not read file")

            if (!tempFile.exists() || tempFile.length() == 0L) {
                return Pair(false, "File is empty or unreadable")
            }

            db = SQLiteDatabase.openDatabase(
                tempFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            val tableExists = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(TABLE_NAME)
            ).use { it.count > 0 }

            if (!tableExists) {
                return Pair(false, "Selected file has no ElevenLabs keys table")
            }

            val columns = getTableColumns(db)
            if (!columns.contains("email") || !columns.contains("api_key")) {
                return Pair(false, "Invalid ElevenLabs table structure")
            }

            val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME", null)
            val keysToImport = mutableListOf<ElevenLabsApiKey>()
            cursor.use { c ->
                val emailIdx = c.getColumnIndexOrThrow("email")
                val apiKeyIdx = c.getColumnIndexOrThrow("api_key")
                val voiceIdIdx = c.getColumnIndex("voice_id")
                val isActiveIdx = c.getColumnIndex("is_active")
                val createdAtIdx = c.getColumnIndex("created_at_utc")
                val lastUsedIdx = c.getColumnIndex("last_used_at_utc")
                val charLimitIdx = c.getColumnIndex("character_limit")
                val charCountIdx = c.getColumnIndex("character_count")
                val charRemainingIdx = c.getColumnIndex("character_limit_remaining")
                val nextResetIdx = c.getColumnIndex("next_character_count_reset_unix")

                while (c.moveToNext()) {
                    val email = c.getString(emailIdx)
                    val apiKey = c.getString(apiKeyIdx)
                    val voiceId = if (voiceIdIdx >= 0 && !c.isNull(voiceIdIdx)) c.getString(voiceIdIdx) else DEFAULT_VOICE_ID
                    val isActive = if (isActiveIdx >= 0) c.getInt(isActiveIdx) != 0 else false
                    val createdAt = if (createdAtIdx >= 0 && !c.isNull(createdAtIdx)) c.getLong(createdAtIdx) else System.currentTimeMillis()
                    val lastUsed = if (lastUsedIdx >= 0 && !c.isNull(lastUsedIdx)) c.getLong(lastUsedIdx) else null
                    val charLimit = if (charLimitIdx >= 0 && !c.isNull(charLimitIdx)) c.getInt(charLimitIdx) else null
                    val charCount = if (charCountIdx >= 0 && !c.isNull(charCountIdx)) c.getInt(charCountIdx) else null
                    val charRemaining = if (charRemainingIdx >= 0 && !c.isNull(charRemainingIdx)) c.getInt(charRemainingIdx) else null
                    val nextReset = if (nextResetIdx >= 0 && !c.isNull(nextResetIdx)) c.getLong(nextResetIdx) else null

                    keysToImport.add(
                        ElevenLabsApiKey(
                            id = 0,
                            email = email,
                            apiKey = apiKey,
                            voiceId = voiceId,
                            isActive = isActive,
                            createdAtUtc = createdAt,
                            lastUsedAtUtc = lastUsed,
                            characterLimit = charLimit,
                            characterCount = charCount,
                            characterLimitRemaining = charRemaining,
                            nextCharacterCountResetUnix = nextReset
                        )
                    )
                }
            }

            if (keysToImport.isEmpty()) {
                return Pair(true, context.getString(R.string.import_elevenlabs_no_keys))
            }

            val appDb = SongsDatabase.getInstance(context)
            val dao = appDb.ElevenLabsApiKeyDao()
            val existingEmails = dao.getAll().map { it.email }.toSet()

            appDb.runInTransaction {
                var firstActive = existingEmails.isEmpty()
                for (key in keysToImport) {
                    if (key.email in existingEmails) continue
                    val toInsert = key.copy(
                        id = 0,
                        isActive = firstActive
                    )
                    if (firstActive) {
                        dao.deactivateAll()
                        firstActive = false
                    }
                    dao.insert(toInsert)
                }
            }

            val importedCount = keysToImport.count { it.email !in existingEmails }
            return Pair(true, context.getString(R.string.import_elevenlabs_success, importedCount))
        } catch (e: Exception) {
            android.util.Log.e("ElevenLabsSettingsImporter", "Import failed", e)
            return Pair(false, context.getString(R.string.import_elevenlabs_error, e.message ?: e.toString()))
        } finally {
            db?.close()
            tempFile?.delete()
        }
    }

    private fun getTableColumns(db: SQLiteDatabase): List<String> {
        return db.rawQuery("PRAGMA table_info($TABLE_NAME)", null).use { cursor ->
            val idx = cursor.getColumnIndexOrThrow("name")
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(idx))
                }
            }
        }
    }
}
