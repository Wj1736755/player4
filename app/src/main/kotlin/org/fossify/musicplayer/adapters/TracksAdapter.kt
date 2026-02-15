package org.fossify.musicplayer.adapters

import android.annotation.SuppressLint
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.ItemMoveCallback
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.commons.interfaces.StartReorderDragListener
import org.fossify.commons.views.MyRecyclerView
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.ItemTrackBinding
import org.fossify.musicplayer.dialogs.EditDialog
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.getTrackCoverArt
import org.fossify.musicplayer.helpers.ALL_TRACKS_PLAYLIST_ID
import org.fossify.musicplayer.helpers.PLAYER_SORT_BY_CUSTOM
import org.fossify.musicplayer.inlines.indexOfFirstOrNull
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.models.Playlist
import org.fossify.musicplayer.models.Track
import org.greenrobot.eventbus.EventBus

class TracksAdapter(
    activity: BaseSimpleActivity,
    recyclerView: MyRecyclerView,
    val sourceType: Int,
    val folder: String? = null,
    val playlist: Playlist? = null,
    items: ArrayList<Track>,
    val showTranscription: Boolean = false,
    itemClick: (Any) -> Unit
) : BaseMusicAdapter<Track>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate, ItemTouchHelperContract {

    private var touchHelper: ItemTouchHelper? = null
    private var startReorderDragListener: StartReorderDragListener

    init {
        touchHelper = ItemTouchHelper(ItemMoveCallback(this))
        touchHelper!!.attachToRecyclerView(recyclerView)

        startReorderDragListener = object : StartReorderDragListener {
            override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                touchHelper?.startDrag(viewHolder)
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_tracks

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTrackBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = items.getOrNull(position) ?: return
        holder.bindView(track, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, track, holder)
        }
        bindViewHolder(holder)
    }

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_remove_from_playlist).isVisible = isPlaylistContent()
            findItem(R.id.cab_rename).isVisible = shouldShowRename()
            findItem(R.id.cab_play_next).isVisible = shouldShowPlayNext()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_to_playlist -> addToPlaylist()
            R.id.cab_add_to_playlist_and_queue -> addToPlaylistAndQueue()
            R.id.cab_add_to_queue -> addToQueue()
            R.id.cab_properties -> showProperties()
            R.id.cab_rename -> displayEditDialog()
            R.id.cab_remove_from_playlist -> removeFromPlaylist()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_share -> shareFiles()
            R.id.cab_select_all -> selectAll()
            R.id.cab_play_next -> playNextInQueue()
        }
    }

    override fun onActionModeCreated() {
        if (isPlaylistContent()) {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun onActionModeDestroyed() {
        if (isPlaylistContent()) {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    private fun removeFromPlaylist() {
        ensureBackgroundThread {
            val playlistId = playlist?.id ?: return@ensureBackgroundThread
            val positions = ArrayList<Int>()
            val selectedTracks = getSelectedTracks()
            selectedTracks.forEach { track ->
                val position = items.indexOfFirst { it.guid == track.guid }
                if (position != -1) {
                    positions.add(position)
                }
            }

            // Remove tracks only from this specific playlist, not from all playlists
            context.audioHelper.removeTracksFromPlaylist(selectedTracks, playlistId)
            
            // this is to make sure these tracks aren't automatically re-added to the 'All tracks' playlist on rescan
            if (playlistId == ALL_TRACKS_PLAYLIST_ID) {
                val removedTrackGuids = selectedTracks.mapNotNull { it.guid?.toString() }
                if (removedTrackGuids.isNotEmpty()) {
                    val config = context.config
                    config.tracksRemovedFromAllTracksPlaylist = config.tracksRemovedFromAllTracksPlaylist.apply {
                        addAll(removedTrackGuids)
                    }
                }
            }

            EventBus.getDefault().post(Events.PlaylistsUpdated())
            context.runOnUiThread {
                positions.sortDescending()
                removeSelectedItems(positions)
                positions.forEach {
                    items.removeAt(it)
                }
            }
        }
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(context) {
            ensureBackgroundThread {
                val positions = ArrayList<Int>()
                val selectedTracks = getSelectedTracks()
                selectedTracks.forEach { track ->
                    val position = items.indexOfFirst { it.guid == track.guid }
                    if (position != -1) {
                        positions.add(position)
                    }
                }

                context.deleteTracks(selectedTracks) {
                    // After deleting files from disk, also remove from database
                    ensureBackgroundThread {
                        val guidsToDelete = selectedTracks.mapNotNull { it.guid }
                        context.audioHelper.deleteTracksByGuid(guidsToDelete)
                        
                        context.runOnUiThread {
                            positions.sortDescending()
                            removeSelectedItems(positions)
                            positions.forEach {
                                if (items.size > it) {
                                    items.removeAt(it)
                                }
                            }

                            finishActMode()

                            // finish activity if all tracks are deleted
                            if (items.isEmpty() && !isPlaylistContent()) {
                                context.finish()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getSelectedTracks(): List<Track> = items.filter { selectedKeys.contains(it.hashCode()) }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView(view: View, track: Track, holder: ViewHolder) {
        ItemTrackBinding.bind(view).apply {
            root.setupViewBackground(context)
            trackFrame.isSelected = selectedKeys.contains(track.hashCode())
            
            // Show transcription as title if requested, otherwise show track title
            val displayTitle = if (showTranscription && !track.transcription.isNullOrEmpty()) {
                track.transcription!!
            } else {
                track.title
            }
            
            // Configure track title display based on mode
            if (showTranscription && !track.transcription.isNullOrEmpty()) {
                // Text search mode: show transcription with smaller font and 3 lines
                trackTitle.maxLines = 3
                trackTitle.textSize = 14f  // Smaller font for transcription
            } else {
                // Normal mode: default 2 lines and bigger font
                trackTitle.maxLines = 2
                trackTitle.textSize = 16f
            }
            
            trackTitle.text = if (textToHighlight.isEmpty()) displayTitle else displayTitle.highlightTextPart(textToHighlight, properPrimaryColor)
            
            // Hide track info in transcription mode, otherwise show artist • album
            if (showTranscription && !track.transcription.isNullOrEmpty()) {
                trackInfo.beGone()
            } else {
                trackInfo.beVisible()
                trackInfo.text = if (textToHighlight.isEmpty()) {
                    "${track.artist} • ${track.album}"
                } else {
                    ("${track.artist} • ${track.album}").highlightTextPart(textToHighlight, properPrimaryColor)
                }
            }
            trackDragHandle.beVisibleIf(isPlaylistContent() && selectedKeys.isNotEmpty())
            trackDragHandle.applyColorFilter(textColor)
            trackDragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    startReorderDragListener.requestDrag(holder)
                }
                false
            }

            arrayOf(trackId, trackTitle, trackInfo, trackDuration).forEach {
                it.setTextColor(textColor)
            }

            trackDuration.text = track.duration.getFormattedDuration()
            
            // Always hide cover art
            trackImage.beGone()
            trackId.beGone()
        }
    }

    override fun onChange(position: Int): String {
        val sorting = if (isPlaylistContent() && playlist != null) {
            context.config.getProperPlaylistSorting(playlist.id)
        } else if (sourceType == TYPE_FOLDER && folder != null) {
            context.config.getProperFolderSorting(folder)
        } else {
            context.config.trackSorting
        }

        return items.getOrNull(position)?.getBubbleText(sorting) ?: ""
    }

    private fun displayEditDialog() {
        getSelectedTracks().firstOrNull()?.let { selectedTrack ->
            EditDialog(context, selectedTrack) { track ->
                val trackIndex = items.indexOfFirstOrNull { it.guid == track.guid } ?: return@EditDialog
                items[trackIndex] = track
                notifyItemChanged(trackIndex)
                finishActMode()

                context.refreshQueueAndTracks(track)
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        context.config.saveCustomPlaylistSorting(playlist!!.id, PLAYER_SORT_BY_CUSTOM)
        items.swap(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onRowClear(myViewHolder: ViewHolder?) {
        // TODO: Reimplement reordering with junction table (PlaylistTracksDao.reorderPlaylist)
        // Drag & drop reordering is disabled until this is implemented
    }

    private fun isPlaylistContent() = sourceType == TYPE_PLAYLIST

    companion object {
        const val TYPE_PLAYLIST = 1
        const val TYPE_FOLDER = 2
        const val TYPE_ALBUM = 3
        const val TYPE_TRACKS = 4
    }
}
