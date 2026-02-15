package org.fossify.musicplayer.helpers

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.models.Track
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.OutputStreamWriter

object DatabaseExporter {
    
    /**
     * Attempts to copy database from original application
     * This uses shell commands through run-as (debug builds only)
     */
    fun copyFromOriginalApp(context: Context, callback: (success: Boolean, path: String?) -> Unit) {
        ensureBackgroundThread {
            try {
                val packageName = context.packageName
                val originalPackageName = packageName.removeSuffix(".debug")
                
                // Get external storage directory
                val externalDir = context.getExternalFilesDir(null)
                val backupDir = File(externalDir, "backup")
                backupDir.mkdirs()
                
                val originalDbPath = "/data/data/$originalPackageName/databases/songs.db"
                val backupFile = File(backupDir, "songs_original.db")
                
                // Try to copy using shell command
                val process = Runtime.getRuntime().exec(
                    arrayOf(
                        "sh", "-c",
                        "run-as $packageName cp $originalDbPath ${backupFile.absolutePath} 2>&1"
                    )
                )
                
                val exitCode = process.waitFor()
                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                
                if (exitCode == 0 && backupFile.exists() && backupFile.length() > 0) {
                    // Also try to copy -wal and -shm files
                    try {
                        Runtime.getRuntime().exec(
                            arrayOf(
                                "sh", "-c",
                                "run-as $packageName cp ${originalDbPath}-wal ${backupFile.absolutePath}-wal 2>&1"
                            )
                        ).waitFor()
                    } catch (e: Exception) {
                        // Ignore if WAL file doesn't exist
                    }
                    
                    try {
                        Runtime.getRuntime().exec(
                            arrayOf(
                                "sh", "-c",
                                "run-as $packageName cp ${originalDbPath}-shm ${backupFile.absolutePath}-shm 2>&1"
                            )
                        ).waitFor()
                    } catch (e: Exception) {
                        // Ignore if SHM file doesn't exist
                    }
                    
                    callback(true, backupFile.absolutePath)
                } else {
                    // Fallback: try direct file access
                    tryDirectCopy(context, originalPackageName, backupFile, callback)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, null)
            }
        }
    }
    
    private fun tryDirectCopy(
        context: Context,
        originalPackageName: String,
        backupFile: File,
        callback: (success: Boolean, path: String?) -> Unit
    ) {
        try {
            // Try to access the database file directly
            val originalDbPath = File("/data/data/$originalPackageName/databases/songs.db")
            if (originalDbPath.canRead()) {
                FileInputStream(originalDbPath).use { input ->
                    FileOutputStream(backupFile).use { output ->
                        input.copyTo(output)
                    }
                }
                callback(true, backupFile.absolutePath)
            } else {
                callback(false, null)
            }
        } catch (e: Exception) {
            callback(false, null)
        }
    }
    
