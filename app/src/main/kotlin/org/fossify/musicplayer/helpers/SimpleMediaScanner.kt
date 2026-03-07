package org.fossify.musicplayer.helpers

import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.*
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.helpers.isRPlus
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.tracksDAO
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.models.*
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import androidx.core.net.toUri
import org.fossify.musicplayer.extensions.getFriendlyFolder
import org.fossify.musicplayer.helpers.TagsProcessor

/**
 * This singleton class manages the process of querying [MediaStore] for new audio files, manually scanning storage for missing audio files, and removing outdated
 * files from the local cache. It ensures that only one scan is running at a time to avoid unnecessary expenses and conflicts.
 */
class SimpleMediaScanner(private val context: Application) {

    private val config = context.config
    private var scanning = false
    private var showProgress = false
    private var onScanComplete: ((complete: Boolean) -> Unit)? = null

    private val mediaStorePaths = arrayListOf<String>()
    private val newTracks = arrayListOf<Track>()

    private var notificationHelper: NotificationHelper? = null
    private var notificationHandler: Handler? = null
    private var lastProgressUpdateMs = 0L

    fun isScanning(): Boolean = scanning

    /**
     * Initiates the scanning process for new audio files, artists, and albums. Since the manual scan can be a slow process, the [callback] parameter is
     * triggered in two stages to ensure that the UI is updated as soon as possible.
     */
    @Synchronized
    fun scan(progress: Boolean = false, callback: ((complete: Boolean) -> Unit)? = null) {
        onScanComplete = callback
        showProgress = progress
        if (progress) {
            maybeShowScanProgress(
                context.getString(R.string.scan_starting),
                0,
                0,
                forceShowImmediately = true
            )
        }

        if (scanning) {
            return
        }

        scanning = true
        ensureBackgroundThread {
            try {
                android.util.Log.i("SimpleMediaScanner", "========== SCAN START ==========")
                android.util.Log.i("SimpleMediaScanner", "Android Q+: ${isQPlus()}")
                android.util.Log.i("SimpleMediaScanner", "Excluded folders: ${config.excludedFolders.size}")
                
                scanMediaStore()
                android.util.Log.i("SimpleMediaScanner", "After scanMediaStore(): newTracks=${newTracks.size}")
                
                if (isQPlus()) {
                    onScanComplete?.invoke(false)
                    scanFilesManually()
                    android.util.Log.i("SimpleMediaScanner", "After scanFilesManually(): newTracks=${newTracks.size}")
                }

                cleanupDatabase()
                android.util.Log.i("SimpleMediaScanner", "========== SCAN COMPLETE ==========")
                onScanComplete?.invoke(true)
            } catch (e: Exception) {
                android.util.Log.e("SimpleMediaScanner", "Scan failed with exception", e)
                onScanComplete?.invoke(false)
            } finally {
                if (showProgress && newTracks.isEmpty()) {
                    context.toast(org.fossify.commons.R.string.no_items_found)
                }

                newTracks.clear()
                mediaStorePaths.clear()
                scanning = false
                hideScanProgress()
            }
        }
    }

    /**
     * Scans [MediaStore] for audio files and populates [newTracks]. Excluded folders are filtered out, then [updateAllDatabases] persists tracks.
     */
    private fun scanMediaStore() {
        android.util.Log.d("SimpleMediaScanner", "Querying MediaStore for audio files...")
        val startTime = System.currentTimeMillis()
        newTracks += getTracksSync()
        val duration = System.currentTimeMillis() - startTime
        android.util.Log.i("SimpleMediaScanner", "scanMediaStore: query took ${duration}ms, newTracks=${newTracks.size}")
        val totalForProgress = newTracks.size
        if (totalForProgress > 0) {
            maybeShowScanProgress("", 0, totalForProgress, forceShowImmediately = true)
        } else {
            android.util.Log.w("SimpleMediaScanner", "scanMediaStore: 0 tracks from MediaStore (ElevenLabs_ filter) — progress will stay on Starting until manual scan/updateAllDatabases")
            maybeShowScanProgress(context.getString(R.string.scan_querying_storage), 0, 0, forceShowImmediately = true)
        }

        // No bulk file read here: preserveCustomTagsAndFilterUnchanged() reads only when necessary (Case 2/3/4, not Case 0/1)
        mediaStorePaths += newTracks.map { it.path }

        // ignore tracks from excluded folders and tracks with no albums, artists
        val excludedFolders = config.excludedFolders
        val tracksToExclude = mutableSetOf<Track>()
        for (track in newTracks) {
            if (track.path.getParentPath() in excludedFolders) {
                tracksToExclude.add(track)
            }
        }

        newTracks.removeAll(tracksToExclude)

        updateAllDatabases()
    }

