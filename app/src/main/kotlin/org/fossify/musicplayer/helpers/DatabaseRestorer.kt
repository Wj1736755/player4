package org.fossify.musicplayer.helpers

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.fossify.musicplayer.databases.SongsDatabase
import java.io.File
import kotlin.system.exitProcess

object DatabaseRestorer {
    
    /**
     * Validates if the selected file is a valid SQLite database
     * @return Pair<Boolean, String> - (isValid, errorMessage or version info)
     */
    fun validateDatabaseFile(context: Context, uri: Uri): Pair<Boolean, String> {
        var tempFile: File? = null
        var db: SQLiteDatabase? = null
        
        try {
            // Copy to temp file for validation
            tempFile = File(context.cacheDir, "temp_restore_validation.db")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            if (!tempFile.exists() || tempFile.length() == 0L) {
                return Pair(false, "File is empty or cannot be read")
            }
            
            // Try to open as SQLite database
            db = SQLiteDatabase.openDatabase(
                tempFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            
            // Check if it has tracks table (basic validation)
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='tracks'",
                null
            )
            val hasTracksTable = cursor.use { it.count > 0 }
            
            if (!hasTracksTable) {
                return Pair(false, "Not a valid Music Player database (missing tracks table)")
            }
            
            // Try to get version from user_version pragma
            val versionCursor = db.rawQuery("PRAGMA user_version", null)
            val dbVersion = versionCursor.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
            
            val currentVersion = SongsDatabase.DB_VERSION
            
            return if (dbVersion > currentVersion) {
                Pair(false, "Database version ($dbVersion) is newer than app version ($currentVersion). Please update the app.")
            } else if (dbVersion < currentVersion) {
                Pair(true, "Database version: $dbVersion (will be migrated to $currentVersion)")
            } else {
                Pair(true, "Database version: $dbVersion (matches current version)")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("DatabaseRestorer", "Validation failed", e)
            return Pair(false, "Invalid database file: ${e.message}")
        } finally {
            db?.close()
            tempFile?.delete()
        }
    }
    
    /**
     * Restores database from backup file
     * Creates a backup of current database before replacing
     * @return Pair<Boolean, String> - (success, message)
     */
    fun restoreDatabase(context: Context, backupUri: Uri): Pair<Boolean, String> {
        try {
            val dbPath = context.getDatabasePath("songs.db")
            
            // 1. Close current database connection
            SongsDatabase.getInstance(context).close()
            
            // 2. Create backup of current database
            val backupPath = File(dbPath.parent, "songs_backup_before_restore_${System.currentTimeMillis()}.db")
            if (dbPath.exists()) {
                try {
                    dbPath.copyTo(backupPath, overwrite = true)
                    android.util.Log.i("DatabaseRestorer", "Created backup at: ${backupPath.absolutePath}")
                } catch (e: Exception) {
                    android.util.Log.w("DatabaseRestorer", "Failed to create backup (proceeding anyway)", e)
                }
            }
            
            // 3. Copy new database file
            context.contentResolver.openInputStream(backupUri)?.use { input ->
                dbPath.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // 4. Validate the restored file
            try {
                val db = SQLiteDatabase.openDatabase(
                    dbPath.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
                db.close()
            } catch (e: Exception) {
                // Restore failed, revert to backup
                if (backupPath.exists()) {
                    backupPath.copyTo(dbPath, overwrite = true)
                    backupPath.delete()
                }
                return Pair(false, "Restored database is corrupted. Reverted to backup.")
            }
            
            android.util.Log.i("DatabaseRestorer", "Database restored successfully from: $backupUri")
            
            return Pair(true, "Database restored. App will restart now.")
            
        } catch (e: Exception) {
            android.util.Log.e("DatabaseRestorer", "Restore failed", e)
            return Pair(false, "Restore failed: ${e.message}")
        }
    }
    
    /**
     * Restarts the application
     */
    fun restartApp(context: Context) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            exitProcess(0)
        }, 500)
    }
}
