package org.fossify.musicplayer.activities

import android.content.ContentUris
import android.content.Intent
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
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.databinding.ActivityElevenlabsTestBinding
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.getMediaStoreIdFromPath
import org.fossify.musicplayer.helpers.AudioGenerationHelper
import org.fossify.musicplayer.helpers.AudioHelper
import org.fossify.musicplayer.helpers.TrackFactory
import org.fossify.musicplayer.models.AudioGenerationResult
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.models.GeneratedAudio
import org.greenrobot.eventbus.EventBus
import java.io.File

class ElevenLabsTestActivity : SimpleControllerActivity() {

    private lateinit var binding: ActivityElevenlabsTestBinding
    private lateinit var audioHelper: AudioGenerationHelper
    private lateinit var adapter: GeneratedAudioAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var currentVariantCount = 1  // TODO: Change back to 3 after testing
    
    // Current generation state
    private var currentFiles: List<File> = emptyList()
    private var currentGeneratedAudios: List<GeneratedAudio> = emptyList()
    private var lastGenerationText: String = ""
    private var lastVoiceId: String? = null
    
    companion object {
        private const val PREFS_NAME = "elevenlabs_test"
        private const val PREF_LAST_TEXT = "last_text"
        private const val PREF_LAST_VOICE_ID = "last_voice_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityElevenlabsTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioHelper = AudioGenerationHelper(this)

        setupToolbar()
        setupUI()
        loadLastGenerationState()
        loadExistingAudios()
        checkApiKeyStatus()
    }