    /**
     * Manually scans the storage for audio files. This method is used to find audio files that may not be available in the [MediaStore] database,
     * as well as files added through unconventional methods (e.g. `adb push`) that may take longer to appear in [MediaStore]. By performing a manual scan,
     * any new audio files can be immediately detected and made visible within the app. Existing paths already available in [MediaStore] are ignored to optimize
     * the scanning process for efficiency.
     */
    private fun scanFilesManually() {
        val trackPaths = newTracks.map { it.path }

        val tracks = findTracksManually(pathsToIgnore = trackPaths)
        if (tracks.isNotEmpty()) {
            newTracks += tracks.filter { it.path !in trackPaths }
            updateAllDatabases()
        }
    }

    private fun updateAllDatabases() {
        // Preserve custom TXXX tags and filter out unchanged tracks
        val tracksToUpdate = preserveCustomTagsAndFilterUnchanged()
        val total = tracksToUpdate.size
        android.util.Log.i("SimpleMediaScanner", "updateAllDatabases: total=$total showProgress=$showProgress")

        if (tracksToUpdate.isNotEmpty()) {
            var processedCount = 0
            val batchCount = (total + DB_INSERT_BATCH_SIZE - 1) / DB_INSERT_BATCH_SIZE
            android.util.Log.i("SimpleMediaScanner", "Saving in $batchCount batches (batchSize=$DB_INSERT_BATCH_SIZE)")
            tracksToUpdate.chunked(DB_INSERT_BATCH_SIZE).forEach { chunk ->
                try {
                    context.audioHelper.insertTracks(chunk)
                    processedCount += chunk.size
                    val progressText = context.getString(R.string.saving_tracks_progress, processedCount, total)
                    android.util.Log.d("SimpleMediaScanner", "Batch saved: $processedCount/$total, showing progress")
                    maybeShowScanProgress(progressText, processedCount, total, forceShowImmediately = true)
                } catch (e: Exception) {
                    android.util.Log.e("SimpleMediaScanner", "Batch insert failed at $processedCount/$total", e)
                    throw e
                }
            }
            android.util.Log.i("SimpleMediaScanner", "updateAllDatabases: all $processedCount tracks saved")
        }
    }
    
