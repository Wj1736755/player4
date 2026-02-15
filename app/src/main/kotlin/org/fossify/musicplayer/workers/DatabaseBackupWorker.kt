package org.fossify.musicplayer.workers

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WorkManager worker for periodic database backups (every 4 hours)
 * Saves backups to: /sdcard/MusicPlayer/Backups/daily-backups/
 * Format: songs_v{version}_daily_{yyyyMMdd_HHmmss}.db
 * 
 * Cleanup strategy:
 * - Keeps all backups from TODAY
 * - Deletes ALL backups from previous days if at least one backup exists from TODAY
 */
class DatabaseBackupWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting periodic database backup (4h interval)")
            
            // Get database file
            val dbFile = applicationContext.getDatabasePath("songs.db")
            if (!dbFile.exists() || dbFile.length() == 0L) {
                Log.w(TAG, "Database file doesn't exist or is empty - skipping backup")
                return Result.success()
            }
            
            // Get database version from the file metadata or use a default
            val version = getDatabaseVersion()
            
            // Create backup
            val backupFile = createPeriodicBackup(dbFile, version)
            if (backupFile != null) {
                Log.i(TAG, "Periodic backup created: ${backupFile.name} (${backupFile.length()} bytes)")
                
                // Clean old backups (from previous days)
                cleanOldDailyBackups()
                
                Result.success()
            } else {
                Log.e(TAG, "Failed to create periodic backup")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during periodic backup", e)
            Result.failure()
        }
    }
    
    /**
     * Get database version (simplified - reads from Room database)
     */
    private fun getDatabaseVersion(): Int {
        return try {
            // Try to get version from database itself
            val db = applicationContext.getDatabasePath("songs.db")
            if (db.exists()) {
                // For now, return hardcoded version (Room will handle it)
                // In production, you could query: PRAGMA user_version
                37 // Current version
            } else {
                37
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting database version", e)
            37 // Default to current version
        }
    }
    
    /**
     * Create periodic backup in daily-backups subfolder
     */
    private fun createPeriodicBackup(dbFile: File, version: Int): File? {
        return try {
            val backupDir = getDailyBackupDirectory()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFileName = "songs_v${version}_daily_${timestamp}.db"
            val backupFile = File(backupDir, backupFileName)
            
            // Copy database file
            dbFile.copyTo(backupFile, overwrite = false)
            
            Log.i(TAG, "Backup created: ${backupFile.absolutePath}")
            backupFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create backup", e)
            null
        }
    }
    
    /**
     * Get daily backup directory
     * Location: /sdcard/MusicPlayer/Backups/daily-backups/
     */
    private fun getDailyBackupDirectory(): File {
        val backupDir = File(
            Environment.getExternalStorageDirectory(),
            "MusicPlayer/Backups/daily-backups"
        )
        if (!backupDir.exists()) {
            val created = backupDir.mkdirs()
            Log.d(TAG, "Daily backup directory created: $created at ${backupDir.absolutePath}")
        }
        return backupDir
    }
    
    /**
     * Clean old daily backups
     * Strategy: 
     * - If we have at least one backup from TODAY, delete ALL backups from previous days
     * - This keeps the folder clean while preserving all backups from the current day
     */
    private fun cleanOldDailyBackups() {
        try {
            val backupDir = getDailyBackupDirectory()
            val allBackups = backupDir.listFiles { file ->
                file.isFile && 
                file.name.startsWith("songs_v") && 
                file.name.contains("_daily_") && 
                file.name.endsWith(".db")
            } ?: return
            
            if (allBackups.isEmpty()) {
                Log.d(TAG, "No backups found for cleanup")
                return
            }
            
            // Get today's date string (yyyyMMdd)
            val todayDateString = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            
            // Separate backups into today's and old ones
            val todayBackups = mutableListOf<File>()
            val oldBackups = mutableListOf<File>()
            
            for (backup in allBackups) {
                // Extract date from filename: songs_v37_daily_20260209_220157.db
                // Date is at position after "daily_"
                val dateMatch = Regex("""_daily_(\d{8})_""").find(backup.name)
                if (dateMatch != null) {
                    val backupDateString = dateMatch.groupValues[1]
                    if (backupDateString == todayDateString) {
                        todayBackups.add(backup)
                    } else {
                        oldBackups.add(backup)
                    }
                }
            }
            
            Log.d(TAG, "Found ${todayBackups.size} backups from today, ${oldBackups.size} from previous days")
            
            // If we have at least one backup from today, delete all old backups
            if (todayBackups.isNotEmpty() && oldBackups.isNotEmpty()) {
                var deletedCount = 0
                for (oldBackup in oldBackups) {
                    if (oldBackup.delete()) {
                        deletedCount++
                        Log.d(TAG, "Deleted old backup: ${oldBackup.name}")
                    } else {
                        Log.w(TAG, "Failed to delete old backup: ${oldBackup.name}")
                    }
                }
                Log.i(TAG, "Cleaned $deletedCount old backups, kept ${todayBackups.size} from today")
            } else {
                Log.d(TAG, "Keeping all backups (${allBackups.size} total)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old daily backups", e)
        }
    }
    
    companion object {
        private const val TAG = "DatabaseBackupWorker"
    }
}
