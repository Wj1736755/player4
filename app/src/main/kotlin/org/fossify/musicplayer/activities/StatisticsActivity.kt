package org.fossify.musicplayer.activities

import android.os.Bundle
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.musicplayer.R
import org.fossify.musicplayer.adapters.StatisticsAdapter
import org.fossify.musicplayer.databinding.ActivityStatisticsBinding
import org.fossify.musicplayer.helpers.StatisticsHelper
import java.util.Calendar
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.models.RadioItem

class StatisticsActivity : SimpleMusicActivity() {
    
    private val binding by viewBinding(ActivityStatisticsBinding::inflate)
    private var selectedDate: Long = System.currentTimeMillis() // Default to today
    
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
        setupDateSelector()
    }
    
    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.statisticsAppbar, NavigationIcon.Arrow)
        
        // Add date picker action to toolbar
        binding.statisticsAppbar.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.statistics_toolbar)?.apply {
            menu.clear()
            menu.add(0, 1, 0, R.string.select_date).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == 1) {
                    showDatePicker()
                    true
                } else {
                    false
                }
            }
        }
        
        updateTitle()
        refreshStatistics()
    }
    
    private fun setupDateSelector() {
        binding.statisticsAppbar.setOnClickListener {
            showDatePicker()
        }
    }
    
    private fun showDatePicker() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
        val items = ArrayList<RadioItem>()
        
        // Generate date options: Today, Yesterday, and last 30 days
        val todayCalendar = Calendar.getInstance()
        for (i in 0..29) {
            val dayCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -i)
            }
            
            val label = when (i) {
                0 -> getString(R.string.today)
                1 -> getString(R.string.yesterday)
                else -> {
                    val dayOfMonth = dayCalendar.get(Calendar.DAY_OF_MONTH)
                    val month = dayCalendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, java.util.Locale.getDefault())
                    "$dayOfMonth $month"
                }
            }
            
            items.add(RadioItem(i, label))
        }
        
        val currentDayIndex = getDaysDifference(selectedDate, System.currentTimeMillis())
        
        RadioGroupDialog(this, items, currentDayIndex) { selectedIndex ->
            val newCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -(selectedIndex as Int))
            }
            selectedDate = newCalendar.timeInMillis
            updateTitle()
            refreshStatistics()
        }
    }
    
    private fun getDaysDifference(timestamp1: Long, timestamp2: Long): Int {
        val cal1 = Calendar.getInstance().apply {
            timeInMillis = timestamp1
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val cal2 = Calendar.getInstance().apply {
            timeInMillis = timestamp2
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return ((cal2.timeInMillis - cal1.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
    }
    
    private fun updateTitle() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
        val today = Calendar.getInstance()
        
        val title = when {
            isSameDay(calendar, today) -> getString(R.string.today)
            isSameDay(calendar, today.apply { add(Calendar.DAY_OF_MONTH, -1) }) -> getString(R.string.yesterday)
            else -> {
                val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                val month = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.getDefault())
                val year = calendar.get(Calendar.YEAR)
                "$dayOfMonth $month $year"
            }
        }
        
        binding.statisticsAppbar.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.statistics_toolbar)?.title = title
    }
    
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    
    private fun refreshStatistics() {
        binding.statisticsPlaceholder.beGone()
        
        // Get tracks played on the selected date
        StatisticsHelper.getTracksPlayedOnDay(this, selectedDate) { trackStats ->
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