    fun exportDatabase(context: Context, callback: (success: Boolean, path: String?) -> Unit) {
        ensureBackgroundThread {
            try {
                // Get database file
                val dbFile = context.getDatabasePath("songs.db")
                
                if (!dbFile.exists()) {
                    callback(false, null)
                    return@ensureBackgroundThread
                }

                // Get external storage directory (SD card)
                val externalDir = context.getExternalFilesDir(null)
                val backupDir = File(externalDir, "backup")
                backupDir.mkdirs()

                // Create backup file
                val backupFile = File(backupDir, "songs_backup.db")
                
                // Copy database file
                FileInputStream(dbFile).use { input ->
                    FileOutputStream(backupFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Also copy the -wal and -shm files if they exist
                val walFile = File(dbFile.parent, "songs.db-wal")
                val shmFile = File(dbFile.parent, "songs.db-shm")
                
                if (walFile.exists()) {
                    FileInputStream(walFile).use { input ->
                        FileOutputStream(File(backupDir, "songs_backup.db-wal")).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                if (shmFile.exists()) {
                    FileInputStream(shmFile).use { input ->
                        FileOutputStream(File(backupDir, "songs_backup.db-shm")).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val backupPath = backupFile.absolutePath
                callback(true, backupPath)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, null)
            }
        }
    }
    
    fun getBackupPath(context: Context): String {
        val externalDir = context.getExternalFilesDir(null)
        val backupFile = File(externalDir, "backup/songs_backup.db")
        return backupFile.absolutePath
    }
    
    /**
     * Exports database to a selected file location using Storage Access Framework (SAF)
     * Note: Only the main database file is exported. WAL and SHM files are not included.
     */
    fun exportDatabaseToFile(
        context: Context,
        fileUri: Uri,
        callback: (success: Boolean) -> Unit
    ) {
        ensureBackgroundThread {
            try {
                // Get database file
                val dbFile = context.getDatabasePath("songs.db")
                
                if (!dbFile.exists()) {
                    callback(false)
                    return@ensureBackgroundThread
                }
                
                // Copy database file to selected location
                context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                    FileInputStream(dbFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: run {
                    callback(false)
                    return@ensureBackgroundThread
                }
                
                callback(true)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false)
            }
        }
    }
    
    /**
     * Exports all playlists to M3U files from the current app's database
     * Each playlist is exported to a separate .m3u file
     */
    fun exportAllPlaylists(context: Context, callback: (success: Boolean, exportedCount: Int, failedCount: Int, path: String?) -> Unit) {
        ensureBackgroundThread {
            try {
                val externalDir = context.getExternalFilesDir(null)
                val playlistsDir = File(externalDir, "playlists")
                playlistsDir.mkdirs()
                
                val audioHelper = context.audioHelper
                val playlists = audioHelper.getAllPlaylists()
                
                var exportedCount = 0
                var failedCount = 0
                
                for (playlist in playlists) {
                    try {
                        val tracks = audioHelper.getPlaylistTracks(playlist.id)
                        
                        if (tracks.isEmpty()) {
                            continue // Skip empty playlists
                        }
                        
                        // Create safe filename from playlist title
                        val safeTitle = playlist.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val m3uFile = File(playlistsDir, "$safeTitle.m3u")
                        
                        // Export playlist to M3U file
                        FileWriter(m3uFile).use { writer ->
                            writer.write("$M3U_HEADER\n")
                            
                            // Tracks are already sorted by position from junction table
                            for (track in tracks) {
                                val title = "${track.artist} - ${track.title}"
                                writer.write("$M3U_ENTRY${track.duration}$M3U_DURATION_SEPARATOR$title\n")
                                writer.write("${track.path}\n")
                            }
                        }
                        
                        exportedCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                        failedCount++
                    }
                }
                
                val success = exportedCount > 0
                callback(success, exportedCount, failedCount, playlistsDir.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, 0, 0, null)
            }
        }
    }
    
    /**
     * Exports all playlists from the original app's database
     * First copies the database, then reads playlists directly from SQLite
     */
    fun exportAllPlaylistsFromOriginalApp(context: Context, callback: (success: Boolean, exportedCount: Int, failedCount: Int, path: String?) -> Unit) {
        ensureBackgroundThread {
            // First, try to copy the database from original app
            copyFromOriginalApp(context) { copySuccess, dbPath ->
                if (!copySuccess || dbPath == null) {
                    callback(false, 0, 0, null)
                    return@copyFromOriginalApp
                }
                
                // Now read playlists from the copied database
                try {
                    val dbFile = File(dbPath)
                    if (!dbFile.exists() || dbFile.length() == 0L) {
                        callback(false, 0, 0, null)
                        return@copyFromOriginalApp
                    }
                    
                    val externalDir = context.getExternalFilesDir(null)
                    val playlistsDir = File(externalDir, "playlists")
                    playlistsDir.mkdirs()
                    
                    // Open the copied database
                    val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    
                    var exportedCount = 0
                    var failedCount = 0
                    
                    try {
                        // Read all playlists
                        val playlistsCursor = db.rawQuery("SELECT id, title FROM playlists ORDER BY id", null)
                        
                        if (playlistsCursor != null && playlistsCursor.count > 0) {
                            while (playlistsCursor.moveToNext()) {
                                try {
                                    val playlistId = playlistsCursor.getInt(playlistsCursor.getColumnIndexOrThrow("id"))
                                    val playlistTitle = playlistsCursor.getString(playlistsCursor.getColumnIndexOrThrow("title"))
                                    
                                    // Read tracks for this playlist from junction table
                                    val tracksCursor = db.rawQuery(
                                        """SELECT t.title, t.artist, t.path, t.duration, pt.position
                                           FROM tracks t
                                           INNER JOIN playlist_tracks pt ON t.guid = pt.track_guid
                                           WHERE pt.playlist_id = ?
                                           ORDER BY pt.position""",
                                        arrayOf(playlistId.toString())
                                    )
                                    
                                    if (tracksCursor != null && tracksCursor.count > 0) {
                                        // Create safe filename
                                        val safeTitle = playlistTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                        val m3uFile = File(playlistsDir, "$safeTitle.m3u")
                                        
                                        FileWriter(m3uFile).use { writer ->
                                            writer.write("$M3U_HEADER\n")
                                            
                                            while (tracksCursor.moveToNext()) {
                                                val trackTitle = tracksCursor.getString(tracksCursor.getColumnIndexOrThrow("title"))
                                                val trackArtist = tracksCursor.getString(tracksCursor.getColumnIndexOrThrow("artist"))
                                                val trackPath = tracksCursor.getString(tracksCursor.getColumnIndexOrThrow("path"))
                                                val duration = tracksCursor.getInt(tracksCursor.getColumnIndexOrThrow("duration"))
                                                
                                                val title = "$trackArtist - $trackTitle"
                                                writer.write("$M3U_ENTRY$duration$M3U_DURATION_SEPARATOR$title\n")
                                                writer.write("$trackPath\n")
                                            }
                                        }
                                        
                                        exportedCount++
                                    }
                                    
                                    tracksCursor?.close()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    failedCount++
                                }
                            }
                        }
                        
                        playlistsCursor?.close()
                    } finally {
                        db.close()
                    }
                    
                    val success = exportedCount > 0
                    callback(success, exportedCount, failedCount, playlistsDir.absolutePath)
                } catch (e: Exception) {
                    e.printStackTrace()
                    callback(false, 0, 0, null)
                }
            }
        }
    }
    
    /**
     * Exports all playlists to M3U files to a selected folder using Storage Access Framework (SAF)
     * Each playlist is exported to a separate .m3u file in the selected directory
     */
    fun exportAllPlaylistsToFolder(
        context: Context,
        folderUri: Uri,
        callback: (success: Boolean, exportedCount: Int, failedCount: Int) -> Unit
    ) {
        ensureBackgroundThread {
            try {
                val folder = DocumentFile.fromTreeUri(context, folderUri)
                if (folder == null || !folder.exists() || !folder.canWrite()) {
                    callback(false, 0, 0)
                    return@ensureBackgroundThread
                }
                
                val audioHelper = context.audioHelper
                val playlists = audioHelper.getAllPlaylists()
                
                var exportedCount = 0
                var failedCount = 0
                
                for (playlist in playlists) {
                    try {
                        val tracks = audioHelper.getPlaylistTracks(playlist.id)
                        
                        if (tracks.isEmpty()) {
                            continue // Skip empty playlists
                        }
                        
                        // Create safe filename from playlist title
                        val safeTitle = playlist.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val m3uFileName = "$safeTitle.m3u"
                        
                        // Check if file already exists and delete it if needed
                        val existingFile = folder.findFile(m3uFileName)
                        existingFile?.delete()
                        
                        // Create new file in the selected folder
                        val m3uFile = folder.createFile("audio/x-mpegurl", m3uFileName)
                        if (m3uFile == null) {
                            failedCount++
                            continue
                        }
                        
                        // Write playlist to file
                        context.contentResolver.openOutputStream(m3uFile.uri)?.use { outputStream ->
                            OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                                writer.write("$M3U_HEADER\n")
                                
                                // Tracks are already sorted by position from junction table
                                for (track in tracks) {
                                    val title = "${track.artist} - ${track.title}"
                                    writer.write("$M3U_ENTRY${track.duration}$M3U_DURATION_SEPARATOR$title\n")
                                    writer.write("${track.path}\n")
                                }
                            }
                        }
                        
                        exportedCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                        failedCount++
                    }
                }
                
                val success = exportedCount > 0
                callback(success, exportedCount, failedCount)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false, 0, 0)
            }
        }
    }
}