    private fun setupToolbar() {
        binding.elevenlabsTestToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun checkApiKeyStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = SongsDatabase.getInstance(this@ElevenLabsTestActivity)
            val activeKey = db.ElevenLabsApiKeyDao().getActive()
            
            withContext(Dispatchers.Main) {
                if (activeKey != null) {
                    Log.d("ElevenLabsTest", "Active API key found: ${activeKey.email}")
                    showSuccess("API Key configured: ${activeKey.email}")
                    refreshCharacterLimits()
                } else {
                    Log.d("ElevenLabsTest", "No active API key found")
                    showError("No active API key. Please configure one in Settings → ElevenLabs API Keys and tap on it to activate.")
                }
            }
        }
    }

    private fun setupUI() {
        // Setup variant slider
        binding.variantSlider.addOnChangeListener { _, value, _ ->
            currentVariantCount = value.toInt()
            binding.variantCountText.text = "$currentVariantCount ${if (currentVariantCount == 1) "variant" else "variants"}"
        }

        // Setup restore from history button
        binding.restoreHistoryButton.setOnClickListener {
            restoreHistoryFromToday()
        }

        // Setup generate button
        binding.generateButton.setOnClickListener {
            val text = binding.textInput.text?.toString()?.trim()
            if (text.isNullOrEmpty()) {
                showError(getString(R.string.enter_text_first))
                return@setOnClickListener
            }
            val voiceId = binding.voiceIdInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            generateAudio(text, currentVariantCount, voiceId, isRegenerate = false)
        }

        // Setup history button
        binding.historyButton.setOnClickListener {
            val intent = Intent(this, ElevenLabsHistoryActivity::class.java)
            startActivity(intent)
        }

        // Setup regenerate button
        binding.regenerateButton.setOnClickListener {
            if (lastGenerationText.isNotEmpty()) {
                generateAudio(lastGenerationText, currentVariantCount, lastVoiceId, isRegenerate = true)
            }
        }
        
        // Setup play all button
        binding.playAllButton.setOnClickListener {
            playAllSequentially()
        }

        // Setup RecyclerView
        adapter = GeneratedAudioAdapter(
            onPlayClick = { file -> playAudio(file) },
            onSaveClick = { file, position -> saveToLibrary(file, position) },
            onSaveAndQueueClick = { file, position -> saveToLibraryAndQueue(file, position) },
            onDeleteClick = { file, position -> deleteAudio(file, position) }
        )
        binding.audioList.layoutManager = LinearLayoutManager(this)
        binding.audioList.adapter = adapter
    }

    private fun loadExistingAudios() {
        // Don't load existing files at startup - only show current generation
        // Users can view all files via History button
    }

    private fun generateAudio(text: String, variantCount: Int, voiceId: String? = null, isRegenerate: Boolean = false) {
        lifecycleScope.launch {
            // Check if service is configured
            val isConfigured = withContext(Dispatchers.IO) {
                audioHelper.isConfigured()
            }
            
            if (!isConfigured) {
                showError("No active API key. Please:\n1. Go to Settings → ElevenLabs API Keys\n2. Add an API key\n3. Tap on it to activate (radio button should be checked)")
                return@launch
            }
            try {
                showLoading(true)
                
                // For regenerate, use previous request IDs
                // Note: We don't use previousRequestIds for regeneration anymore
                // Each generation (initial or regenerate) creates new independent variants
                val logMsg = if (isRegenerate) {
                    "Regenerating audio for: $text (variants: $variantCount, voice: ${voiceId ?: "default"}) - new independent variants"
                } else {
                    "Generating audio for: $text (variants: $variantCount, voice: ${voiceId ?: "default"})"
                }
                Log.d("ElevenLabsTest", logMsg)

                val result = withContext(Dispatchers.IO) {
                    audioHelper.generateAndSaveAudio(
                        text, 
                        voiceId = voiceId, 
                        variantCount = variantCount,
                        previousRequestIds = null  // Always null - we want independent variants
                    )
                }

                when (result) {
                    is AudioGenerationResult.Success -> {
                        // Delete old current files if regenerating
                        if (isRegenerate) {
                            currentFiles.forEach { it.delete() }
                        }
                        
                        // Update current state
                        currentGeneratedAudios = result.audios
                        currentFiles = result.audios.mapNotNull { it.localFile }
                        lastGenerationText = text
                        lastVoiceId = voiceId
                        
                        // Save state to SharedPreferences for persistence
                        saveLastGenerationState(text, voiceId)
                        
                        showSuccess(getString(R.string.audio_generated_success, currentFiles.size))
                        refreshCharacterLimits()  // Update character limits after generation
                        showAudioList(currentFiles)
                        
                        // Show regenerate button
                        binding.regenerateButton.visibility = View.VISIBLE
                        
                        // Keep text in input field for regeneration and persistence
                        // Don't clear the text anymore - it will be saved and restored
                        
                        Log.d("ElevenLabsTest", "Generated ${currentFiles.size} files successfully")
                    }
                    is AudioGenerationResult.Error -> {
                        val errorMsg = getString(R.string.audio_generation_error, result.message)
                        showError(errorMsg)
                        Log.e("ElevenLabsTest", "Error: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                val errorMsg = getString(R.string.audio_generation_error, e.message ?: "Unknown error")
                showError(errorMsg)
                Log.e("ElevenLabsTest", "Exception: ${e.message}", e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun restoreHistoryFromToday() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                showStatus("Fetching history from ElevenLabs...", false)
                
                val restoredCount = withContext(Dispatchers.IO) {
                    // Get active API key
                    val db = SongsDatabase.getInstance(this@ElevenLabsTestActivity)
                    val activeKey = db.ElevenLabsApiKeyDao().getActive()
                    if (activeKey == null) {
                        withContext(Dispatchers.Main) {
                            showError("No active API key configured")
                        }
                        return@withContext 0
                    }
                    
                    // Calculate today's start timestamp (00:00:00 UTC)
                    val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    val todayStartUnix = calendar.timeInMillis / 1000
                    
                    Log.d("ElevenLabsTest", "Fetching history from $todayStartUnix")
                    
                    // Fetch history from ElevenLabs API
                    val api = org.fossify.musicplayer.network.elevenlabs.ElevenLabsClient.api
                    val response = api.getHistory(
                        apiKey = activeKey.apiKey,
                        dateAfterUnix = todayStartUnix,
                        pageSize = 100
                    )
                    
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            showError("Failed to fetch history: ${response.code()}")
                        }
                        return@withContext 0
                    }
                    
                    val historyResponse = response.body()
                    if (historyResponse == null || historyResponse.history.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            showStatus("No files found in today's history", false)
                        }
                        return@withContext 0
                    }
                    
                    Log.d("ElevenLabsTest", "Found ${historyResponse.history.size} items in history")
                    
                    // Download and save each audio file
                    val tagsWriter = org.fossify.musicplayer.helpers.TXXXTagsWriter(this@ElevenLabsTestActivity)
                    val tempDir = File(this@ElevenLabsTestActivity.cacheDir, "elevenLabs_temp").apply {
                        if (!exists()) mkdirs()
                    }
                    var restoredCount = 0
                    
                    for (item in historyResponse.history) {
                        try {
                            // Download audio
                            val audioResponse = api.getHistoryAudio(item.history_item_id, activeKey.apiKey)
                            if (!audioResponse.isSuccessful) {
                                Log.w("ElevenLabsTest", "Failed to download ${item.history_item_id}")
                                continue
                            }
                            
                            val audioBytes = audioResponse.body()?.bytes()
                            if (audioBytes == null) {
                                Log.w("ElevenLabsTest", "Empty audio for ${item.history_item_id}")
                                continue
                            }
                            
                            // Build filename using timestamp from ElevenLabs
                            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH_mm_ss", java.util.Locale.getDefault())
                                .format(java.util.Date(item.date_unix * 1000))
                            val stability = ((item.settings?.stability ?: 0.5) * 100).toInt()
                            val similarityBoost = ((item.settings?.similarity_boost ?: 0.75) * 100).toInt()
                            val style = ((item.settings?.style ?: 0.0) * 100).toInt()
                            val speakerBoost = if (item.settings?.use_speaker_boost == true) "b" else ""
                            val model = "m2"
                            
                            val filename = "ElevenLabs_${timestamp}_${item.voice_name}_pre_s${stability}_sb${similarityBoost}_se${style}_${speakerBoost}_${model}.mp3"
                            val file = File(tempDir, filename)
                            
                            // 1. Save audio data
                            file.writeBytes(audioBytes)
                            Log.d("ElevenLabsTest", "Saved audio to: ${file.name}")
                            
                            // 2. Write TXXX tags ONCE (this will also set file.lastModified)
                            val success = tagsWriter.writeElevenLabsTags(
                                file = file,
                                text = item.text,
                                voiceId = item.voice_id,
                                model = item.model_id,
                                stability = item.settings?.stability?.toFloat() ?: 0.5f,
                                similarityBoost = item.settings?.similarity_boost?.toFloat() ?: 0.75f,
                                style = item.settings?.style?.toFloat() ?: 0.0f,
                                speakerBoost = item.settings?.use_speaker_boost ?: true,
                                voice = item.voice_name,
                                timestampUnixSec = item.date_unix  // Use ElevenLabs timestamp
                            )
                            
                            if (success) {
                                restoredCount++
                                Log.d("ElevenLabsTest", "Restored ${item.history_item_id}: ${item.text.take(30)}... with TXXX tags")
                            } else {
                                Log.w("ElevenLabsTest", "Restored ${item.history_item_id} but failed to write tags")
                            }
                            
                        } catch (e: Exception) {
                            Log.e("ElevenLabsTest", "Failed to restore ${item.history_item_id}", e)
                        }
                    }
                    
                    restoredCount
                }
                
                if (restoredCount > 0) {
                    showSuccess("Restored $restoredCount files from history")
                    // Open history activity to show restored files
                    val intent = Intent(this@ElevenLabsTestActivity, ElevenLabsHistoryActivity::class.java)
                    startActivity(intent)
                } else {
                    showStatus("No files were restored", false)
                }
                
            } catch (e: Exception) {
                showError("Error restoring history: ${e.message}")
                Log.e("ElevenLabsTest", "Error restoring history", e)
            } finally {
                showLoading(false)
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
                    adapter.setActiveFile(null)
                }
            }
            
            // Highlight active file in adapter
            adapter.setActiveFile(file)
            
            toast("Playing: ${file.name}")
        } catch (e: Exception) {
            showError("Error playing audio: ${e.message}")
            adapter.setActiveFile(null)
        }
    }
    
    private fun playAllSequentially() {
        val files = adapter.getFiles()
        if (files.isEmpty()) {
            toast("No files to play")
            return
        }
        
        var currentIndex = 0
        
        fun playNext() {
            if (currentIndex >= files.size) {
                // All files played
                adapter.setActiveFile(null)
                toast("All files played")
                return
            }
            
            val file = files[currentIndex]
            currentIndex++
            
            try {
                // Release previous player
                mediaPlayer?.release()
                
                // Create new player
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                    
                    // Highlight current file
                    adapter.setActiveFile(file)
                    
                    setOnCompletionListener {
                        it.release()
                        mediaPlayer = null
                        // Play next file
                        playNext()
                    }
                }
                
                Log.d("ElevenLabsTest", "Playing $currentIndex/${files.size}: ${file.name}")
            } catch (e: Exception) {
                Log.e("ElevenLabsTest", "Error playing file: ${file.name}", e)
                // Try next file
                playNext()
            }
        }
        
        playNext()
    }

    private fun saveToLibrary(file: File, position: Int) {
        lifecycleScope.launch {
            try {
                // Clear current generation immediately
                clearCurrentGeneration()
                
                Log.d("ElevenLabsTest", "=== SAVE START ===")
                Log.d("ElevenLabsTest", "Source file: ${file.absolutePath}, exists: ${file.exists()}, size: ${file.length()}")
                
                // 1. Get target directory: /storage/emulated/0/Music/org.fossify.musicplayer/Music/
                val mediaDir = File(android.os.Environment.getExternalStorageDirectory(), "Music/${packageName}")
                val targetDir = File(mediaDir, "Music")
                Log.d("ElevenLabsTest", "Target dir: ${targetDir.absolutePath}")
                
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                    Log.d("ElevenLabsTest", "Created target directory: ${targetDir.exists()}")
                }
                
                // 2. Move file (copy + delete)
                val destFile = File(targetDir, file.name)
                Log.d("ElevenLabsTest", "Destination: ${destFile.absolutePath}")
                
                withContext(Dispatchers.IO) {
                    // Read tags BEFORE copy
                    Log.d("ElevenLabsTest", "=== READING TAGS BEFORE COPY ===")
                    val tagsReader = org.fossify.musicplayer.helpers.TXXXTagsReader
                    val tagsBeforeCopy = tagsReader.readAllTags(file)
                    if (tagsBeforeCopy != null) {
                        Log.d("ElevenLabsTest", "BEFORE: Transcription: ${tagsBeforeCopy.transcription}")
                        Log.d("ElevenLabsTest", "BEFORE: GUID: ${tagsBeforeCopy.guid}")
                    }
                    
                    Log.d("ElevenLabsTest", "Starting copy...")
                    file.copyTo(destFile, overwrite = true)
                    Log.d("ElevenLabsTest", "Copy completed, destFile exists: ${destFile.exists()}, size: ${destFile.length()}")
                    
                    Log.d("ElevenLabsTest", "Deleting original...")
                    val deleted = file.delete()
                    Log.d("ElevenLabsTest", "Delete result: $deleted")
                }
                
                Log.d("ElevenLabsTest", "File moved to: ${destFile.absolutePath}")
                
                // 3. Request MediaStore scan
                Log.d("ElevenLabsTest", "Requesting MediaStore scan for: ${destFile.absolutePath}")
                MediaScannerConnection.scanFile(
                    this@ElevenLabsTestActivity,
                    arrayOf(destFile.absolutePath),
                    null
                ) { scannedPath, uri ->
                    Log.d("ElevenLabsTest", "=== MediaScannerConnection CALLBACK ===")
                    Log.d("ElevenLabsTest", "Scanned path: $scannedPath")
                    Log.d("ElevenLabsTest", "URI: $uri")
                    
                    if (uri != null) {
                        val mediaStoreId = ContentUris.parseId(uri)
                        Log.d("ElevenLabsTest", "✅ MediaStore ID: $mediaStoreId")
                    }
                    
                    // Read tags AFTER scan
                    val tagsAfterScan = org.fossify.musicplayer.helpers.TXXXTagsReader.readAllTags(destFile)
                    
                    // Add to database if successfully indexed
                    if (uri != null && tagsAfterScan != null) {
                        try {
                            val mediaStoreId = ContentUris.parseId(uri)
                            Log.d("ElevenLabsTest", "=== ADDING TO DATABASE ===")
                            
                            // Create Track from file
                            val trackFactory = org.fossify.musicplayer.helpers.TrackFactory(this@ElevenLabsTestActivity)
                            val track = trackFactory.createFromMediaStoreId(mediaStoreId, tagsAfterScan)
                            
                            if (track != null) {
                                // Insert into database
                                val dbHelper = AudioHelper(this@ElevenLabsTestActivity)
                                dbHelper.insertTracks(listOf(track))
                                Log.d("ElevenLabsTest", "✅ Track added to database: ${track.title}")
                                
                                // Refresh UI lists
                                EventBus.getDefault().post(org.fossify.musicplayer.models.Events.RefreshTracks())
                                Log.d("ElevenLabsTest", "✅ Sent RefreshTracks event")
                                
                                runOnUiThread {
                                    toast("File saved and added to library!")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ElevenLabsTest", "Error adding to database: ${e.message}", e)
                        }
                    }
                    
                    Log.d("ElevenLabsTest", "=== END CALLBACK ===")
                }
                
                Log.d("ElevenLabsTest", "=== SAVE END (waiting for callback) ===")
                
            } catch (e: Exception) {
                val errorMsg = getString(R.string.file_save_error, e.message ?: "Unknown error")
                showError(errorMsg)
                Log.e("ElevenLabsTest", "Error moving file: ${e.message}", e)
            }
        }
    }
    
    private fun saveToLibraryAndQueue(file: File, position: Int) {
        lifecycleScope.launch {
            try {
                // Clear current generation immediately
                clearCurrentGeneration()
                
                Log.d("ElevenLabsTest", "=== SAVE & ADD TO QUEUE START ===")
                Log.d("ElevenLabsTest", "Source file: ${file.absolutePath}, exists: ${file.exists()}, size: ${file.length()}")
                
                // 1. Get target directory: /storage/emulated/0/Music/org.fossify.musicplayer/Music/
                val mediaDir = File(android.os.Environment.getExternalStorageDirectory(), "Music/${packageName}")
                val targetDir = File(mediaDir, "Music")
                Log.d("ElevenLabsTest", "Target dir: ${targetDir.absolutePath}")
                
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                    Log.d("ElevenLabsTest", "Created target directory: ${targetDir.exists()}")
                }
                
                // 2. Move file (copy + delete)
                val destFile = File(targetDir, file.name)
                Log.d("ElevenLabsTest", "Destination: ${destFile.absolutePath}")
                
                withContext(Dispatchers.IO) {
                    Log.d("ElevenLabsTest", "Starting copy...")
                    file.copyTo(destFile, overwrite = true)
                    Log.d("ElevenLabsTest", "Copy completed, destFile exists: ${destFile.exists()}, size: ${destFile.length()}")
                    
                    Log.d("ElevenLabsTest", "Deleting original...")
                    val deleted = file.delete()
                    Log.d("ElevenLabsTest", "Delete result: $deleted")
                }
                
                Log.d("ElevenLabsTest", "File moved to: ${destFile.absolutePath}")
                
                // 3. Request MediaStore scan
                Log.d("ElevenLabsTest", "Requesting MediaStore scan for: ${destFile.absolutePath}")
                MediaScannerConnection.scanFile(
                    this@ElevenLabsTestActivity,
                    arrayOf(destFile.absolutePath),
                    null
                ) { scannedPath, uri ->
                    Log.d("ElevenLabsTest", "=== MediaScannerConnection CALLBACK (Queue) ===")
                    Log.d("ElevenLabsTest", "Scanned path: $scannedPath")
                    Log.d("ElevenLabsTest", "URI: $uri")
                    
                    if (uri != null) {
                        val mediaStoreId = ContentUris.parseId(uri)
                        Log.d("ElevenLabsTest", "✅ MediaStore ID: $mediaStoreId")
                    }
                    
                    // Read tags AFTER scan
                    val tagsAfterScan = org.fossify.musicplayer.helpers.TXXXTagsReader.readAllTags(destFile)
                    
                    // Add to database and queue if successfully indexed
                    if (uri != null && tagsAfterScan != null) {
                        try {
                            val mediaStoreId = ContentUris.parseId(uri)
                            Log.d("ElevenLabsTest", "=== ADDING TO DATABASE & QUEUE ===")
                            
                            // Create Track from file
                            val trackFactory = org.fossify.musicplayer.helpers.TrackFactory(this@ElevenLabsTestActivity)
                            val track = trackFactory.createFromMediaStoreId(mediaStoreId, tagsAfterScan)
                            
                            if (track != null) {
                                // Insert into database
                                val dbHelper = AudioHelper(this@ElevenLabsTestActivity)
                                dbHelper.insertTracks(listOf(track))
                                Log.d("ElevenLabsTest", "✅ Track added to database: ${track.title}")
                                
                                // Add to playback queue
                                addTracksToQueue(listOf(track)) {
                                    Log.d("ElevenLabsTest", "✅ Track added to queue")
                                }
                                
                                // Refresh UI lists
                                EventBus.getDefault().post(org.fossify.musicplayer.models.Events.RefreshTracks())
                                Log.d("ElevenLabsTest", "✅ Sent RefreshTracks event")
                                
                                runOnUiThread {
                                    toast("File saved and added to queue!")
                                }
                            } else {
                                Log.e("ElevenLabsTest", "❌ Failed to create Track from mediaStoreId")
                            }
                        } catch (e: Exception) {
                            Log.e("ElevenLabsTest", "Error adding to database/queue: ${e.message}", e)
                        }
                    }
                    
                    Log.d("ElevenLabsTest", "=== END CALLBACK (Queue) ===")
                }
                
                Log.d("ElevenLabsTest", "=== SAVE & ADD TO QUEUE END (waiting for callback) ===")
                
            } catch (e: Exception) {
                val errorMsg = getString(R.string.file_save_error, e.message ?: "Unknown error")
                showError(errorMsg)
                Log.e("ElevenLabsTest", "Error moving file (queue): ${e.message}", e)
            }
        }
    }
    
    private fun clearCurrentGeneration() {
        // Stop any playing audio
        mediaPlayer?.release()
        mediaPlayer = null
        
        // Clear the current generation from view (files remain in cache/history)
        currentFiles = emptyList()
        currentGeneratedAudios = emptyList()
        
        // Update UI
        adapter.updateFiles(emptyList())
        binding.audioList.visibility = View.GONE
        binding.audioListTitle.visibility = View.GONE
        binding.playAllButton.visibility = View.GONE
        binding.regenerateButton.visibility = View.GONE
        
        Log.d("ElevenLabsTest", "Current generation cleared from view")
    }
    
    private fun deleteAudio(file: File, position: Int) {
        try {
            if (file.exists()) {
                file.delete()
            }
            adapter.removeFile(position)
            toast(R.string.file_deleted)
            
            // Hide list if empty
            if (adapter.itemCount == 0) {
                binding.audioList.visibility = View.GONE
                binding.audioListTitle.visibility = View.GONE
                binding.playAllButton.visibility = View.GONE
            } else if (adapter.itemCount == 1) {
                // Hide Play All button if only 1 file left
                binding.playAllButton.visibility = View.GONE
            }
        } catch (e: Exception) {
            showError("Error deleting file: ${e.message}")
        }
    }

    private fun showAudioList(files: List<File>) {
        adapter.updateFiles(files)
        binding.audioList.visibility = View.VISIBLE
        binding.audioListTitle.visibility = View.VISIBLE
        binding.playAllButton.visibility = if (files.size > 1) View.VISIBLE else View.GONE
    }

    private fun showLoading(show: Boolean) {
        binding.progressLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.generateButton.isEnabled = !show
        binding.regenerateButton.isEnabled = !show
        binding.historyButton.isEnabled = !show
        binding.textInput.isEnabled = !show
        binding.variantSlider.isEnabled = !show
    }

    private fun refreshCharacterLimits() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = SongsDatabase.getInstance(this@ElevenLabsTestActivity)
            val activeKey = db.ElevenLabsApiKeyDao().getActive()
            
            Log.d("ElevenLabsTest", "refreshCharacterLimits: activeKey=${activeKey?.email}, limit=${activeKey?.characterLimit}, remaining=${activeKey?.characterLimitRemaining}, used=${activeKey?.characterCount}")
            
            withContext(Dispatchers.Main) {
                if (activeKey != null && activeKey.characterLimit != null) {
                    val remaining = activeKey.characterLimitRemaining ?: 0
                    val limit = activeKey.characterLimit ?: 0
                    val used = activeKey.characterCount ?: 0
                    
                    // Format reset date
                    val resetText = if (activeKey.nextCharacterCountResetUnix != null) {
                        val resetDate = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(activeKey.nextCharacterCountResetUnix!! * 1000))
                        " • Resets: $resetDate"
                    } else {
                        ""
                    }
                    
                    Log.d("ElevenLabsTest", "Showing limits card: $remaining / $limit ($used used)")
                    
                    binding.limitsCard.visibility = View.VISIBLE
                    binding.limitsText.text = "Characters: $remaining / $limit remaining ($used used)$resetText"
                    binding.limitsText.setTextColor(
                        getColor(
                            when {
                                remaining < 1000 -> android.R.color.holo_red_dark
                                remaining < 5000 -> android.R.color.holo_orange_dark
                                else -> android.R.color.holo_green_dark
                            }
                        )
                    )
                } else {
                    Log.d("ElevenLabsTest", "Hiding limits card - activeKey is null or characterLimit is null")
                    binding.limitsCard.visibility = View.GONE
                }
            }
        }
    }

    private fun showSuccess(message: String) {
        showStatus(message, isError = false)
    }

    private fun showError(message: String) {
        showStatus(message, isError = true)
    }

    private fun showStatus(message: String, isError: Boolean) {
        binding.statusCard.visibility = View.VISIBLE
        binding.statusText.text = message
        binding.statusText.setTextColor(
            getColor(if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark)
        )
    }

    private fun hideStatus() {
        binding.statusCard.visibility = View.GONE
    }
    
    private fun loadLastGenerationState() {
        Thread {
            try {
                android.util.Log.i("ElevenLabsTest", "loadLastGenerationState: Starting")
                val db = org.fossify.musicplayer.databases.SongsDatabase.getInstance(this)
                val activeKey = db.ElevenLabsApiKeyDao().getActive()
                android.util.Log.i("ElevenLabsTest", "loadLastGenerationState: activeKey=$activeKey, voiceId=${activeKey?.voiceId}")
                
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val savedText = prefs.getString(PREF_LAST_TEXT, "")
                
                // Always use voice ID from active API key
                val voiceIdToUse = activeKey?.voiceId ?: ""
                android.util.Log.i("ElevenLabsTest", "loadLastGenerationState: voiceIdToUse=$voiceIdToUse")
                
                runOnUiThread {
                    if (!savedText.isNullOrEmpty()) {
                        binding.textInput.setText(savedText)
                        lastGenerationText = savedText
                    }
                    
                    if (voiceIdToUse.isNotEmpty()) {
                        binding.voiceIdInput.setText(voiceIdToUse)
                        lastVoiceId = voiceIdToUse
                        android.util.Log.i("ElevenLabsTest", "loadLastGenerationState: Voice ID set to UI: $voiceIdToUse")
                    } else {
                        android.util.Log.w("ElevenLabsTest", "loadLastGenerationState: No active API key or voice ID")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ElevenLabsTest", "loadLastGenerationState: Error", e)
            }
        }.start()
    }
    
    private fun saveLastGenerationState(text: String, voiceId: String?) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putString(PREF_LAST_TEXT, text)
            // Voice ID is always from active API key - no need to save
            apply()
        }
    }

    override fun onPause() {
        super.onPause()
        // Save current text state when leaving activity
        val currentText = binding.textInput.text?.toString() ?: ""
        val currentVoiceId = binding.voiceIdInput.text?.toString()
        if (currentText.isNotEmpty()) {
            saveLastGenerationState(currentText, currentVoiceId)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        adapter.setActiveFile(null)
    }
}
