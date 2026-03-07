package org.fossify.musicplayer.activities

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.ActivityElevenlabsTestBinding
import org.fossify.musicplayer.di.DependencyContainer
import org.fossify.musicplayer.domain.model.TrackDomain

class AudioGenerationActivityExample : SimpleControllerActivity() {
    
    private lateinit var binding: ActivityElevenlabsTestBinding
    private lateinit var dependencies: DependencyContainer
    
    private var generatedTracks: List<TrackDomain> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityElevenlabsTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        dependencies = DependencyContainer(this)
        
        setupToolbar()
        setupUI()
    }
    
    private fun setupToolbar() {
        binding.elevenlabsTestToolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupUI() {
        binding.generateButton.setOnClickListener {
            val text = binding.textInput.text?.toString()?.trim()
            if (text.isNullOrEmpty()) {
                toast(R.string.enter_text_first)
                return@setOnClickListener
            }
            
            val variantCount = binding.variantSlider.value.toInt()
            generateAudio(text, variantCount)
        }
    }
    
    private fun generateAudio(text: String, variantCount: Int) {
        lifecycleScope.launch {
            showLoading(true)
            
            val result = withContext(Dispatchers.IO) {
                dependencies.getGenerateAudioUseCase().execute(
                    text = text,
                    variantCount = variantCount
                )
            }
            
            showLoading(false)
            
            result.onSuccess { tracks ->
                generatedTracks = tracks
                toast("Generated ${tracks.size} audio variants successfully!")
                updateUI(tracks)
            }.onFailure { error ->
                showError(error.message ?: "Failed to generate audio")
            }
        }
    }
    
    private fun updateUI(tracks: List<TrackDomain>) {
    }
    
    private fun showLoading(loading: Boolean) {
        binding.generateButton.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
    
    private fun showError(message: String) {
        toast(message)
    }
}
