package org.fossify.musicplayer.fragments

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.adapters.TracksAdapter
import org.fossify.musicplayer.databinding.FragmentTextBinding
import org.fossify.musicplayer.dialogs.ChangeSortingDialog
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.tracksDAO
import org.fossify.musicplayer.extensions.viewBinding
import org.fossify.musicplayer.helpers.TAB_TEXT
import org.fossify.musicplayer.models.Track
import org.fossify.musicplayer.models.sortSafely

class TextFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var tracks = ArrayList<Track>()
    private val binding by viewBinding(FragmentTextBinding::bind)

    override fun setupFragment(activity: BaseSimpleActivity) {
        // Show placeholder - no need to load all tracks, we'll query on demand
        activity.runOnUiThread {
            binding.textPlaceholder.text = context.getString(R.string.search_transcription)
            binding.textPlaceholder.beVisible()
            binding.textList.beGone()
        }
    }

    override fun finishActMode() {
        getAdapter()?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        if (text.length < 3) {
            // Clear results and show placeholder
            tracks.clear()
            getAdapter()?.updateItems(tracks)
            binding.textPlaceholder.text = context.getString(R.string.search_transcription)
            binding.textPlaceholder.beVisible()
            binding.textList.beGone()
            return
        }

        // Search in transcription with Polish character normalization
        ensureBackgroundThread {
            // Normalize search text for Polish characters
            val normalizedSearchText = normalizePolishText(text)
            
            // Get all tracks with transcription from database (uses index for faster retrieval)
            val allTracksWithTranscription = context.tracksDAO.getTracksWithTranscription()
            
            // Filter in memory with Polish character normalization
            // This allows "moze" to match "może", "lódź" to match "lodz", etc.
            val filtered = ArrayList(
                allTracksWithTranscription.filter { track ->
                    val normalizedTranscription = normalizePolishText(track.transcription!!)
                    normalizedTranscription.contains(normalizedSearchText, ignoreCase = true)
                }
            )

            (context as? Activity)?.runOnUiThread {
                tracks = filtered
                // Sort results by current sorting preference (default: date added descending)
                tracks.sortSafely(context.config.textSorting)
                val adapter = binding.textList.adapter
                if (adapter == null) {
                    TracksAdapter(
                        activity = context as SimpleActivity,
                        recyclerView = binding.textList,
                        sourceType = TracksAdapter.TYPE_TRACKS,
                        items = tracks,
                        showTranscription = true
                    ) {
                        (context as? SimpleActivity)?.hideKeyboard()
                        (context as? SimpleActivity)?.handleNotificationPermission { granted ->
                            if (granted) {
                                // Playing from search, not from a specific playlist
                                context.config.activePlaylistPlayed = null
                                val startIndex = tracks.indexOf(it as Track)
                                prepareAndPlay(tracks, startIndex)
                            } else {
                                if (context is Activity) {
                                    PermissionRequiredDialog(
                                        context as SimpleActivity,
                                        org.fossify.commons.R.string.allow_notifications_music_player,
                                        { (context as SimpleActivity).openNotificationSettings() }
                                    )
                                }
                            }
                        }
                    }.apply {
                        binding.textList.adapter = this
                    }

                    if (context.areSystemAnimationsEnabled) {
                        binding.textList.scheduleLayoutAnimation()
                    }
                } else {
                    (adapter as TracksAdapter).updateItems(tracks)
                }

                if (tracks.isEmpty()) {
                    binding.textPlaceholder.text = context.getString(org.fossify.commons.R.string.no_items_found)
                    binding.textPlaceholder.beVisible()
                    binding.textList.beGone()
                } else {
                    binding.textPlaceholder.beGone()
                    binding.textList.beVisible()
                }
            }
        }
    }

    override fun onSearchClosed() {
        tracks.clear()
        getAdapter()?.updateItems(tracks)
        binding.textPlaceholder.text = context.getString(R.string.search_transcription)
        binding.textPlaceholder.beVisible()
        binding.textList.beGone()
    }

    override fun onSortOpen(activity: SimpleActivity) {
        ChangeSortingDialog(activity, TAB_TEXT) {
            val adapter = getAdapter() ?: return@ChangeSortingDialog
            // Re-sort current search results
            tracks.sortSafely(activity.config.textSorting)
            adapter.updateItems(tracks, forceUpdate = true)
        }
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        binding.textPlaceholder.setTextColor(textColor)
        binding.textFastscroller.updateColors(adjustedPrimaryColor)
        getAdapter()?.updateColors(textColor)
    }

    private fun getAdapter() = binding.textList.adapter as? TracksAdapter

    /**
     * Normalizes Polish special characters to their ASCII equivalents:
     * ą->a, ć->c, ę->e, ł->l, ń->n, ó->o, ś->s, ź/ż->z
     */
    private fun normalizePolishText(text: String): String {
        return text
            .replace('ą', 'a').replace('Ą', 'A')
            .replace('ć', 'c').replace('Ć', 'C')
            .replace('ę', 'e').replace('Ę', 'E')
            .replace('ł', 'l').replace('Ł', 'L')
            .replace('ń', 'n').replace('Ń', 'N')
            .replace('ó', 'o').replace('Ó', 'O')
            .replace('ś', 's').replace('Ś', 'S')
            .replace('ź', 'z').replace('Ź', 'Z')
            .replace('ż', 'z').replace('Ż', 'Z')
    }
}
