package org.fossify.musicplayer.activities

import android.content.ContentUris
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.musicplayer.R
import org.fossify.musicplayer.adapters.GeneratedAudioAdapter
import org.fossify.musicplayer.databinding.ActivityElevenlabsHistoryBinding
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.getMediaStoreIdFromPath
import org.fossify.musicplayer.helpers.AudioGenerationHelper
import org.fossify.musicplayer.helpers.AudioHelper
import org.fossify.musicplayer.helpers.TrackFactory
import org.fossify.musicplayer.models.Events
import org.greenrobot.eventbus.EventBus
import java.io.File

class ElevenLabsHistoryActivity : SimpleControllerActivity() {

    private lateinit var binding: ActivityElevenlabsHistoryBinding
    private lateinit var audioHelper: AudioGenerationHelper
    private lateinit var adapter: GeneratedAudioAdapter
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityElevenlabsHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioHelper = AudioGenerationHelper(this)

        setupToolbar()
        setupUI()
        loadHistory()
    }

    private fun setupToolbar() {
        binding.elevenlabsHistoryToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupUI() {
        // Setup RecyclerView
        adapter = GeneratedAudioAdapter(
            onPlayClick = { file -> playAudio(file) },
            onSaveClick = { file, position -> saveToLibrary(file, position) },
            onSaveAndQueueClick = { file, position -> saveToLibraryAndQueue(file, position) },
            onDeleteClick = { file, position -> deleteAudio(file, position) }
        )
        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = adapter
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                // 1. Clean up files older than 3 days
                val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 3600 * 1000L)
                val allFiles = audioHelper.getTempFiles()
                
                allFiles.forEach { file ->
                    if (file.lastModified() < threeDaysAgo) {
                        Log.d("ElevenLabsHistory", "Deleting old file (>3 days): ${file.name}")
                        file.delete()
                    }
                }
                
                // 2. Get remaining files and sort by newest first
                audioHelper.getTempFiles()
                    .sortedByDescending { it.lastModified() }
            }
            
            if (files.isEmpty()) {
                binding.emptyText.visibility = View.VISIBLE
                binding.historyList.visibility = View.GONE
            } else {
                binding.emptyText.visibility = View.GONE
                binding.historyList.visibility = View.VISIBLE
                adapter.updateFiles(files)
            }
        }
    }

    private fun playAudio(file: File) {
        try {
            // Release previous player
            mediaPlayer?.release()

            // Create new player
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                }
            }
            
            toast("Playing: ${file.name}")
        } catch (e: Exception) {
            toast("Error playing audio: ${e.message}")
        }
    }

    private fun saveToLibrary(file: File, position: Int) {
        lifecycleScope.launch {
            try {
                Log.d("ElevenLabsHistory", "=== SAVE START ===")
                Log.d("ElevenLabsHistory", "Source file: ${file.absolutePath}, exists: ${file.exists()}, size: ${file.length()}")
                
                // 1. Get target directory: /storage/emulated/0/Music/org.fossify.musicplayer/Music/
                val mediaDir = File(android.os.Environment.getExternalStorageDirectory(), "Music/${packageName}")
                val targetDir = File(mediaDir, "Music")
                Log.d("ElevenLabsHistory", "Target dir: ${targetDir.absolutePath}")
                
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                    Log.d("ElevenLabsHistory", "Created target directory: ${targetDir.exists()}")
                }
                
                // 2. Move file (copy + delete)
                val destFile = File(targetDir, file.name)
                Log.d("ElevenLabsHistory", "Destination: ${destFile.absolutePath}")
                
                withContext(Dispatchers.IO) {
                    Log.d("ElevenLabsHistory", "Starting copy...")
                    file.copyTo(destFile, overwrite = true)
                    Log.d("ElevenLabsHistory", "Copy completed, destFile exists: ${destFile.exists()}, size: ${destFile.length()}")
                    
                    Log.d("ElevenLabsHistory", "Deleting original...")
                    val deleted = file.delete()
                    Log.d("ElevenLabsHistory", "Delete result: $deleted")
                }
                
                Log.d("ElevenLabsHistory", "File moved to: ${destFile.absolutePath}")
                
                // 3. Request MediaStore scan
                Log.d("ElevenLabsHistory", "Requesting MediaStore scan for: ${destFile.absolutePath}")
                MediaScannerConnection.scanFile(
                    this@ElevenLabsHistoryActivity,
                    arrayOf(destFile.absolutePath),
                    null
                ) { scannedPath, uri ->
                    Log.d("ElevenLabsHistory", "=== MediaScannerConnection CALLBACK ===")
                    Log.d("ElevenLabsHistory", "URI: $uri")
                    
                    if (uri != null) {
                        val mediaStoreId = ContentUris.parseId(uri)
                        Log.d("ElevenLabsHistory", "✅ MediaStore ID: $mediaStoreId")
                    }
                    
                    // Read tags AFTER scan
                    val tagsAfterScan = org.fossify.musicplayer.helpers.TXXXTagsReader.readAllTags(destFile)
                    
                    // Add to database if successfully indexed
                    if (uri != null && tagsAfterScan != null) {
                        try {
                            val mediaStoreId = ContentUris.parseId(uri)
                            Log.d("ElevenLabsHistory", "=== ADDING TO DATABASE ===")
                            
                            // Create Track from file
                            val trackFactory = org.fossify.musicplayer.helpers.TrackFactory(this@ElevenLabsHistoryActivity)
                            val track = trackFactory.createFromMediaStoreId(mediaStoreId, tagsAfterScan)
                            
                            if (track != null) {
                                // Insert into database
                                val dbHelper = AudioHelper(this@ElevenLabsHistoryActivity)
                                dbHelper.insertTracks(listOf(track))
                                Log.d("ElevenLabsHistory", "✅ Track added to database: ${track.title}")
                                
                                // Refresh UI lists
                                EventBus.getDefault().post(org.fossify.musicplayer.models.Events.RefreshTracks())
                                Log.d("ElevenLabsHistory", "✅ Sent RefreshTracks event")
                                
                                runOnUiThread {
                                    toast("File saved and added to library!")
                                    // Remove from history list
                                    adapter.removeFile(position)
                                    
                                    // Show empty state if no more files
                                    if (adapter.itemCount == 0) {
                                        binding.emptyText.visibility = View.VISIBLE
                                        binding.historyList.visibility = View.GONE
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ElevenLabsHistory", "Error adding to database: ${e.message}", e)
                        }
                    }
                    
                    Log.d("ElevenLabsHistory", "=== END CALLBACK ===")
                }
                
                Log.d("ElevenLabsHistory", "=== SAVE END (waiting for callback) ===")
                
            } catch (e: Exception) {
                val errorMsg = getString(R.string.file_save_error, e.message ?: "Unknown error")
                toast(errorMsg)
                Log.e("ElevenLabsHistory", "Error moving file: ${e.message}", e)
            }
        }
    }
    
    private fun saveToLibraryAndQueue(file: File, position: Int) {
        lifecycleScope.launch {
            try {
                Log.d("ElevenLabsHistory", "=== SAVE & ADD TO QUEUE START ===")
                Log.d("ElevenLabsHistory", "Source file: ${file.absolutePath}, exists: ${file.exists()}, size: ${file.length()}")
                
                // 1. Get target directory: /storage/emulated/0/Music/org.fossify.musicplayer/Music/
                val mediaDir = File(android.os.Environment.getExternalStorageDirectory(), "Music/${packageName}")
                val targetDir = File(mediaDir, "Music")
                Log.d("ElevenLabsHistory", "Target dir: ${targetDir.absolutePath}")
                
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                    Log.d("ElevenLabsHistory", "Created target directory: ${targetDir.exists()}")
                }
                
                // 2. Move file (copy + delete)
                val destFile = File(targetDir, file.name)
                Log.d("ElevenLabsHistory", "Destination: ${destFile.absolutePath}")
                
                withContext(Dispatchers.IO) {
                    Log.d("ElevenLabsHistory", "Starting copy...")
                    file.copyTo(destFile, overwrite = true)
                    Log.d("ElevenLabsHistory", "Copy completed, destFile exists: ${destFile.exists()}, size: ${destFile.length()}")
                    
                    Log.d("ElevenLabsHistory", "Deleting original...")
                    val deleted = file.delete()
                    Log.d("ElevenLabsHistory", "Delete result: $deleted")
                }
                
                Log.d("ElevenLabsHistory", "File moved to: ${destFile.absolutePath}")
                
                // 3. Request MediaStore scan
                Log.d("ElevenLabsHistory", "Requesting MediaStore scan for: ${destFile.absolutePath}")
                MediaScannerConnection.scanFile(
                    this@ElevenLabsHistoryActivity,
                    arrayOf(destFile.absolutePath),
                    null
                ) { scannedPath, uri ->
                    Log.d("ElevenLabsHistory", "=== MediaScannerConnection CALLBACK (Queue) ===")
                    Log.d("ElevenLabsHistory", "URI: $uri")
                    
                    if (uri != null) {
                        val mediaStoreId = ContentUris.parseId(uri)
                        Log.d("ElevenLabsHistory", "✅ MediaStore ID: $mediaStoreId")
                    }
                    
                    // Read tags AFTER scan
                    val tagsAfterScan = org.fossify.musicplayer.helpers.TXXXTagsReader.readAllTags(destFile)
                    
                    // Add to database and queue if successfully indexed
                    if (uri != null && tagsAfterScan != null) {
                        try {
                            val mediaStoreId = ContentUris.parseId(uri)
                            Log.d("ElevenLabsHistory", "=== ADDING TO DATABASE & QUEUE ===")
                            
                            // Create Track from file
                            val trackFactory = org.fossify.musicplayer.helpers.TrackFactory(this@ElevenLabsHistoryActivity)
                            val track = trackFactory.createFromMediaStoreId(mediaStoreId, tagsAfterScan)
                            
                            if (track != null) {
                                // Insert into database
                                val dbHelper = AudioHelper(this@ElevenLabsHistoryActivity)
                                dbHelper.insertTracks(listOf(track))
                                Log.d("ElevenLabsHistory", "✅ Track added to database: ${track.title}")
                                
                                // Add to playback queue
                                addTracksToQueue(listOf(track)) {
                                    Log.d("ElevenLabsHistory", "✅ Track added to queue")
                                }
                                
                                // Refresh UI lists
                                EventBus.getDefault().post(org.fossify.musicplayer.models.Events.RefreshTracks())
                                Log.d("ElevenLabsHistory", "✅ Sent RefreshTracks event")
                                
                                runOnUiThread {
                                    toast("File saved and added to queue!")
                                    // Remove from history list
                                    adapter.removeFile(position)
                                    
                                    // Show empty state if no more files
                                    if (adapter.itemCount == 0) {
                                        binding.emptyText.visibility = View.VISIBLE
                                        binding.historyList.visibility = View.GONE
                                    }
                                }
                            } else {
                                Log.e("ElevenLabsHistory", "❌ Failed to create Track from mediaStoreId")
                            }
                        } catch (e: Exception) {
                            Log.e("ElevenLabsHistory", "Error adding to database/queue: ${e.message}", e)
                        }
                    }
                    
                    Log.d("ElevenLabsHistory", "=== END CALLBACK (Queue) ===")
                }
                
                Log.d("ElevenLabsHistory", "=== SAVE & ADD TO QUEUE END (waiting for callback) ===")
                
            } catch (e: Exception) {
                val errorMsg = getString(R.string.file_save_error, e.message ?: "Unknown error")
                toast(errorMsg)
                Log.e("ElevenLabsHistory", "Error moving file (queue): ${e.message}", e)
            }
        }
    }
    
    private fun deleteAudio(file: File, position: Int) {
        try {
            if (file.exists()) {
                file.delete()
            }
            adapter.removeFile(position)
            toast(R.string.file_deleted)
            
            // Show empty state if no more files
            if (adapter.itemCount == 0) {
                binding.emptyText.visibility = View.VISIBLE
                binding.historyList.visibility = View.GONE
            }
        } catch (e: Exception) {
            toast("Error deleting file: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
