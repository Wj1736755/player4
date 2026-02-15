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
 * - Keeps ALL backups from TODAY
 * - Keeps last 7 days of backups
 * - For each past date (not today), keeps only the LATEST backup from that day
 * - Deletes backups older than 7 days
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
     * - Keeps ALL backups from TODAY
     * - Keeps last 7 days of backups
     * - For each past date (not today), keeps only the LATEST backup from that day
     * - Deletes backups older than 7 days
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
            
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
            val today = Date()
            val todayDateString = dateFormat.format(today)
            
            // Calculate date 7 days ago
            val calendar = java.util.Calendar.getInstance()
            calendar.time = today
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -7)
            val sevenDaysAgo = calendar.time

            // Group backups by date
            val backupsByDate = mutableMapOf<String, MutableList<File>>()
            
            for (backup in allBackups) {
                // Extract date from filename: songs_v63_daily_20260308_143022.db
                val dateMatch = Regex("""_daily_(\d{8})_""").find(backup.name)
                if (dateMatch != null) {
                    val backupDateString = dateMatch.groupValues[1]
                    backupsByDate.getOrPut(backupDateString) { mutableListOf() }.add(backup)
                }
            }
            
            Log.d(TAG, "Found backups for ${backupsByDate.size} different dates")
            
            val backupsToDelete = mutableListOf<File>()
            var keptCount = 0

            for ((dateString, backupsForDate) in backupsByDate) {
                try {
                    val backupDate = dateFormat.parse(dateString)

                    if (backupDate == null) {
                        // Can't parse date, skip
                        continue
                    }

                    // Check if backup is older than 7 days
                    if (backupDate.before(sevenDaysAgo)) {
                        // Delete all backups older than 7 days
                        Log.d(TAG, "Date $dateString is older than 7 days, marking ${backupsForDate.size} backups for deletion")
                        backupsToDelete.addAll(backupsForDate)
                    } else if (dateString == todayDateString) {
                        // Keep ALL backups from today
                        Log.d(TAG, "Keeping all ${backupsForDate.size} backups from today ($dateString)")
                        keptCount += backupsForDate.size
                    } else {
                        // For past dates (within 7 days), keep only the LATEST backup
                        // Sort by timestamp (filename contains timestamp after date)
                        val sortedBackups = backupsForDate.sortedByDescending { it.name }
                        val latestBackup = sortedBackups.first()
                        val oldBackups = sortedBackups.drop(1)

                        Log.d(TAG, "Date $dateString: keeping latest backup (${latestBackup.name}), marking ${oldBackups.size} for deletion")
                        keptCount++
                        backupsToDelete.addAll(oldBackups)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing backups for date $dateString", e)
                }
            }

            // Delete marked backups
            var deletedCount = 0
            for (backup in backupsToDelete) {
                if (backup.delete()) {
                    deletedCount++
                    Log.d(TAG, "Deleted backup: ${backup.name}")
                } else {
                    Log.w(TAG, "Failed to delete backup: ${backup.name}")
                }
            }

            Log.i(TAG, "Cleanup complete: kept $keptCount backups, deleted $deletedCount backups")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old daily backups", e)
        }
    }
    
    companion object {
        private const val TAG = "DatabaseBackupWorker"
    }
}
