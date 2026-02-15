package org.fossify.musicplayer.activities

import android.os.Bundle
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.musicplayer.R
import org.fossify.musicplayer.adapters.StatisticsAdapter
import org.fossify.musicplayer.databinding.ActivityStatisticsBinding
import org.fossify.musicplayer.helpers.StatisticsHelper

class StatisticsActivity : SimpleMusicActivity() {
    
    private val binding by viewBinding(ActivityStatisticsBinding::inflate)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        setupEdgeToEdge(
            padBottomImeAndSystem = listOf(binding.statisticsList),
            padBottomSystem = listOf(binding.currentTrackBar.root)
        )
        setupMaterialScrollListener(binding.statisticsList, binding.statisticsAppbar)
        
        val properPrimaryColor = getProperPrimaryColor()
        binding.statisticsFastscroller.updateColors(properPrimaryColor)
        binding.statisticsPlaceholder.setTextColor(getProperTextColor())
        
        setupCurrentTrackBar(binding.currentTrackBar.root)
    }
    
    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.statisticsAppbar, NavigationIcon.Arrow)
        refreshStatistics()
    }
    
    private fun refreshStatistics() {
        binding.statisticsPlaceholder.beGone()
        
        StatisticsHelper.getMostPlayedTracks(this, limit = 100) { trackStats ->
            runOnUiThread {
                if (trackStats.isEmpty()) {
                    binding.statisticsPlaceholder.beVisible()
                } else {
                    val currentAdapter = binding.statisticsList.adapter
                    if (currentAdapter == null) {
                        StatisticsAdapter(
                            activity = this,
                            recyclerView = binding.statisticsList,
                            items = ArrayList(trackStats),
                            itemClick = { item ->
                                itemClicked(item as StatisticsHelper.TrackStatistics)
                            }
                        ).apply {
                            binding.statisticsList.adapter = this
                        }
                        
                        if (areSystemAnimationsEnabled) {
                            binding.statisticsList.scheduleLayoutAnimation()
                        }
                    } else {
                        (currentAdapter as StatisticsAdapter).updateItems(ArrayList(trackStats))
                    }
                }
            }
        }
    }
    
    private fun itemClicked(trackStats: StatisticsHelper.TrackStatistics) {
        val track = trackStats.track
        val allTracks = (binding.statisticsList.adapter as? StatisticsAdapter)?.items?.map { it.track } ?: emptyList()
        
        handleNotificationPermission { granted ->
            if (granted) {
                val startIndex = allTracks.indexOf(track)
                prepareAndPlay(allTracks, startIndex)
            } else {
                org.fossify.commons.dialogs.PermissionRequiredDialog(
                    this, 
                    org.fossify.commons.R.string.allow_notifications_music_player, 
                    { openNotificationSettings() }
                )
            }
        }
    }
}

