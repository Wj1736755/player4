package org.fossify.musicplayer.adapters

import android.view.Menu
import android.view.ViewGroup
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.ItemTrackBinding
import org.fossify.musicplayer.dialogs.EditDialog
import org.fossify.musicplayer.extensions.getTrackCoverArt
import org.fossify.musicplayer.helpers.StatisticsHelper.TrackStatistics

/**
 * Adapter for displaying track statistics (most played tracks with play counts).
 */
class StatisticsAdapter(
    activity: BaseSimpleActivity,
    recyclerView: MyRecyclerView,
    items: ArrayList<TrackStatistics>,
    itemClick: (Any) -> Unit
) : BaseMusicAdapter<TrackStatistics>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrackBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val trackStats = items.getOrNull(position) ?: return
        holder.bindView(trackStats, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, trackStats, holder)
        }
        bindViewHolder(holder)
    }

    override fun getActionMenuId() = R.menu.cab_tracks

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_remove_from_playlist).isVisible = false
            findItem(R.id.cab_play_next).isVisible = shouldShowPlayNext()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_to_playlist -> addToPlaylist()
            R.id.cab_add_to_queue -> addToQueue()
            R.id.cab_properties -> showProperties()
            R.id.cab_rename -> displayEditDialog()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_share -> shareFiles()
            R.id.cab_select_all -> selectAll()
            R.id.cab_play_next -> playNextInQueue()
        }
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(context) {
            ensureBackgroundThread {
                val positions = ArrayList<Int>()
                val selectedTracks = getSelectedTracks()
                selectedTracks.forEach { track ->
                    val position = items.indexOfFirst { it.track.guid == track.guid }
                    if (position != -1) {
                        positions.add(position)
                    }
                }

                context.deleteTracks(selectedTracks) {
                    context.runOnUiThread {
                        positions.sortDescending()
                        removeSelectedItems(positions)
                        positions.forEach {
                            if (items.size > it) {
                                items.removeAt(it)
                            }
                        }

                        finishActMode()

                        if (items.isEmpty()) {
                            context.finish()
                        }
                    }
                }
            }
        }
    }

    private fun displayEditDialog() {
        getSelectedTracks().firstOrNull()?.let { selectedTrack ->
            EditDialog(context, selectedTrack) { track ->
                val trackIndex = items.indexOfFirst { it.track.guid == track.guid }
                if (trackIndex != -1) {
                    items[trackIndex] = items[trackIndex].copy(track = track)
                    notifyItemChanged(trackIndex)
                    finishActMode()

                    context.refreshQueueAndTracks(track)
                }
            }
        }
    }

    override fun getSelectedTracks(): List<org.fossify.musicplayer.models.Track> {
        return items.filter { selectedKeys.contains(it.hashCode()) }.map { it.track }
    }

    private fun setupView(view: android.view.View, trackStats: TrackStatistics, holder: ViewHolder) {
        val track = trackStats.track
        ItemTrackBinding.bind(view).apply {
            root.setupViewBackground(context)
            trackFrame.isSelected = selectedKeys.contains(trackStats.hashCode())
            
            // Display track title
            trackTitle.text = track.title
            trackTitle.setTextColor(textColor)
            
            // Display artist, album, play count, and average playback speed
            val playCountText = "${trackStats.playCount}x"
            val speedText = trackStats.averagePlaybackSpeed?.let { 
                String.format("%.2fx", it)
            } ?: ""
            val infoText = if (speedText.isNotEmpty()) {
                "${track.artist} • ${track.album} • $playCountText • $speedText"
            } else {
                "${track.artist} • ${track.album} • $playCountText"
            }
            trackInfo.text = infoText
            trackInfo.setTextColor(textColor)
            
            // Display duration
            trackDuration.text = track.duration.getFormattedDuration()
            trackDuration.setTextColor(textColor)
            
            // Display play count in trackId field
            trackId.text = trackStats.playCount.toString()
            trackId.setTextColor(context.getProperPrimaryColor())
            trackId.beVisible()
            
            // Load cover art
            activity.getTrackCoverArt(track) { coverArt ->
                loadImage(trackImage, coverArt, placeholderBig)
            }
            trackImage.beVisible()
            
            trackDragHandle.beGone()
        }
    }

    override fun onChange(position: Int): CharSequence {
        return items.getOrNull(position)?.track?.title ?: ""
    }
}

