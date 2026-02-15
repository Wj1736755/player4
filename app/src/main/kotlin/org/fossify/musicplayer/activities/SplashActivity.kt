package org.fossify.musicplayer.activities

import android.content.Intent
import android.os.Environment
import android.util.Log
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.commons.extensions.baseConfig
import org.fossify.musicplayer.BuildConfig
import org.fossify.musicplayer.extensions.config
import java.io.File

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        checkAndBackupDatabase()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun checkAndBackupDatabase() {
        try {
            val currentVersionName = BuildConfig.VERSION_NAME
            val currentBuildNumber = BuildConfig.VERSION_CODE
            val lastVersionName = config.lastInstalledVersionName
            val lastBuildNumber = config.lastInstalledBuildNumber
            val currentFullVersion = "$currentVersionName.$currentBuildNumber"

            // Only backup if we have both old version components and version changed
            if (currentBuildNumber > lastBuildNumber && lastBuildNumber > 0 && lastVersionName.isNotEmpty()) {
                val lastFullVersion = "$lastVersionName.$lastBuildNumber"
                Log.i("SplashActivity", "App updated from $lastFullVersion to $currentFullVersion - creating database backup")
                backupDatabase(lastFullVersion)
            }

            // Save current version
            config.lastInstalledVersionName = currentVersionName
            config.lastInstalledBuildNumber = currentBuildNumber
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error during database backup check", e)
        }
    }

    private fun getBackupDirectory(): File {
        val backupDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MusicPlayer/Backups")
        if (!backupDir.exists()) {
            val created = backupDir.mkdirs()
            Log.i("SplashActivity", "Backup directory created: $created, path: ${backupDir.absolutePath}")
        }
        return backupDir
    }

    private fun backupDatabase(oldFullVersion: String) {
        try {
            val dbPath = getDatabasePath("songs.db")
            if (!dbPath.exists()) {
                Log.w("SplashActivity", "Database does not exist yet, skipping backup")
                return
            }

            val backupDir = getBackupDirectory()
            if (!backupDir.exists()) {
                Log.e("SplashActivity", "Failed to create backup directory: ${backupDir.absolutePath}")
                return
            }

            val backupPath = File(backupDir, "songs.db.$oldFullVersion")
            dbPath.copyTo(backupPath, overwrite = true)
            Log.i("SplashActivity", "Database backup created: ${backupPath.absolutePath}")

            // Keep only last 5 backups to save space
            cleanupOldBackups(backupDir)
        } catch (e: Exception) {
            Log.e("SplashActivity", "Failed to backup database", e)
        }
    }

    private fun cleanupOldBackups(backupDir: File) {
        try {
            backupDir.listFiles { file ->
                file.name.startsWith("songs.db.") && file.name.matches(Regex("songs\\.db\\.\\d{4}\\.\\d{2}\\.\\d{2}\\.\\d{6}"))
            }?.sortedByDescending { it.name }
                ?.drop(5) // Keep last 5 backups
                ?.forEach { backup ->
                    if (backup.delete()) {
                        Log.i("SplashActivity", "Deleted old backup: ${backup.name}")
                    }
                }
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error cleaning up old backups", e)
        }
    }
}
