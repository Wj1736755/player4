package org.fossify.musicplayer.helpers

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logger for dry-run scanner output.
 * Creates a detailed log file of what would be changed during a media scan.
 */
class ScanLogger(private val context: Context) {
    private val logLines = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    init {
        logHeader()
    }

    private fun logHeader() {
        logLines.add("=".repeat(80))
        logLines.add("MEDIA SCAN DRY RUN LOG")
        logLines.add("Started: ${dateFormat.format(Date())}")
        logLines.add("=".repeat(80))
        logLines.add("")
    }

    fun logNewTrack(path: String, reason: String) {
        logLines.add("[NEW] $path")
        logLines.add("      Reason: $reason")
        logLines.add("")
    }

    fun logUpdatedTrack(path: String, changes: List<String>) {
        logLines.add("[UPDATE] $path")
        changes.forEach { change ->
            logLines.add("         - $change")
        }
        logLines.add("")
    }

    fun logSkippedTrack(path: String, reason: String) {
        logLines.add("[SKIP] $path")
        logLines.add("       Reason: $reason")
        logLines.add("")
    }

    fun logDeletedTrack(path: String, reason: String) {
        logLines.add("[DELETE] $path")
        logLines.add("         Reason: $reason")
        logLines.add("")
    }

    fun logCase(caseNumber: Int, description: String, path: String) {
        logLines.add("[CASE $caseNumber] $description")
        logLines.add("            File: $path")
        logLines.add("")
    }

    fun logTagOperation(path: String, operation: String, details: String) {
        logLines.add("[TAG] $operation")
        logLines.add("      File: $path")
        logLines.add("      Details: $details")
        logLines.add("")
    }

    fun logSummary(newCount: Int, updateCount: Int, deleteCount: Int, skipCount: Int) {
        logLines.add("")
        logLines.add("=".repeat(80))
        logLines.add("SUMMARY")
        logLines.add("=".repeat(80))
        logLines.add("New tracks:     $newCount")
        logLines.add("Updated tracks: $updateCount")
        logLines.add("Deleted tracks: $deleteCount")
        logLines.add("Skipped tracks: $skipCount")
        logLines.add("Completed: ${dateFormat.format(Date())}")
        logLines.add("=".repeat(80))
    }

    /**
     * Save log to file in Music Player directory.
     * @return File path where log was saved, or null on error
     */
    fun saveToFile(): String? {
        return try {
            val musicPlayerDir = File(Environment.getExternalStorageDirectory(), "MusicPlayer")
            if (!musicPlayerDir.exists()) {
                musicPlayerDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val logFile = File(musicPlayerDir, "scan_dryrun_$timestamp.txt")

            logFile.writeText(logLines.joinToString("\n"))

            logFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