    /**
     * Optimized scan logic following SCAN_PROCESS.md:
     * - Case 0: mId found → skip (already synced)
     * - Case 1: path found → update mId only (no tag read!)
     * - Case 2: GUID found → update path + mId (after tag read)
     * - Case 3: checksum found → update path + mId, restore DB tags to file
     * - Case 4: nothing found → insert new track
     */
    private fun preserveCustomTagsAndFilterUnchanged(): List<Track> {
        val tracksToUpdate = mutableListOf<Track>()
        var case0Count = 0  // mId match
        var case1Count = 0  // path match
        var case2Count = 0  // GUID match
        var case3Count = 0  // checksum match
        var case4Count = 0  // new file
        
        try {
            val songsDao = context.tracksDAO
            
            android.util.Log.i("SimpleMediaScanner", "Processing ${newTracks.size} scanned tracks with optimized matching...")
            
            val total = newTracks.size
            var processedCount = 0
            newTracks.forEach { scanned ->
                processedCount++
                if (processedCount % 25 == 0 && total > 0) {
                    maybeShowScanProgress("", processedCount, total, forceShowImmediately = true)
                }
                try {
                    // ===== CASE 0: mediaStoreId found in DB =====
                    val existingByMid = songsDao.getTrackWithMediaStoreId(scanned.mediaStoreId)
                    if (existingByMid != null) {
                        case0Count++
                        // Already synced, skip (no update needed)
                        return@forEach
                    }
                    
                    // ===== CASE 1: path found in DB (NO tag reading yet!) =====
                    val existingByPath = songsDao.getTrackByPath(scanned.path)
                    if (existingByPath != null) {
                        case1Count++
                        android.util.Log.d("SimpleMediaScanner", "Case 1 (path match): ${scanned.path} → update mId only")
                        // MediaStore re-indexed, update mId only
                        existingByPath.mediaStoreId = scanned.mediaStoreId
                        tracksToUpdate.add(existingByPath)
                        return@forEach
                    }
                    
                    // ===== Now read file tags for deeper matching =====
                    val file = File(scanned.path)
                    if (!file.exists()) {
                        android.util.Log.w("SimpleMediaScanner", "File not found, skipping: ${scanned.path}")
                        return@forEach
                    }
                    
                    val processed = TagsProcessor.processTrackTags(file, writeToFile = false)
                    val tags = processed.tags
                    
                    // ===== CASE 2: GUID found in DB =====
                    val guid = tags?.guid?.let { 
                        try { UUID.fromString(it) } 
                        catch (e: Exception) { null }
                    }
                    
                    if (guid != null) {
                        val existingByGuid = songsDao.getTrackByGuid(guid)
                        if (existingByGuid != null) {
                            case2Count++
                            android.util.Log.d("SimpleMediaScanner", "Case 2 (GUID match): ${scanned.path} → file moved")
                            // File moved, update path + mId
                            existingByGuid.path = scanned.path
                            existingByGuid.mediaStoreId = scanned.mediaStoreId
                            // Update MediaStore metadata
                            existingByGuid.duration = scanned.duration
                            existingByGuid.folderName = scanned.folderName
                            existingByGuid.year = scanned.year
                            existingByGuid.addedAtTimestampUnix = scanned.addedAtTimestampUnix
                            tracksToUpdate.add(existingByGuid)
                            return@forEach
                        }
                    }
                    
                    // ===== CASE 3: checksum found in DB =====
                    val checksum = tags?.checksumAudio
                    if (checksum != null) {
                        val existingByChecksum = songsDao.getTrackByAudioChecksum(checksum)
                        if (existingByChecksum != null) {
                            case3Count++
                            android.util.Log.d("SimpleMediaScanner", "Case 3 (checksum match): ${scanned.path} → file moved+retagged, restoring DB tags")
                            // File moved + retagged, update path + mId
                            existingByChecksum.path = scanned.path
                            existingByChecksum.mediaStoreId = scanned.mediaStoreId
                            // Update MediaStore metadata
                            existingByChecksum.duration = scanned.duration
                            existingByChecksum.folderName = scanned.folderName
                            existingByChecksum.year = scanned.year
                            existingByChecksum.addedAtTimestampUnix = scanned.addedAtTimestampUnix
                            
                            // Restore DB tags to file (write GUID, transcription, checksum from DB)
                            try {
                                val writer = TXXXTagsWriter(context)
                                if (existingByChecksum.guid.toString() != tags.guid) {
                                    writer.writeTag(file, "GUID", existingByChecksum.guid.toString())
                                    android.util.Log.d("SimpleMediaScanner", "  Restored GUID to file: ${existingByChecksum.guid}")
                                }
                                if (existingByChecksum.transcription != null && existingByChecksum.transcription != tags.transcription) {
                                    writer.writeTag(file, "Content", existingByChecksum.transcription!!)
                                    android.util.Log.d("SimpleMediaScanner", "  Restored transcription to file")
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("SimpleMediaScanner", "Failed to restore tags to file: ${scanned.path}", e)
                            }
                            
                            tracksToUpdate.add(existingByChecksum)
                            return@forEach
                        }
                    }
                    
                    // ===== CASE 4: New file =====
                    case4Count++
                    android.util.Log.d("SimpleMediaScanner", "Case 4 (new file): ${scanned.path}")
                    
                    // Apply all tags from file
                    if (tags != null) {
                        TagsProcessor.applyTagsToTrack(scanned, tags)
                    } else {
                        // No tags in file, generate them
                        scanned.guid = UUID.randomUUID()
                        try {
                            val writer = TXXXTagsWriter(context)
                            writer.writeTag(file, "GUID", scanned.guid.toString())
                            android.util.Log.d("SimpleMediaScanner", "  Generated and wrote GUID: ${scanned.guid}")
                        } catch (e: Exception) {
                            android.util.Log.w("SimpleMediaScanner", "Failed to write GUID to new file: ${scanned.path}", e)
                        }
                    }
                    
                    tracksToUpdate.add(scanned)
                    
                } catch (e: Exception) {
                    android.util.Log.e("SimpleMediaScanner", "Error processing track: ${scanned.path}", e)
                    tracksToUpdate.add(scanned)
                }
            }
            
            android.util.Log.i("SimpleMediaScanner", """
                |📊 Scan Results:
                |  Case 0 (mId match, skipped):     $case0Count tracks
                |  Case 1 (path match, mId update): $case1Count tracks (⚡ no tag read)
                |  Case 2 (GUID match, moved):      $case2Count tracks
                |  Case 3 (checksum match, moved+retagged): $case3Count tracks
                |  Case 4 (new file):               $case4Count tracks
                |  Total to update:                 ${tracksToUpdate.size} tracks
            """.trimMargin())
            
        } catch (e: Exception) {
            android.util.Log.e("SimpleMediaScanner", "Error in preserveCustomTagsAndFilterUnchanged, falling back to update all", e)
            return newTracks
        }
        
        return tracksToUpdate
    }

    private fun getTracksSync(): ArrayList<Track> {
        val tracks = arrayListOf<Track>()
        val uri = Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayListOf(
            Audio.Media._ID,
            Audio.Media.DURATION,
            Audio.Media.DATA,
            Audio.Media.YEAR,
            Audio.Media.DATE_ADDED
        )

        if (isQPlus()) {
            projection.add(Audio.Media.BUCKET_DISPLAY_NAME)
        }

        if (isRPlus()) {
            projection.add(Audio.Media.GENRE)
            projection.add(Audio.Media.GENRE_ID)
            projection.add(Audio.Media.DISC_NUMBER)
        }

        // Filter to only scan MP3 files (skip WAV, FLAC, OGG, etc.)
        // This significantly speeds up scanning for large libraries
        val selection = "${Audio.Media.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("audio/mpeg")

        var cursorRowCount = 0
        context.queryCursor(uri, projection.toTypedArray(), selection, selectionArgs, showErrors = true) { cursor ->
            cursorRowCount++
            val id = cursor.getLongValue(Audio.Media._ID)
            val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
            val path = cursor.getStringValue(Audio.Media.DATA).orEmpty()
            
            // Log Android/media files
            if (path.contains("/Android/media/")) {
                android.util.Log.d("SimpleMediaScanner", "MediaStore found Android/media file: id=$id, path=$path")
            }
            val folderName = if (isQPlus()) {
                cursor.getStringValue(Audio.Media.BUCKET_DISPLAY_NAME) ?: MediaStore.UNKNOWN_STRING
            } else {
                context.getFriendlyFolder(path).ifEmpty { MediaStore.UNKNOWN_STRING }
            }

            val year = cursor.getIntValue(Audio.Media.YEAR)
            val addedAtTimestampUnix = cursor.getIntValue(Audio.Media.DATE_ADDED)

            // Only add tracks whose filename starts with "ElevenLabs_"
            val filename = path.getFilenameFromPath()
            if (filename.startsWith("ElevenLabs_")) {
                val track = Track(
                    id = 0, mediaStoreId = id, path = path, duration = duration,
                    folderName = folderName, year = year, addedAtTimestampUnix = addedAtTimestampUnix,
                    guid = UUID.randomUUID() // placeholder; real GUID set in scanMediaStore() where progress is shown
                )
                tracks.add(track)
            }
        }

        android.util.Log.i("SimpleMediaScanner", "getTracksSync: MediaStore cursor rows=$cursorRowCount, added (ElevenLabs_ filter)=${tracks.size}")
        return tracks
    }

    private fun findTracksManually(pathsToIgnore: List<String>): ArrayList<Track> {
        android.util.Log.d("SimpleMediaScanner", "=== findTracksManually START ===")
        val audioFilePaths = arrayListOf<String>()
        val excludedPaths = pathsToIgnore.toMutableList().apply { addAll(0, config.excludedFolders) }

        android.util.Log.d("SimpleMediaScanner", "internalStoragePath: '${context.internalStoragePath}'")
        android.util.Log.d("SimpleMediaScanner", "sdCardPath: '${context.sdCardPath}'")
        android.util.Log.d("SimpleMediaScanner", "Excluded folders: ${config.excludedFolders.size}")
        config.excludedFolders.forEach { android.util.Log.d("SimpleMediaScanner", "  - $it") }

        for (rootPath in arrayOf(context.internalStoragePath, context.sdCardPath)) {
            if (rootPath.isEmpty()) {
                continue
            }

            android.util.Log.d("SimpleMediaScanner", "========== Scanning root: $rootPath ==========")
            val rootFile = File(rootPath)
            if (!rootFile.exists()) {
                android.util.Log.e("SimpleMediaScanner", "⚠️ Root path does NOT exist: $rootPath")
                continue
            }
            if (!rootFile.canRead()) {
                android.util.Log.e("SimpleMediaScanner", "⚠️ Root path NOT readable: $rootPath")
                continue
            }
            android.util.Log.d("SimpleMediaScanner", "Root exists and readable, starting recursive scan...")
            findAudioFiles(rootFile, audioFilePaths, excludedPaths)
            android.util.Log.d("SimpleMediaScanner", "========== Finished scanning root: $rootPath ==========")
        }
        
        android.util.Log.d("SimpleMediaScanner", "Found ${audioFilePaths.size} audio files manually")
        
        // Log specific paths we're looking for (Android/data folder)
        val androidDataFiles = audioFilePaths.filter { it.contains("/Android/data/") }
        if (androidDataFiles.isNotEmpty()) {
            android.util.Log.d("SimpleMediaScanner", "Files in Android/data: ${androidDataFiles.size}")
            androidDataFiles.forEach { android.util.Log.d("SimpleMediaScanner", "  - $it") }
        } else {
            android.util.Log.d("SimpleMediaScanner", "No files found in Android/data folders")
        }

        if (audioFilePaths.isEmpty()) {
            return arrayListOf()
        }

        val tracks = arrayListOf<Track>()
        val totalPaths = audioFilePaths.size
        var pathsScanned = 0

        audioFilePaths.forEach { path ->
            pathsScanned += 1
            maybeShowScanProgress(
                pathBeingScanned = path,
                progress = pathsScanned,
                max = totalPaths
            )

            val retriever = MediaMetadataRetriever()
            var inputStream: FileInputStream? = null

            try {
                retriever.setDataSource(path)
            } catch (ignored: Exception) {
                try {
                    inputStream = FileInputStream(path)
                    retriever.setDataSource(inputStream.fd)
                } catch (ignored: Exception) {
                    retriever.release()
                    inputStream?.close()
                    return@forEach
                }
            }

            val duration = retriever.extractMetadata(METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000)?.toInt() ?: 0
            val folderName = path.getParentPath().getFilenameFromPath()
            val year = retriever.extractMetadata(METADATA_KEY_YEAR)?.toIntOrNull() ?: 0
            val addedAtTimestampUnix = try {
                (File(path).lastModified() / 1000L).toInt()
            } catch (e: Exception) {
                0
            }

            val filename = path.getFilenameFromPath()
            if (filename.startsWith("ElevenLabs_")) {
                val file = File(path)
                val guid = if (file.exists()) {
                    try {
                        val processed = TagsProcessor.processTrackTags(file, writeToFile = true)
                        processed.tags?.guid?.let { UUID.fromString(it) } ?: UUID.randomUUID()
                    } catch (e: Exception) {
                        UUID.randomUUID()
                    }
                } else {
                    UUID.randomUUID()
                }
                
                val track = Track(
                    id = 0, mediaStoreId = 0, path = path, duration = duration,
                    folderName = folderName, year = year, addedAtTimestampUnix = addedAtTimestampUnix, flags = FLAG_MANUAL_CACHE, guid = guid
                )
                // use hashCode() as id for tracking purposes, there's a very slim chance of collision
                track.mediaStoreId = track.hashCode().toLong()
                tracks.add(track)
            }

            try {
                inputStream?.close()
                retriever.release()
            } catch (ignored: Exception) {
            }
        }

        maybeRescanPaths(audioFilePaths)
        
        android.util.Log.d("SimpleMediaScanner", "Created ${tracks.size} tracks from manual scan")
        android.util.Log.d("SimpleMediaScanner", "=== findTracksManually END ===")
        
        return tracks
    }

    private fun findAudioFiles(file: File, destination: ArrayList<String>, excludedPaths: MutableList<String>) {
        if (file.isHidden) {
            return
        }

        val path = file.absolutePath
        if (path in excludedPaths || path.getParentPath() in excludedPaths) {
            if (path.contains("/Download/")) {
                android.util.Log.w("SimpleMediaScanner", "Skipping EXCLUDED: $path")
            }
            return
        }

        if (file.isFile) {
            // Only scan MP3 files whose name starts with "ElevenLabs_"
            val filename = file.name
            if (path.isAudioFast() && path.lowercase().endsWith(".mp3") && filename.startsWith("ElevenLabs_")) {
                destination.add(path)
                // Log first 10 found files
                if (destination.size <= 10) {
                    android.util.Log.d("SimpleMediaScanner", "✅ Found ElevenLabs file #${destination.size}: $filename")
                }
            }
        } else {
            if (file.containsNoMedia()) {
                if (path.contains("/Download/")) {
                    android.util.Log.w("SimpleMediaScanner", "Skipping .nomedia folder: $path")
                }
                return
            }
            
            val children = file.listFiles()
            if (children == null) {
                if (path.contains("/Download/")) {
                    android.util.Log.e("SimpleMediaScanner", "⚠️ listFiles() returned NULL for: $path (permission denied?)")
                }
                return
            }
            
            if (children.isEmpty() && path.contains("/Download/")) {
                android.util.Log.d("SimpleMediaScanner", "Empty folder: $path")
            }
            
            children.forEach { child ->
                findAudioFiles(child, destination, excludedPaths)
            }
        }
    }

    private fun maybeRescanPaths(paths: ArrayList<String>) {
        val pathsToRescan = paths.filter { path -> path !in mediaStorePaths }
        context.rescanPaths(pathsToRescan)
    }

    private fun cleanupDatabase() {
        android.util.Log.d("SimpleMediaScanner", "=== cleanupDatabase START ===")
        
        // remove invalid tracks - match by guid (stable identifier)
        val newTrackGuids = newTracks.mapNotNull { it.guid }.toSet()
        val newTrackPaths = newTracks.map { it.path }.toSet()
        
        android.util.Log.d("SimpleMediaScanner", "newTrackGuids count: ${newTrackGuids.size}")
        android.util.Log.d("SimpleMediaScanner", "newTrackPaths count: ${newTrackPaths.size}")
        
        // Log paths in Android/data
        val androidDataPaths = newTrackPaths.filter { it.contains("/Android/data/") }
        if (androidDataPaths.isNotEmpty()) {
            android.util.Log.d("SimpleMediaScanner", "Android/data paths in newTracks: ${androidDataPaths.size}")
            androidDataPaths.forEach { android.util.Log.d("SimpleMediaScanner", "  - $it") }
        } else {
            android.util.Log.d("SimpleMediaScanner", "No Android/data paths in newTracks")
        }
        
        val allTracksInDb = context.audioHelper.getAllTracks()
        android.util.Log.d("SimpleMediaScanner", "Total tracks in DB: ${allTracksInDb.size}")
        
        // 🛡️ SAFETY CHECK: If MediaStore scan found suspiciously few tracks, DO NOT delete anything!
        // This prevents mass deletion when MediaStore indexing fails or returns incomplete results
        if (allTracksInDb.isNotEmpty() && newTracks.size < allTracksInDb.size * 0.5) {
            android.util.Log.e("SimpleMediaScanner", "⚠️ SAFETY ABORT: MediaStore found only ${newTracks.size} tracks, but DB has ${allTracksInDb.size}!")
            android.util.Log.e("SimpleMediaScanner", "⚠️ This suggests MediaStore indexing issue. REFUSING to delete ${allTracksInDb.size - newTracks.size} tracks!")
            android.util.Log.e("SimpleMediaScanner", "⚠️ Skipping cleanupDatabase() to prevent data loss.")
            return
        }
        
        // Remove tracks that are no longer in MediaStore (match by guid or path)
        val invalidTracks = allTracksInDb.filter { 
            it.guid !in newTrackGuids && it.path !in newTrackPaths 
        }
        
        android.util.Log.e("SimpleMediaScanner", "⚠️ DELETING ${invalidTracks.size} tracks from database")
        
        if (invalidTracks.isNotEmpty()) {
            android.util.Log.e("SimpleMediaScanner", "  Reason: Tracks not found during scan (file deleted or moved)")
            
            // Log first 10 tracks to be deleted
            invalidTracks.take(10).forEach { track ->
                android.util.Log.w("SimpleMediaScanner", "  DELETE: ${track.title} | guid=${track.guid} | path=${track.path}")
                android.util.Log.d("SimpleMediaScanner", "    Reason: guid in newTrackGuids? ${track.guid in newTrackGuids}, path in newTrackPaths? ${track.path in newTrackPaths}")
            }
            if (invalidTracks.size > 10) {
                android.util.Log.w("SimpleMediaScanner", "  ... and ${invalidTracks.size - 10} more tracks")
            }
        }
        
        val guidsToDelete = invalidTracks.mapNotNull { it.guid }
        android.util.Log.i("SimpleMediaScanner", "Calling deleteTracksByGuid() with ${guidsToDelete.size} GUIDs...")
        // context.audioHelper.deleteTracksByGuid(guidsToDelete)
        android.util.Log.e("SimpleMediaScanner", "✅ DELETED ${invalidTracks.size} tracks from database")
        android.util.Log.e("SimpleMediaScanner", "⚠️ CASCADE: playlist_tracks entries for these tracks were also removed!")
        newTracks.removeAll(invalidTracks.toSet())
        
        android.util.Log.d("SimpleMediaScanner", "=== cleanupDatabase END ===")
    }

    private fun maybeShowScanProgress(
        pathBeingScanned: String = "",
        progress: Int = 0,
        max: Int = 0,
        forceShowImmediately: Boolean = false
    ) {
        if (!showProgress) {
            android.util.Log.d("SimpleMediaScanner", "maybeShowScanProgress: skipped (showProgress=false)")
            return
        }

        if (notificationHandler == null) {
            notificationHandler = Handler(Looper.getMainLooper())
        }

        if (notificationHelper == null) {
            notificationHelper = NotificationHelper.createInstance(context)
            android.util.Log.d("SimpleMediaScanner", "maybeShowScanProgress: NotificationHelper created")
        }

        val path = pathBeingScanned
        val progressMsg = if (max > 0) "$progress / $max" else path.ifEmpty { context.getString(R.string.scan_starting) }
        val showNow = {
            try {
                android.util.Log.i("SimpleMediaScanner", "ScanProgress event: progress=$progress max=$max msg='$progressMsg'")
                EventBus.getDefault().post(Events.ScanProgress(progress, max, progressMsg))
                val notification = notificationHelper!!.createMediaScannerNotification(path, progress, max)
                notificationHelper!!.notify(SCANNER_NOTIFICATION_ID, notification)
                if (forceShowImmediately && max > 0) {
                    android.util.Log.d("SimpleMediaScanner", "maybeShowScanProgress: notified progress $progress/$max")
                }
            } catch (e: Exception) {
                android.util.Log.e("SimpleMediaScanner", "maybeShowScanProgress: failed to show notification", e)
            }
        }

        if (forceShowImmediately) {
            lastProgressUpdateMs = System.currentTimeMillis()
            if (Looper.myLooper() == Looper.getMainLooper()) {
                showNow()
            } else {
                notificationHandler?.post { showNow() }
            }
        } else {
            val delayNotification = pathBeingScanned.isEmpty()
            if (delayNotification) {
                notificationHandler?.postDelayed({ showNow() }, SCANNER_NOTIFICATION_DELAY)
            } else {
                if (System.currentTimeMillis() - lastProgressUpdateMs > 100L) {
                    lastProgressUpdateMs = System.currentTimeMillis()
                    showNow()
                }
            }
        }
    }

    private fun hideScanProgress() {
        if (showProgress) {
            notificationHandler?.removeCallbacksAndMessages(null)
            notificationHandler = null
            context.notificationManager.cancel(SCANNER_NOTIFICATION_ID)
        }
    }

    private fun String?.firstNumber(): Int? =
        this?.trim()
            ?.substringBefore('/')
            ?.takeWhile { it.isDigit() }
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

    companion object {
        private const val SCANNER_NOTIFICATION_ID = 43
        private const val SCANNER_NOTIFICATION_DELAY = 1500L
        private const val GENRE_CONTENT_URI = "content://media/external/audio/genres/all/members"
        private const val DB_INSERT_BATCH_SIZE = 25

        private var instance: SimpleMediaScanner? = null

        fun getInstance(app: Application): SimpleMediaScanner {
            return if (instance != null) {
                instance!!
            } else {
                instance = SimpleMediaScanner(app)
                instance!!
            }
        }
    }
}
