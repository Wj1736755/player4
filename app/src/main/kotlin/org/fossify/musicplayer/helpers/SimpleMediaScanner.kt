package org.fossify.musicplayer.helpers

import android.app.Application
import android.content.ContentUris
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
import org.fossify.musicplayer.models.*
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
    private val newAlbums = arrayListOf<Album>()
    private val newArtists = arrayListOf<Artist>()
    private val newGenres = arrayListOf<Genre>()

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
        maybeShowScanProgress()

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
            } finally {
                if (showProgress && newTracks.isEmpty()) {
                    context.toast(org.fossify.commons.R.string.no_items_found)
                }

                newTracks.clear()
                newAlbums.clear()
                newArtists.clear()
                newGenres.clear()
                mediaStorePaths.clear()
                scanning = false
                hideScanProgress()
            }
        }
    }

    /**
     * Scans [MediaStore] for audio files. Querying [MediaStore.Audio.Artists] and [MediaStore.Audio.Albums] is not necessary in this context, we
     * can manually group tracks by artist and album as done in [scanFilesManually]. However, this approach would require fetching album art bitmaps repeatedly
     * using [MediaMetadataRetriever] instead of utilizing the cached version provided by [MediaStore]. This may become a necessity when we add more nuanced
     * features e.g. group albums by `ALBUM-ARTIST` instead of `ARTIST`
     */
    private fun scanMediaStore() {
        android.util.Log.d("SimpleMediaScanner", "Querying MediaStore for audio files...")
        val startTime = System.currentTimeMillis()
        newTracks += getTracksSync()
        val duration = System.currentTimeMillis() - startTime
        android.util.Log.d("SimpleMediaScanner", "MediaStore query took ${duration}ms, found ${newTracks.size} tracks")
        
        // 🔧 CRITICAL FIX: Read guid from ID3 tags IMMEDIATELY after MediaStore scan
        // Without this, all guid values are NULL, causing:
        // 1. preserveCustomTagsAndFilterUnchanged() to treat ALL tracks as new
        // 2. cleanupDatabase() to delete tracks that aren't in newTrackGuids (empty set!)
        android.util.Log.i("SimpleMediaScanner", "📝 Reading GUID from ID3 tags for ${newTracks.size} tracks...")
        val guidStartTime = System.currentTimeMillis()
        var processedCount = 0
        newTracks.forEach { track ->
            processedCount++
            if (processedCount % 100 == 0) {
                android.util.Log.d("SimpleMediaScanner", "  Progress: $processedCount/${newTracks.size} tracks processed...")
            }
            try {
                val file = File(track.path)
                if (file.exists()) {
                    val processed = TagsProcessor.processTrackTags(file, writeToFile = false)
                    val guidString = processed.tags?.guid
                    track.guid = if (guidString != null) {
                        try { UUID.fromString(guidString) } catch (e: Exception) { null }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("SimpleMediaScanner", "Failed to read GUID for: ${track.path}", e)
                // guid remains null - track will be matched by path in cleanupDatabase()
            }
        }
        val tracksWithGuid = newTracks.count { it.guid != null }
        val guidDuration = System.currentTimeMillis() - guidStartTime
        android.util.Log.i("SimpleMediaScanner", "✅ GUID reading complete: $tracksWithGuid/${newTracks.size} tracks have GUID (${guidDuration}ms, ${guidDuration / 1000.0}s)")
        
        newArtists += getArtistsSync()
        newAlbums += getAlbumsSync(newArtists)
        newGenres += getGenresSync()
        mediaStorePaths += newTracks.map { it.path }
        assignGenreToTracks()

        // ignore tracks from excluded folders and tracks with no albums, artists
        val albumIds = newAlbums.map { it.id }
        val artistIds = newArtists.map { it.id }
        val excludedFolders = config.excludedFolders
        val tracksToExclude = mutableSetOf<Track>()
        for (track in newTracks) {
            if (track.path.getParentPath() in excludedFolders) {
                tracksToExclude.add(track)
                continue
            }

            if (track.albumId !in albumIds || track.artistId !in artistIds) {
                tracksToExclude.add(track)
            }
        }

        newTracks.removeAll(tracksToExclude)

        // update album, track count if any tracks were excluded
        for (album in newAlbums) {
            val tracksInAlbum = newTracks.filter { it.albumId == album.id }
            album.trackCnt = tracksInAlbum.size
            if (album.trackCnt > 0) {
                album.addedAtTimestampUnix = tracksInAlbum.first().addedAtTimestampUnix
            }
        }

        for (artist in newArtists) {
            artist.trackCnt = newTracks.filter { it.artistId == artist.id }.size
            val albumsByArtist = newAlbums.filter { it.artistId == artist.id }
            artist.albumCnt = albumsByArtist.size
            artist.albumArt = albumsByArtist.firstOrNull { it.coverArt.isNotEmpty() }?.coverArt.orEmpty()
        }

        for (genre in newGenres) {
            val genreTracks = newTracks.filter { it.genreId == genre.id }
            genre.trackCnt = genreTracks.size
            genre.albumArt = genreTracks.firstOrNull { it.coverArt.isNotEmpty() }?.coverArt.orEmpty()
        }

        // remove invalid albums, artists
        newAlbums.removeAll { it.trackCnt == 0 }
        newArtists.removeAll { it.trackCnt == 0 || it.albumCnt == 0 }
        newGenres.removeAll { it.trackCnt == 0 }

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
        val artistNames = newArtists.map { it.title }
        val albumNames = newAlbums.map { it.title }
        val genreNames = newGenres.map { it.title }

        val tracks = findTracksManually(pathsToIgnore = trackPaths)
        if (tracks.isNotEmpty()) {
            val artists = splitIntoArtists(tracks)
            val albums = splitIntoAlbums(tracks)
            val genres = splitIntoGenres(tracks)

            newTracks += tracks.filter { it.path !in trackPaths }
            newAlbums += albums.filter { it.title !in albumNames }
            newArtists += artists.filter { it.title !in artistNames }
            newGenres += genres.filter { it.title !in genreNames }

            updateAllDatabases()
        }
    }

    private fun updateAllDatabases() {
        // Preserve custom TXXX tags and filter out unchanged tracks
        val tracksToUpdate = preserveCustomTagsAndFilterUnchanged()
        
        context.audioHelper.apply {
            if (tracksToUpdate.isNotEmpty()) {
                insertTracks(tracksToUpdate)
            }
            insertAlbums(newAlbums)
            insertArtists(newArtists)
            insertGenres(newGenres)
        }
        updateAllTracksPlaylist()
    }
    
    private fun preserveCustomTagsAndFilterUnchanged(): List<Track> {
        val tracksToUpdate = mutableListOf<Track>()
        var skippedCount = 0
        
        try {
            // Fetch all tracks once and create a map for O(1) lookup by guid (stable identifier)
            // This is much faster than querying database for each track individually
            val allTracks = context.audioHelper.getAllTracks()
            val existingTracksMap = allTracks.associateBy { it.guid }
            
            android.util.Log.i("SimpleMediaScanner", "Comparing ${newTracks.size} new tracks against ${existingTracksMap.size} existing tracks")
            
            newTracks.forEach { newTrack ->
                try {
                    // Match by guid (stable across rescans) - newTrack.guid is already populated by TagsProcessor
                    val existing = existingTracksMap[newTrack.guid]
                    if (existing != null) {
                        // Preserve all custom TXXX tags from existing track
                        newTrack.transcription = existing.transcription
                        newTrack.transcriptionNormalized = existing.transcriptionNormalized
                        newTrack.guid = existing.guid
                        newTrack.tagTxxxCreatedAtUnix = existing.tagTxxxCreatedAtUnix
                        newTrack.checksumAudio = existing.checksumAudio
                        
                        // Compare if track has changed (only relevant fields)
                        if (hasTrackChanged(existing, newTrack)) {
                            tracksToUpdate.add(newTrack)
                        } else {
                            skippedCount++
                        }
                    } else {
                        // New track - process TXXX tags (read existing, generate missing, write to file)
                        try {
                            val file = File(newTrack.path)
                            if (file.exists()) {
                                val processed = TagsProcessor.processTrackTags(file, writeToFile = true)
                                TagsProcessor.applyTagsToTrack(newTrack, processed.tags)
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("SimpleMediaScanner", "Error processing tags for new track: ${newTrack.path}", e)
                            // Continue without tags - fields will be null
                        }
                        
                        tracksToUpdate.add(newTrack)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SimpleMediaScanner", "Error processing track: ${newTrack.path}", e)
                    // On error, add track to be safe
                    tracksToUpdate.add(newTrack)
                }
            }
            
            if (skippedCount > 0) {
                android.util.Log.i("SimpleMediaScanner", "Skipped $skippedCount unchanged tracks, updating ${tracksToUpdate.size} tracks")
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleMediaScanner", "Error in preserveCustomTagsAndFilterUnchanged, falling back to update all", e)
            // On error, return all tracks to be safe
            return newTracks
        }
        
        return tracksToUpdate
    }
    
    private fun hasTrackChanged(existing: Track, new: Track): Boolean {
        // Compare only relevant fields that can change in MediaStore
        return existing.title != new.title ||
                existing.artist != new.artist ||
                existing.path != new.path ||
                existing.duration != new.duration ||
                existing.album != new.album ||
                existing.genre != new.genre ||
                existing.coverArt != new.coverArt ||
                existing.trackId != new.trackId ||
                existing.discNumber != new.discNumber ||
                existing.folderName != new.folderName ||
                existing.albumId != new.albumId ||
                existing.artistId != new.artistId ||
                existing.genreId != new.genreId ||
                existing.year != new.year ||
                existing.addedAtTimestampUnix != new.addedAtTimestampUnix
    }

    private fun updateAllTracksPlaylist() {
        if (!config.wasAllTracksPlaylistCreated) {
            val allTracksLabel = context.resources.getString(R.string.all_tracks)
            val playlist = Playlist(ALL_TRACKS_PLAYLIST_ID, allTracksLabel)
            context.audioHelper.insertPlaylist(playlist)
            config.wasAllTracksPlaylistCreated = true
        }

        // avoid re-adding tracks that have been explicitly removed from 'All tracks' playlist
        val excludedFolders = config.excludedFolders
        val tracksRemovedFromAllTracks = config.tracksRemovedFromAllTracksPlaylist.mapNotNull { 
            try { UUID.fromString(it) } catch (e: Exception) { null }
        }.toSet()
        val tracksForAllTracksPlaylist = newTracks
            .filter { it.guid !in tracksRemovedFromAllTracks && it.path.getParentPath() !in excludedFolders }
        RoomHelper(context).insertTracksWithPlaylist(tracksForAllTracksPlaylist as ArrayList<Track>, ALL_TRACKS_PLAYLIST_ID)
    }

    private fun getTracksSync(): ArrayList<Track> {
        val tracks = arrayListOf<Track>()
        val uri = Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayListOf(
            Audio.Media._ID,
            Audio.Media.DURATION,
            Audio.Media.DATA,
            Audio.Media.TITLE,
            Audio.Media.ARTIST,
            Audio.Media.ALBUM,
            Audio.Media.ALBUM_ID,
            Audio.Media.ARTIST_ID,
            Audio.Media.TRACK,
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

        context.queryCursor(uri, projection.toTypedArray(), selection, selectionArgs, showErrors = true) { cursor ->
            val id = cursor.getLongValue(Audio.Media._ID)
            val title = cursor.getStringValue(Audio.Media.TITLE)
            val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
            var trackId = cursor.getStringValue(Audio.Media.TRACK)?.firstNumber()
                ?: cursor.getIntValueOrNull(Audio.Media.TRACK)
            val path = cursor.getStringValue(Audio.Media.DATA).orEmpty()
            
            // Log Android/media files
            if (path.contains("/Android/media/")) {
                android.util.Log.d("SimpleMediaScanner", "MediaStore found Android/media file: id=$id, path=$path")
            }
            val artist = cursor.getStringValue(Audio.Media.ARTIST) ?: MediaStore.UNKNOWN_STRING
            val folderName = if (isQPlus()) {
                cursor.getStringValue(Audio.Media.BUCKET_DISPLAY_NAME) ?: MediaStore.UNKNOWN_STRING
            } else {
                context.getFriendlyFolder(path).ifEmpty { MediaStore.UNKNOWN_STRING }
            }

            val album = cursor.getStringValue(Audio.Media.ALBUM) ?: folderName
            val albumId = cursor.getLongValue(Audio.Media.ALBUM_ID)
            val artistId = cursor.getLongValue(Audio.Media.ARTIST_ID)
            val year = cursor.getIntValue(Audio.Media.YEAR)
            val addedAtTimestampUnix = cursor.getIntValue(Audio.Media.DATE_ADDED)
            val coverUri = ContentUris.withAppendedId(artworkUri, albumId)
            val coverArt = coverUri.toString()

            val genre: String
            val genreId: Long
            var discNumber: Int?
            if (isRPlus()) {
                genre = cursor.getStringValue(Audio.Media.GENRE).orEmpty()
                genreId = cursor.getLongValue(Audio.Media.GENRE_ID)
                discNumber = cursor.getStringValue(Audio.Media.DISC_NUMBER)?.firstNumber()
            } else {
                genre = ""
                genreId = 0
                discNumber = null
            }

            if (trackId != null && trackId >= 1000) {
                // derive disc number from track number when possible
                if (discNumber == null) {
                    discNumber = trackId / 1000
                }

                trackId %= 1000
            }

            // Only add tracks whose filename starts with "ElevenLabs_"
            val filename = path.getFilenameFromPath()
            if (!title.isNullOrEmpty() && filename.startsWith("ElevenLabs_")) {
                val track = Track(
                    id = 0, mediaStoreId = id, title = title, artist = artist, path = path, duration = duration, album = album, genre = genre,
                    coverArt = coverArt, trackId = trackId, discNumber = discNumber, folderName = folderName, albumId = albumId, artistId = artistId,
                    genreId = genreId, year = year, addedAtTimestampUnix = addedAtTimestampUnix
                )
                tracks.add(track)
            }
        }

        return tracks
    }

    private fun getArtistsSync(): ArrayList<Artist> {
        val artists = arrayListOf<Artist>()
        val uri = Audio.Artists.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            Audio.Artists._ID,
            Audio.Artists.ARTIST,
            Audio.Artists.NUMBER_OF_TRACKS,
            Audio.Artists.NUMBER_OF_ALBUMS
        )

        context.queryCursor(uri, projection, showErrors = true) { cursor ->
            val id = cursor.getLongValue(Audio.Artists._ID)
            val title = cursor.getStringValue(Audio.Artists.ARTIST) ?: MediaStore.UNKNOWN_STRING
            val albumCnt = cursor.getIntValue(Audio.Artists.NUMBER_OF_TRACKS)
            val trackCnt = cursor.getIntValue(Audio.Artists.NUMBER_OF_ALBUMS)
            val artist = Artist(id = id, title = title, albumCnt = albumCnt, trackCnt = trackCnt, albumArt = "")
            if (artist.albumCnt > 0 && artist.trackCnt > 0) {
                newArtists.add(artist)
            }
        }

        return artists
    }

    private fun getAlbumsSync(artists: ArrayList<Artist>): ArrayList<Album> {
        val albums = arrayListOf<Album>()
        val uri = Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayListOf(
            Audio.Albums._ID,
            Audio.Albums.ARTIST,
            Audio.Albums.FIRST_YEAR,
            Audio.Albums.ALBUM,
            Audio.Albums.NUMBER_OF_SONGS
        )

        if (isQPlus()) {
            projection.add(Audio.Albums.ARTIST_ID)
        }

        context.queryCursor(uri, projection.toTypedArray(), null, null, showErrors = true) { cursor ->
            val id = cursor.getLongValue(Audio.Albums._ID)
            val artistName = cursor.getStringValue(Audio.Albums.ARTIST) ?: MediaStore.UNKNOWN_STRING
            val title = cursor.getStringValue(Audio.Albums.ALBUM) ?: MediaStore.UNKNOWN_STRING
            val coverArt = ContentUris.withAppendedId(artworkUri, id).toString()
            val year = cursor.getIntValue(Audio.Albums.FIRST_YEAR)
            val trackCnt = cursor.getIntValue(Audio.Albums.NUMBER_OF_SONGS)
            val artistId = if (isQPlus()) {
                cursor.getLongValue(Audio.Albums.ARTIST_ID)
            } else {
                artists.first { it.title == artistName }.id
            }

            if (trackCnt > 0) {
                val album = Album(
                    id = id, artist = artistName, title = title, coverArt = coverArt, year = year, trackCnt = trackCnt, artistId = artistId, addedAtTimestampUnix = 0
                )
                albums.add(album)
            }
        }

        return albums
    }

    private fun getGenresSync(): ArrayList<Genre> {
        val genres = arrayListOf<Genre>()
        val uri = Audio.Genres.EXTERNAL_CONTENT_URI
        val projection = arrayListOf(Audio.Genres._ID, Audio.Genres.NAME)
        context.queryCursor(uri, projection.toTypedArray(), showErrors = true) { cursor ->
            val id = cursor.getLongValue(Audio.Genres._ID)
            val title = cursor.getStringValue(Audio.Genres.NAME)

            if (!title.isNullOrEmpty()) {
                val genre = Genre(id = id, title = title, trackCnt = 0, albumArt = "")
                genres.add(genre)
            }
        }

        return genres
    }

    /**
     * To map tracks to genres, we utilize [MediaStore.Audio.Genres.Members] because [MediaStore.Audio.Media.GENRE_ID] is not available on Android 11 and
     * below. It is essential to call this method after [getTracksSync].
     */
    private fun assignGenreToTracks() {
        if (isRPlus()) {
            return
        }

        val genreToTracks = hashMapOf<Long, MutableList<Long>>()
        val uri = GENRE_CONTENT_URI.toUri()
        val projection = arrayListOf(
            Audio.Genres.Members.GENRE_ID,
            Audio.Genres.Members.AUDIO_ID
        )

        context.queryCursor(uri, projection.toTypedArray(), showErrors = true) {
            val trackId = it.getLongValue(Audio.Genres.Members.AUDIO_ID)
            val genreId = it.getLongValue(Audio.Genres.Members.GENRE_ID)

            var tracks = genreToTracks[genreId]
            if (tracks == null) {
                tracks = mutableListOf(trackId)
            } else {
                tracks.add(trackId)
            }

            genreToTracks[genreId] = tracks
        }

        for ((genreId, trackIds) in genreToTracks) {
            for (track in newTracks) {
                if (track.mediaStoreId in trackIds) {
                    track.genreId = genreId
                }
            }
        }
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

            val title = retriever.extractMetadata(METADATA_KEY_TITLE) ?: path.getFilenameFromPath()
            val artist = retriever.extractMetadata(METADATA_KEY_ARTIST) ?: retriever.extractMetadata(METADATA_KEY_ALBUMARTIST) ?: MediaStore.UNKNOWN_STRING
            val duration = retriever.extractMetadata(METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000)?.toInt() ?: 0
            val folderName = path.getParentPath().getFilenameFromPath()
            val album = retriever.extractMetadata(METADATA_KEY_ALBUM) ?: folderName
            val trackNumber = retriever.extractMetadata(METADATA_KEY_CD_TRACK_NUMBER)
            val trackId = trackNumber?.firstNumber()
            val discNumber = retriever.extractMetadata(METADATA_KEY_DISC_NUMBER)?.firstNumber()
            val year = retriever.extractMetadata(METADATA_KEY_YEAR)?.toIntOrNull() ?: 0
            val addedAtTimestampUnix = try {
                (File(path).lastModified() / 1000L).toInt()
            } catch (e: Exception) {
                0
            }

            val genre = retriever.extractMetadata(METADATA_KEY_GENRE).orEmpty()

            // Only add tracks whose filename starts with "ElevenLabs_"
            val filename = path.getFilenameFromPath()
            if (title.isNotEmpty() && filename.startsWith("ElevenLabs_")) {
                val track = Track(
                    id = 0, mediaStoreId = 0, title = title, artist = artist, path = path, duration = duration, album = album, genre = genre,
                    coverArt = "", trackId = trackId, discNumber = discNumber, folderName = folderName, albumId = 0, artistId = 0,
                    genreId = 0, year = year, addedAtTimestampUnix = addedAtTimestampUnix, flags = FLAG_MANUAL_CACHE
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

    private fun splitIntoArtists(tracks: ArrayList<Track>): ArrayList<Artist> {
        val artists = arrayListOf<Artist>()
        val tracksGroupedByArtist = tracks.groupBy { it.artist }
        for ((artistName, tracksByArtist) in tracksGroupedByArtist) {
            val trackCnt = tracksByArtist.size
            if (trackCnt > 0) {
                val albumCnt = tracksByArtist.groupBy { it.album }.size
                val artist = Artist(0, artistName, albumCnt, trackCnt, "")
                val artistId = artist.hashCode().toLong()
                artist.id = artistId
                tracksByArtist.onEach { it.artistId = artistId }
                artists.add(artist)
            }
        }

        return artists
    }

    private fun splitIntoAlbums(tracks: ArrayList<Track>): ArrayList<Album> {
        val albums = arrayListOf<Album>()
        val tracksGroupedByAlbums = tracks.groupBy { it.album }
        for ((albumName, tracksInAlbum) in tracksGroupedByAlbums) {
            val trackCnt = tracksInAlbum.size
            if (trackCnt > 0) {
                val track = tracksInAlbum.first()
                val artistName = track.artist
                val year = track.year
                val album = Album(0, artistName, albumName, "", year, trackCnt, track.artistId, track.addedAtTimestampUnix)
                val albumId = album.hashCode().toLong()
                album.id = albumId
                tracksInAlbum.onEach { it.albumId = albumId }
                albums.add(album)
            }
        }

        return albums
    }

    private fun splitIntoGenres(tracks: ArrayList<Track>): ArrayList<Genre> {
        val genres = arrayListOf<Genre>()
        val tracksGroupedByGenres = tracks.groupBy { it.genre }
        for ((title, tracksInGenre) in tracksGroupedByGenres) {
            val trackCnt = tracksInGenre.size
            if (trackCnt > 0 && title.isNotEmpty()) {
                val genre = Genre(id = 0, title = title, trackCnt = trackCnt, albumArt = "")
                val genreId = genre.hashCode().toLong()
                genre.id = genreId
                tracksInGenre.onEach { it.genreId = genreId }
                genres.add(genre)
            }
        }

        return genres
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
        context.audioHelper.deleteTracksByGuid(guidsToDelete)
        android.util.Log.e("SimpleMediaScanner", "✅ DELETED ${invalidTracks.size} tracks from database")
        android.util.Log.e("SimpleMediaScanner", "⚠️ CASCADE: playlist_tracks entries for these tracks were also removed!")
        newTracks.removeAll(invalidTracks.toSet())
        
        android.util.Log.d("SimpleMediaScanner", "=== cleanupDatabase END ===")
        

        // remove invalid albums
        val newAlbumIds = newAlbums.map { it.id }
        val invalidAlbums = context.audioHelper.getAllAlbums().filter { it.id !in newAlbumIds }.toMutableList()
        invalidAlbums += newAlbums.filter { album -> newTracks.none { it.albumId == album.id } }
        context.audioHelper.deleteAlbums(invalidAlbums)
        newAlbums.removeAll(invalidAlbums.toSet())

        // remove invalid artists
        val newArtistIds = newArtists.map { it.id }
        val invalidArtists = context.audioHelper.getAllArtists().filter { it.id !in newArtistIds }.toMutableList()
        for (artist in newArtists) {
            val artistId = artist.id
            val albumsByArtist = newAlbums.filter { it.artistId == artistId }
            if (albumsByArtist.isEmpty()) {
                invalidArtists.add(artist)
                continue
            }

            // update album, track counts
            val albumCnt = albumsByArtist.size
            val trackCnt = albumsByArtist.sumOf { it.trackCnt }
            if (trackCnt != artist.trackCnt || albumCnt != artist.albumCnt) {
                context.audioHelper.deleteArtist(artistId)
                val updated = artist.copy(trackCnt = trackCnt, albumCnt = albumCnt)
                context.audioHelper.insertArtists(listOf(updated))
            }
        }

        context.audioHelper.deleteArtists(invalidArtists)

        // remove invalid genres
        val newGenreIds = newGenres.map { it.id }
        val invalidGenres = context.audioHelper.getAllGenres().filter { it.id !in newGenreIds }.toMutableList()
        invalidGenres += newGenres.filter { genre -> newTracks.none { it.genreId == genre.id } }
        context.audioHelper.deleteGenres(invalidGenres)
    }

    private fun maybeShowScanProgress(pathBeingScanned: String = "", progress: Int = 0, max: Int = 0) {
        if (!showProgress) {
            return
        }

        if (notificationHandler == null) {
            notificationHandler = Handler(Looper.getMainLooper())
        }

        if (notificationHelper == null) {
            notificationHelper = NotificationHelper.createInstance(context)
        }

        // avoid showing notification for a short duration
        val delayNotification = pathBeingScanned.isEmpty()
        if (delayNotification) {
            notificationHandler?.postDelayed({
                val notification = notificationHelper!!.createMediaScannerNotification(pathBeingScanned, progress, max)
                notificationHelper!!.notify(SCANNER_NOTIFICATION_ID, notification)
            }, SCANNER_NOTIFICATION_DELAY)
        } else {
            if (System.currentTimeMillis() - lastProgressUpdateMs > 100L) {
                lastProgressUpdateMs = System.currentTimeMillis()
                val notification = notificationHelper!!.createMediaScannerNotification(pathBeingScanned, progress, max)
                notificationHelper!!.notify(SCANNER_NOTIFICATION_ID, notification)
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
