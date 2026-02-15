package org.fossify.musicplayer.dialogs

import android.app.Activity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.*
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.DialogAddElevenlabsKeyBinding
import org.fossify.musicplayer.network.elevenlabs.ElevenLabsClient
import org.fossify.musicplayer.network.elevenlabs.Voice

class AddElevenLabsKeyDialog(
    val activity: Activity,
    val existingKey: org.fossify.musicplayer.models.ElevenLabsApiKey? = null,
    val callback: (email: String, apiKey: String, voiceId: String) -> Unit
) {
    private var voices: List<Voice> = emptyList()
    private var selectedVoiceId: String? = null
    
    init {
        val binding = DialogAddElevenlabsKeyBinding.inflate(activity.layoutInflater)
        
        // Pre-fill fields if editing existing key
        existingKey?.let { key ->
            binding.elevenlabsEmail.setText(key.email)
            binding.elevenlabsApiKey.setText(key.apiKey)
            binding.elevenlabsVoiceId.setText(key.voiceId)
            selectedVoiceId = key.voiceId
        }
        
        // Setup initial spinner with placeholder
        val initialAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, listOf(activity.getString(R.string.select_voice)))
        initialAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.elevenlabsVoiceSpinner.adapter = initialAdapter
        binding.elevenlabsVoiceSpinner.isEnabled = false
        
        // Load voices button
        binding.elevenlabsLoadVoicesButton.setOnClickListener {
            val apiKey = binding.elevenlabsApiKey.value.trim()
            if (apiKey.isEmpty()) {
                activity.toast("Please enter API key first")
                return@setOnClickListener
            }
            
            loadVoices(binding, apiKey)
        }
        
        // Spinner selection
        binding.elevenlabsVoiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (voices.isNotEmpty() && position > 0) { // Skip "Select a voice" item
                    selectedVoiceId = voices[position - 1].voice_id
                    binding.elevenlabsVoiceId.setText(selectedVoiceId)
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedVoiceId = null
            }
        }

        val dialogTitle = if (existingKey != null) R.string.edit_api_key else R.string.add_api_key
        
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, dialogTitle) { alertDialog ->
                    alertDialog.showKeyboard(binding.elevenlabsEmail)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val email = binding.elevenlabsEmail.value.trim()
                        val apiKey = binding.elevenlabsApiKey.value.trim()
                        val voiceId = selectedVoiceId ?: binding.elevenlabsVoiceId.value.trim()

                        if (email.isEmpty() || apiKey.isEmpty() || voiceId.isEmpty()) {
                            activity.toast(R.string.api_key_required)
                            return@setOnClickListener
                        }

                        callback(email, apiKey, voiceId)
                        alertDialog.dismiss()
                    }
                }
            }
    }
    
    private fun loadVoices(binding: DialogAddElevenlabsKeyBinding, apiKey: String) {
        if (activity !is androidx.lifecycle.LifecycleOwner) {
            activity.toast("Cannot load voices in this context")
            return
        }
        
        (activity as androidx.lifecycle.LifecycleOwner).lifecycleScope.launch {
            try {
                activity.toast(R.string.loading_voices)
                binding.elevenlabsLoadVoicesButton.isEnabled = false
                
                withContext(Dispatchers.IO) {
                    // 1. Get available voices from /voices endpoint
                    val voicesResponse = ElevenLabsClient.api.getVoices(apiKey)
                    val availableVoices = if (voicesResponse.isSuccessful && voicesResponse.body() != null) {
                        voicesResponse.body()!!.voices
                    } else {
                        emptyList()
                    }
                    
                    // 2. Get recently used voices from /history endpoint (last 10 items only)
                    val historyResponse = ElevenLabsClient.api.getHistory(
                        apiKey = apiKey,
                        pageSize = 10
                    )
                    val historyVoices = if (historyResponse.isSuccessful && historyResponse.body() != null) {
                        historyResponse.body()!!.history
                            .map { 
                                Voice(
                                    voice_id = it.voice_id,
                                    name = it.voice_name,
                                    category = "recently_used"
                                )
                            }
                            .distinctBy { it.voice_id } // Deduplicate by voice_id
                    } else {
                        emptyList()
                    }
                    
                    // 3. Merge: available voices + history-only voices (remove duplicates)
                    val allVoiceIds = availableVoices.map { it.voice_id }.toSet()
                    val historyOnlyVoices = historyVoices.filter { it.voice_id !in allVoiceIds }
                    
                    // 4. Sort alphabetically by name (case-insensitive)
                    voices = (availableVoices + historyOnlyVoices).sortedBy { it.name.lowercase() }
                }
                
                if (voices.isNotEmpty()) {
                    // Create spinner items with voice names
                    val voiceNames = listOf(activity.getString(R.string.select_voice)) + voices.map { 
                        "${it.name} (${it.voice_id.take(8)}...)" 
                    }
                    val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, voiceNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    
                    binding.elevenlabsVoiceSpinner.adapter = adapter
                    binding.elevenlabsVoiceSpinner.isEnabled = true
                    
                    activity.toast(activity.getString(R.string.voices_loaded, voices.size))
                } else {
                    activity.toast("No voices found")
                }
            } catch (e: Exception) {
                activity.toast(activity.getString(R.string.error_loading_voices, e.message ?: "Unknown error"))
            } finally {
                binding.elevenlabsLoadVoicesButton.isEnabled = true
            }
        }
    }
}
