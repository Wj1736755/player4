package org.fossify.musicplayer.helpers

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getFilenameExtension
import org.fossify.musicplayer.models.Track
import org.jaudiotagger.audio.SupportedFileFormat
import org.jaudiotagger.tag.TagOptionSingleton

class TagHelper(private val activity: BaseSimpleActivity) {

    init {
        TagOptionSingleton.getInstance().isAndroid = true
    }

    companion object {
        private const val TEMP_FOLDER = "music"

        // Editing tags in WMA and WAV files are flaky so we exclude them
        private val EXCLUDED_EXTENSIONS = listOf("wma", "wav")
        private val SUPPORTED_EXTENSIONS = SupportedFileFormat.values().map { it.filesuffix }.filter { it !in EXCLUDED_EXTENSIONS }
    }

    fun isEditTagSupported(track: Track): Boolean {
        return SUPPORTED_EXTENSIONS.any { it == track.path.getFilenameExtension() }
    }

    fun writeTag(track: Track) {
        // Title/Artist/Album tags removed - no longer editing these fields
    }
}
