package org.fossify.musicplayer.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager.widget.ViewPager
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import me.grantland.widget.AutofitHelper
import org.fossify.commons.databinding.BottomTablayoutItemBinding
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.Release
import org.fossify.commons.views.MySearchMenu
import org.fossify.commons.views.MyTextView
import org.fossify.commons.views.MyViewPager
import org.fossify.musicplayer.R
import org.fossify.musicplayer.BuildConfig
import org.fossify.musicplayer.adapters.ViewPagerAdapter
import org.fossify.musicplayer.dialogs.NewPlaylistDialog
import org.fossify.musicplayer.dialogs.SelectPlaylistDialog
import org.fossify.musicplayer.dialogs.SleepTimerCustomDialog
import org.fossify.musicplayer.extensions.*
import org.fossify.musicplayer.helpers.*
import org.fossify.musicplayer.helpers.M3uImporter.ImportResult
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.models.Track
import org.fossify.musicplayer.playback.CustomCommands
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.FileOutputStream
import org.fossify.musicplayer.databases.SongsDatabase
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.fossify.musicplayer.workers.DatabaseBackupWorker
import java.util.concurrent.TimeUnit

class MainActivity : SimpleMusicActivity() {
    private val PICK_IMPORT_SOURCE_INTENT = 1

    private var bus: EventBus? = null
    private var storedShowTabs = 0
    private var storedExcludedFolders = 0

    override var isSearchBarEnabled = true

    private lateinit var mainMenu: MySearchMenu
    private lateinit var mainTabsHolder: TabLayout
    private lateinit var viewPager: MyViewPager
    private lateinit var mainHolder: ConstraintLayout
    private lateinit var currentTrackBar: org.fossify.musicplayer.views.CurrentTrackBar
    private lateinit var loadingProgressBar: LinearProgressIndicator
    private lateinit var sleepTimerHolder: ConstraintLayout
    private lateinit var sleepTimerStop: ImageView
    private lateinit var sleepTimerValue: MyTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        // 🚨 ONE-TIME AUTO-RESTORE: Restore latest manual backup before Room initialization
        // This recovers data lost due to the GUID bug in previous versions
        attemptOneTimeAutoRestore()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()
        setupEdgeToEdge(
            padBottomImeAndSystem = buildList {
                add(mainTabsHolder)
                if (getVisibleTabs().size == 1) {
                    add(currentTrackBar)
                }
            }
        )
        storeStateVariables()
        setupTabs()
        setupCurrentTrackBar(currentTrackBar)

        handlePermission(getPermissionToRequest()) {
            if (it) {
                initActivity()
            } else {
                toast(org.fossify.commons.R.string.no_storage_permissions)
                finish()
            }
        }

        volumeControlStream = AudioManager.STREAM_MUSIC
        checkWhatsNewDialog()
        checkAppOnSDCard()
        
        // Schedule periodic database backups (every 4 hours)
        schedulePeriodicDatabaseBackups()
    }
    
    /**
     * Schedule periodic database backups every 4 hours
     * Backups are saved to: /sdcard/MusicPlayer/Backups/daily-backups/
     */
    private fun schedulePeriodicDatabaseBackups() {
        try {
            val backupWorkRequest = PeriodicWorkRequestBuilder<DatabaseBackupWorker>(
                repeatInterval = 4, // Every 4 hours
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 15, // 15 minutes flex
                flexTimeIntervalUnit = TimeUnit.MINUTES
            ).build()
            
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "database_backup_4h",
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule if already exists
                backupWorkRequest
            )
            
            Log.d("MainActivity", "Periodic database backup scheduled (every 4 hours)")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to schedule periodic backup", e)
        }
    }

    /**
     * ONE-TIME auto-restore from latest manual backup
     * Runs only once for this build to recover data after GUID bug fix
     * Automatically finds the latest backup in /sdcard/MusicPlayer/manual-backups/
     */
    private fun attemptOneTimeAutoRestore() {
        Log.e("MainActivity", "🔍 AUTO-RESTORE: Starting... (Build ${BuildConfig.VERSION_CODE})")
        
        val prefs = getSharedPreferences("music_player_prefs", android.content.Context.MODE_PRIVATE)
        val restoreKey = "auto_restore_completed_for_build_${BuildConfig.VERSION_CODE}"
        
        if (prefs.getBoolean(restoreKey, false)) {
            Log.w("MainActivity", "AUTO-RESTORE: Already completed for this build")
            return
        }
        
        // Check if we have MANAGE_EXTERNAL_STORAGE permission (Android 11+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Log.w("MainActivity", "AUTO-RESTORE: MANAGE_EXTERNAL_STORAGE not granted, requesting...")
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                    android.widget.Toast.makeText(this, "Please grant 'All files access' permission to restore database backup", android.widget.Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to open permission settings", e)
                }
                // Don't mark as completed - will retry on next launch after permission granted
                return
            }
        }
        
        try {
            // Find latest manual backup
            val backupFolder = File("/storage/emulated/0/MusicPlayer/Backups/manual-backups")
            Log.i("MainActivity", "AUTO-RESTORE: Checking folder: ${backupFolder.absolutePath}")
            
            if (!backupFolder.exists() || !backupFolder.isDirectory) {
                Log.w("MainActivity", "AUTO-RESTORE: Folder not found")
                prefs.edit().putBoolean(restoreKey, true).apply()
                return
            }
            
            val allFiles = backupFolder.listFiles()
            Log.i("MainActivity", "AUTO-RESTORE: Found ${allFiles?.size ?: 0} files")
            allFiles?.forEach { f -> 
                Log.d("MainActivity", "  - ${f.name} (${f.length()} bytes)")
                Log.d("MainActivity", "    isFile=${f.isFile}, endsWithDb=${f.name.endsWith(".db")}, size>${1024}=${f.length() > 1024}")
            }
            
            val latestBackup = allFiles
                ?.filter { 
                    val matchesPattern = it.name.startsWith("songs_manual_backup_dbv_") || it.name == "songs_backup.db"
                    val isValid = it.isFile && matchesPattern && it.name.endsWith(".db") && it.length() > 1024
                    Log.d("MainActivity", "  File ${it.name}: matchesPattern=$matchesPattern, isValid=$isValid")
                    isValid
                }
                ?.maxByOrNull { it.lastModified() }
            
            if (latestBackup == null) {
                Log.w("MainActivity", "AUTO-RESTORE: No valid backup found")
                prefs.edit().putBoolean(restoreKey, true).apply()
                return
            }
            
            Log.i("MainActivity", "AUTO-RESTORE: Selected backup: ${latestBackup.name}")
            
            val dbPath = getDatabasePath("songs.db")
            
            // Copy backup to songs.db (BEFORE Room initialization)
            latestBackup.inputStream().use { input ->
                dbPath.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.e("MainActivity", "✅ AUTO-RESTORE SUCCESS: Database restored from ${latestBackup.name}")
            
            // Mark as completed BEFORE restart
            prefs.edit().putBoolean(restoreKey, true).apply()
            
            // CRITICAL: Restart app to ensure Room uses the restored database
            android.widget.Toast.makeText(this, "Database restored. Restarting app...", android.widget.Toast.LENGTH_LONG).show()
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
                android.os.Process.killProcess(android.os.Process.myPid())
            }, 1000)
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ AUTO-RESTORE FAILED", e)
            prefs.edit().putBoolean(restoreKey, true).apply()
        }
    }

    override fun onResume() {
        super.onResume()
        handleNotificationIntent(intent)
        if (storedShowTabs != config.showTabs) {
            config.lastUsedViewPagerPage = 0
            System.exit(0)
            return
        }

        updateMenuColors()
        updateTextColors(mainHolder)
        setupTabColors()
        val properTextColor = getProperTextColor()
        val properPrimaryColor = getProperPrimaryColor()
        sleepTimerHolder.background = ColorDrawable(getProperBackgroundColor())
        sleepTimerStop.applyColorFilter(properTextColor)
        loadingProgressBar.setIndicatorColor(properPrimaryColor)
        loadingProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)

        getAllFragments().forEach {
            it.setupColors(properTextColor, properPrimaryColor)
        }

        if (storedExcludedFolders != config.excludedFolders.hashCode()) {
            refreshAllFragments(shouldScan = false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        config.lastUsedViewPagerPage = viewPager.currentItem
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onBackPressedCompat(): Boolean {
        return if (mainMenu.isSearchOpen) {
            mainMenu.closeSearch()
            true
        } else {
            false
        }
    }

    private fun refreshMenuItems(position: Int = viewPager.currentItem) {
        mainMenu.requireToolbar().menu.apply {
            val tab = getVisibleTabs()[position]
            val isPlaylistFragment = tab == TAB_PLAYLISTS
            findItem(R.id.create_new_playlist).isVisible = isPlaylistFragment
            findItem(R.id.create_playlist_from_folder).isVisible = isPlaylistFragment
            findItem(R.id.import_playlist).isVisible = isPlaylistFragment
            findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)
        }
    }

    private fun setupOptionsMenu() {
        mainMenu.requireToolbar().inflateMenu(R.menu.menu_main)
        mainMenu.toggleHideOnScroll(false)
        mainMenu.setupMenu()

        mainMenu.onSearchClosedListener = {
            getAllFragments().forEach {
                it.onSearchClosed()
            }
        }

        mainMenu.onSearchTextChangedListener = { text ->
            getCurrentFragment()?.onSearchQueryChanged(text)
        }

        mainMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog()
                R.id.rescan_media -> refreshAllFragments(showProgress = true, shouldScan = true)
                R.id.scan_tags -> scanID3Tags()
                R.id.sleep_timer -> showSleepTimer()
                R.id.create_new_playlist -> createNewPlaylist()
                R.id.create_playlist_from_folder -> createPlaylistFromFolder()
                R.id.import_playlist -> tryImportPlaylist()
                R.id.equalizer -> launchEqualizer()
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.statistics -> launchStatistics()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateMenuColors() {
        mainMenu.updateColors()
    }

    private fun storeStateVariables() {
        config.apply {
            storedShowTabs = showTabs
            storedExcludedFolders = config.excludedFolders.hashCode()
        }
    }

    private fun initActivity() {
        bus = EventBus.getDefault()
        bus!!.register(this)
        initFragments()
        sleepTimerStop.setOnClickListener { stopSleepTimer() }

        // only refresh UI from the existing database on startup; full scan is triggered manually from the menu
        refreshAllFragments(showProgress = false, shouldScan = false)
    }

    private fun refreshAllFragments(
        showProgress: Boolean = config.appRunCount == 1,
        shouldScan: Boolean = true,
    ) {
        if (showProgress) {
            loadingProgressBar.show()
        }

        val onDone: (complete: Boolean) -> Unit = { complete ->
            runOnUiThread {
                getAllFragments().forEach {
                    it.setupFragment(this)
                }

                if (complete) {
                    loadingProgressBar.hide()
                    withPlayer {
                        if (currentMediaItem == null) {
                            maybePreparePlayer()
                        } else {
                            sendCommand(CustomCommands.RELOAD_CONTENT)
                        }
                    }
                }
            }
        }

        if (shouldScan) {
            handleNotificationPermission { granted ->
                mediaScanner.scan(progress = showProgress && granted) { complete ->
                    onDone(complete)
                }
            }
        } else {
            onDone(true)
        }
    }

    private fun initFragments() {
        viewPager.adapter = ViewPagerAdapter(this)
        viewPager.offscreenPageLimit = tabsList.size - 1
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                mainTabsHolder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it.finishActMode()
                }
                refreshMenuItems(position)
            }
        })
        viewPager.currentItem = config.lastUsedViewPagerPage
    }

    private fun setupTabs() {
        mainTabsHolder.removeAllTabs()
        getVisibleTabs().forEach { value ->
            mainTabsHolder.newTab().setCustomView(org.fossify.commons.R.layout.bottom_tablayout_item).apply {
                val tabItemBinding = BottomTablayoutItemBinding.bind(customView!!)
                tabItemBinding.tabItemIcon.setImageDrawable(getTabIcon(value))
                tabItemBinding.tabItemLabel.text = getTabLabel(value)
                AutofitHelper.create(tabItemBinding.tabItemLabel)
                mainTabsHolder.addTab(this)
            }
        }

        mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false)
            },
            tabSelectedAction = {
                viewPager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true)
                viewPager.post {
                    getAdapter()?.getFragmentAt(it.position)?.onSearchQueryChanged(
                        text = mainMenu.getCurrentQuery()
                    )
                }
            }
        )

        mainTabsHolder.beGoneIf(mainTabsHolder.tabCount == 1)
    }

    private fun setupTabColors() {
        val activeView = mainTabsHolder.getTabAt(viewPager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true)

        getInactiveTabIndexes(viewPager.currentItem).forEach { index ->
            val inactiveView = mainTabsHolder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false)
        }

        val bottomBarColor = getBottomNavigationBackgroundColor()
        mainTabsHolder.setBackgroundColor(bottomBarColor)
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until tabsList.size).filter { it != activeIndex }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            TAB_PLAYLISTS -> R.drawable.ic_playlist_vector
            TAB_FOLDERS -> R.drawable.ic_folders_vector
            TAB_TEXT -> R.drawable.ic_music_note_vector  // Text search icon
            TAB_GENRES -> R.drawable.ic_genre_vector
            else -> R.drawable.ic_music_note_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, getProperTextColor())
    }

    private fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            TAB_PLAYLISTS -> R.string.playlists
            TAB_FOLDERS -> R.string.folders
            TAB_TEXT -> R.string.text_search
            TAB_GENRES -> R.string.genres
            else -> R.string.tracks
        }

        return resources.getString(stringId)
    }

    private fun showSortingDialog() {
        getCurrentFragment()?.onSortOpen(this)
    }

    private fun createNewPlaylist() {
        NewPlaylistDialog(this) {
            EventBus.getDefault().post(Events.PlaylistsUpdated())
        }
    }

    private fun createPlaylistFromFolder() {
        FilePickerDialog(this, pickFile = false, enforceStorageRestrictions = false) {
            createPlaylistFrom(it)
        }
    }

    private fun createPlaylistFrom(path: String) {
        ensureBackgroundThread {
            getFolderTracks(path, true) { tracks ->
                runOnUiThread {
                    NewPlaylistDialog(this) { playlistId ->
                        ensureBackgroundThread {
                            val tracksToAdd = ArrayList(tracks)
                            tracksToAdd.forEach { it.id = 0 }
                            RoomHelper(this).insertTracksWithPlaylist(tracksToAdd, playlistId)
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_IMPORT_SOURCE_INTENT && resultCode == RESULT_OK && resultData?.data != null) {
            tryImportPlaylistFromFile(resultData.data!!)
        }
    }

    private fun tryImportPlaylistFromFile(uri: Uri) {
        when {
            uri.scheme == "file" -> showImportPlaylistDialog(uri.path!!)
            uri.scheme == "content" -> {
                val tempFile = getTempFile("imports", uri.path!!.getFilenameFromPath())
                if (tempFile == null) {
                    Log.e("MusicPlayer", "Failed to create temp file for playlist import, uri=$uri")
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                    return
                }

                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val out = FileOutputStream(tempFile)
                    inputStream!!.copyTo(out)

                    showImportPlaylistDialog(tempFile.absolutePath)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }

            else -> toast(org.fossify.commons.R.string.invalid_file_format)
        }
    }

    private fun tryImportPlaylist() {
        if (isQPlus()) {
            hideKeyboard()
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = MIME_TYPE_M3U

                try {
                    startActivityForResult(this, PICK_IMPORT_SOURCE_INTENT)
                } catch (e: ActivityNotFoundException) {
                    toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        } else {
            handlePermission(PERMISSION_READ_STORAGE) { granted ->
                if (granted) {
                    showFilePickerDialog()
                }
            }
        }
    }

    private fun showFilePickerDialog() {
        FilePickerDialog(this, enforceStorageRestrictions = false) { path ->
            SelectPlaylistDialog(this) { id ->
                importPlaylist(path, id)
            }
        }
    }

    private fun showImportPlaylistDialog(path: String) {
        SelectPlaylistDialog(this) { id ->
            importPlaylist(path, id)
        }
    }

    private fun importPlaylist(path: String, id: Int) {
        ensureBackgroundThread {
            M3uImporter(this) { result ->
                runOnUiThread {
                    toast(
                        when (result) {
                            ImportResult.IMPORT_OK -> org.fossify.commons.R.string.importing_successful
                            ImportResult.IMPORT_PARTIAL -> org.fossify.commons.R.string.importing_some_entries_failed
                            else -> org.fossify.commons.R.string.importing_failed
                        }
                    )

                    getAdapter()?.getPlaylistsFragment()?.setupFragment(this)
                }
            }.importPlaylist(path, id)
        }
    }

    private fun showSleepTimer() {
        val minutes = getString(org.fossify.commons.R.string.minutes_raw)
        val hour = resources.getQuantityString(org.fossify.commons.R.plurals.hours, 1, 1)

        val items = arrayListOf(
            RadioItem(5 * 60, "5 $minutes"),
            RadioItem(10 * 60, "10 $minutes"),
            RadioItem(20 * 60, "20 $minutes"),
            RadioItem(30 * 60, "30 $minutes"),
            RadioItem(60 * 60, hour)
        )

        if (items.none { it.id == config.lastSleepTimerSeconds }) {
            val lastSleepTimerMinutes = config.lastSleepTimerSeconds / 60
            val text = resources.getQuantityString(org.fossify.commons.R.plurals.minutes, lastSleepTimerMinutes, lastSleepTimerMinutes)
            items.add(RadioItem(config.lastSleepTimerSeconds, text))
        }

        items.sortBy { it.id }
        items.add(RadioItem(-1, getString(org.fossify.commons.R.string.custom)))

        RadioGroupDialog(this, items, config.lastSleepTimerSeconds) {
            if (it as Int == -1) {
                SleepTimerCustomDialog(this) {
                    if (it > 0) {
                        pickedSleepTimer(it)
                    }
                }
            } else if (it > 0) {
                pickedSleepTimer(it)
            }
        }
    }

    private fun pickedSleepTimer(seconds: Int) {
        config.lastSleepTimerSeconds = seconds
        config.sleepInTS = System.currentTimeMillis() + seconds * 1000
        startSleepTimer()
    }

    private fun startSleepTimer() {
        sleepTimerHolder.fadeIn()
        withPlayer {
            sendCommand(CustomCommands.TOGGLE_SLEEP_TIMER)
        }
    }

    private fun stopSleepTimer() {
        sleepTimerHolder.fadeOut()
        withPlayer {
            sendCommand(CustomCommands.TOGGLE_SLEEP_TIMER)
        }
    }

    private fun getAdapter() = viewPager.adapter as? ViewPagerAdapter

    private fun getAllFragments() = getAdapter()?.getAllFragments().orEmpty()

    private fun getCurrentFragment() = getAdapter()?.getCurrentFragment()

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun sleepTimerChanged(event: Events.SleepTimerChanged) {
        sleepTimerValue.text = event.seconds.getFormattedDuration()
        sleepTimerHolder.beVisible()

        if (event.seconds == 0) {
            finish()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun playlistsUpdated(event: Events.PlaylistsUpdated) {
        getAdapter()?.getPlaylistsFragment()?.setupFragment(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun tracksUpdated(event: Events.RefreshTracks) {
        getAdapter()?.getTracksFragment()?.setupFragment(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun shouldRefreshFragments(event: Events.RefreshFragments) {
        refreshAllFragments(showProgress = false, shouldScan = false)
    }

    private fun bindViews() {
        mainMenu = findViewById(R.id.main_menu)
        mainTabsHolder = findViewById(R.id.main_tabs_holder)
        viewPager = findViewById(R.id.view_pager)
        mainHolder = findViewById(R.id.main_holder)
        currentTrackBar = findViewById(R.id.current_track_bar)
        loadingProgressBar = findViewById(R.id.loading_progress_bar)
        sleepTimerHolder = findViewById(R.id.sleep_timer_holder)
        sleepTimerStop = findViewById(R.id.sleep_timer_stop)
        sleepTimerValue = findViewById(R.id.sleep_timer_value)
    }

    private fun launchEqualizer() {
        hideKeyboard()
        startActivity(Intent(applicationContext, EqualizerActivity::class.java))
    }

    private fun launchStatistics() {
        hideKeyboard()
        startActivity(Intent(applicationContext, StatisticsActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SimpleAboutActivity::class.java))
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }

    private fun handleNotificationIntent(intent: Intent) {
        val shouldOpenPlayer = intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)

        if (shouldOpenPlayer) {
            intent.removeExtra(EXTRA_OPEN_PLAYER)
            Intent(this, TrackActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun scanID3Tags() {
        hideKeyboard()
        
        // First, check how many tracks already have tags
        Thread {
            val allTracks = audioHelper.getAllTracks()
            val totalTracks = allTracks.size
            val tracksWithTags = allTracks.count { 
                !it.transcription.isNullOrEmpty() || it.guid != null 
            }
            
            runOnUiThread {
                val message = "$tracksWithTags/$totalTracks plików w bazie ma tag"
                
                // Check if activity is still active before showing dialog
                if (!isFinishing && !isDestroyed) {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setMessage(message)
                        .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                            startScanning()
                        }
                        .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                        .show()
                }
            }
        }.start()
    }
    
    private fun startScanning() {
        // Check storage permissions for Android 11+ (API 30+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                runOnUiThread {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Storage Permission Required")
                        .setMessage("To scan and fix ID3 tags in all folders, including Download, the app needs 'All Files Access' permission.\n\nClick OK to open Settings.")
                        .setPositiveButton("OK") { _, _ ->
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.data = android.net.Uri.parse("package:$packageName")
                                startActivity(intent)
                            } catch (e: Exception) {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                startActivity(intent)
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                return
            }
        }
        
        toast(R.string.scanning_tags)
        
        Thread {
            try {
                val startTime = System.currentTimeMillis()
                
                // STEP 1: Fix ElevenLabs timestamps first (parse from filename, convert UTC → Warsaw)
                fixElevenLabsTimestamps()
                
                // STEP 2: Regular tag scanning
                var updatedCount = 0
                var skippedCount = 0
                val batchSize = 50
                val tracksToUpdate = mutableListOf<org.fossify.musicplayer.models.Track>()
                val missingTagsPaths = mutableListOf<String>()
                
                // Get all tracks from database
                val allTracks = audioHelper.getAllTracks()
                val totalTracks = allTracks.size
                
                Log.i("MusicPlayer", "Starting tag scan for $totalTracks tracks")
                
                allTracks.forEachIndexed { index, track ->
                    try {
                        // Progress update every 100 tracks
                        if ((index + 1) % 100 == 0) {
                            runOnUiThread {
                                toast("Scanning: ${index + 1}/$totalTracks")
                            }
                        }
                        
                        // Skip if track already has tags populated
                        if (!track.transcription.isNullOrEmpty() || track.guid != null) {
                            skippedCount++
                            return@forEachIndexed
                        }
                        
                        // Process TXXX tags using unified TagsProcessor
                        val file = java.io.File(track.path)
                        if (!file.exists() || !file.canRead()) {
                            Log.w("MusicPlayer", "Cannot access file: ${track.path}")
                            skippedCount++
                            missingTagsPaths.add(track.path)
                            return@forEachIndexed
                        }
                        
                        // Read tags, generate missing ones (GUID, timestamp from filename), and write to file
                        val processed = org.fossify.musicplayer.helpers.TagsProcessor.processTrackTags(file, writeToFile = true)
                        
                        if (processed.tags == null || processed.tags.isEmpty()) {
                            // Track files without any tags
                            skippedCount++
                            missingTagsPaths.add(track.path)
                            return@forEachIndexed
                        }
                        
                        // Apply tags to track
                        org.fossify.musicplayer.helpers.TagsProcessor.applyTagsToTrack(track, processed.tags)
                        
                        tracksToUpdate.add(track)
                        updatedCount++
                        
                        // Batch insert every batchSize tracks
                        if (tracksToUpdate.size >= batchSize) {
                            tracksDAO.insertAll(tracksToUpdate)
                            Log.i("MusicPlayer", "Batch updated ${tracksToUpdate.size} tracks")
                            tracksToUpdate.clear()
                        }
                        
                    } catch (e: Exception) {
                        Log.e("MusicPlayer", "Error processing track: ${track.path}", e)
                        skippedCount++
                        missingTagsPaths.add(track.path)
                    }
                }
                
                // Insert remaining tracks
                if (tracksToUpdate.isNotEmpty()) {
                    tracksDAO.insertAll(tracksToUpdate)
                    Log.i("MusicPlayer", "Final batch updated ${tracksToUpdate.size} tracks")
                }
                
                // Export missing tags to file
                try {
                    if (missingTagsPaths.isNotEmpty()) {
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val missingFile = java.io.File(downloadsDir, "missing.txt")
                        missingFile.writeText(missingTagsPaths.joinToString("\n"))
                        Log.i("MusicPlayer", "Exported ${missingTagsPaths.size} missing files to ${missingFile.absolutePath}")
                    }
                } catch (e: Exception) {
                    Log.e("MusicPlayer", "Error writing missing.txt", e)
                }
                
                val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                val summary = """
                    Scan Complete
                    
                    Total files: $totalTracks
                    Updated: $updatedCount
                    Skipped: $skippedCount
                    Missing tags: ${missingTagsPaths.size}
                    Time: ${elapsedTime}s
                """.trimIndent()
                
                runOnUiThread {
                    // Check if activity is still active before showing dialog
                    if (!isFinishing && !isDestroyed) {
                        android.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("Tag Scan Complete")
                            .setMessage(summary)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("MusicPlayer", "Error scanning tags", e)
                runOnUiThread {
                    toast("Error: ${e.message}")
                }
            }
        }.start()
    }
    
    private fun fixElevenLabsTimestamps() {
        try {
            Log.i("MusicPlayer", "=== Starting fixElevenLabsTimestamps ===")
            
            // Get all tracks
            val allTracks = audioHelper.getAllTracks()
            val elevenLabsTracks = allTracks.filter { 
                it.title.startsWith("ElevenLabs_") && it.path.contains("ElevenLabs_")
            }
            
            if (elevenLabsTracks.isEmpty()) {
                Log.i("MusicPlayer", "No ElevenLabs tracks to fix")
                return
            }
            
            Log.i("MusicPlayer", "Found ${elevenLabsTracks.size} ElevenLabs tracks to process")
            runOnUiThread {
                toast("Fixing ElevenLabs timestamps: 0/${elevenLabsTracks.size}")
            }
            
            var fixedCount = 0
            var skippedCount = 0
            var errorCount = 0
            val tagsWriter = org.fossify.musicplayer.helpers.TXXXTagsWriter(this)
            val tagsReader = org.fossify.musicplayer.helpers.TXXXTagsReader
            
            elevenLabsTracks.forEachIndexed { index, track ->
                try {
                    // Progress update every 100 tracks
                    if ((index + 1) % 100 == 0) {
                        Log.i("MusicPlayer", "Progress: ${index + 1}/${elevenLabsTracks.size} (fixed: $fixedCount, skipped: $skippedCount, errors: $errorCount)")
                        runOnUiThread {
                            toast("Fixing: ${index + 1}/${elevenLabsTracks.size} (OK: $fixedCount)")
                        }
                    }
                    
                    val file = java.io.File(track.path)
                    if (!file.exists()) {
                        Log.w("MusicPlayer", "File not found: ${track.path}")
                        skippedCount++
                        return@forEachIndexed
                    }
                    
                    // Read existing tags FIRST (needed for all cases)
                    val existingTags = tagsReader.readAllTags(file)
                    if (existingTags == null) {
                        Log.w("MusicPlayer", "Cannot read existing tags from: ${file.name}")
                        skippedCount++
                        return@forEachIndexed
                    }
                    
                    // Parse timestamp from filename (UTC) - PRIORITY 1
                    val timestampUtcSec = org.fossify.musicplayer.helpers.TXXXTagsWriter.parseTimestampFromFilename(file.name)
                    
                    val timestampUnix = if (timestampUtcSec != null) {
                        // Filename timestamp exists - use UTC directly (Unix timestamp is always UTC!)
                        Log.d("MusicPlayer", "[${index + 1}/${elevenLabsTracks.size}] Processing: ${file.name}")
                        Log.d("MusicPlayer", "  Source: FILENAME (UTC)")
                        Log.d("MusicPlayer", "  Unix timestamp: $timestampUtcSec")
                        Log.d("MusicPlayer", "  UTC: ${java.util.Date(timestampUtcSec * 1000)}")
                        timestampUtcSec
                    } else {
                        // NO FILENAME TIMESTAMP - compare file system timestamps and TXXX tag
                        Log.d("MusicPlayer", "[${index + 1}/${elevenLabsTracks.size}] Processing: ${file.name}")
                        Log.d("MusicPlayer", "  Source: COMPARING (no timestamp in filename)")
                        
                        val candidates = mutableListOf<Long>()
                        
                        // 1. file.lastModified (local timezone = Warsaw, in milliseconds)
                        val lastModifiedSec = file.lastModified() / 1000
                        candidates.add(lastModifiedSec)
                        Log.d("MusicPlayer", "    file.lastModified: ${java.util.Date(file.lastModified())} ($lastModifiedSec sec)")
                        
                        // 2. file creation time (if available on this OS)
                        try {
                            val attrs = java.nio.file.Files.readAttributes(
                                file.toPath(),
                                java.nio.file.attribute.BasicFileAttributes::class.java
                            )
                            val creationTimeSec = attrs.creationTime().toMillis() / 1000
                            candidates.add(creationTimeSec)
                            Log.d("MusicPlayer", "    file.creationTime: ${java.util.Date(attrs.creationTime().toMillis())} ($creationTimeSec sec)")
                        } catch (e: Exception) {
                            Log.d("MusicPlayer", "    file.creationTime: not available")
                        }
                        
                        // 3. Existing TXXX CREATED_ON_TS tag (already in Warsaw timezone)
                        if (existingTags.createdOnTimestamp != null) {
                            candidates.add(existingTags.createdOnTimestamp!!)
                            Log.d("MusicPlayer", "    TXXX timestamp: ${java.util.Date(existingTags.createdOnTimestamp!! * 1000)} (${existingTags.createdOnTimestamp} sec)")
                        } else {
                            Log.d("MusicPlayer", "    TXXX timestamp: not available")
                        }
                        
                        // Select MINIMUM (oldest) timestamp
                        val oldestTimestamp = candidates.minOrNull()
                        if (oldestTimestamp == null) {
                            Log.w("MusicPlayer", "  SKIP: No valid timestamps found")
                            skippedCount++
                            return@forEachIndexed
                        }
                        
                        Log.d("MusicPlayer", "  ✅ Selected OLDEST: ${java.util.Date(oldestTimestamp * 1000)} ($oldestTimestamp sec)")
                        oldestTimestamp
                    }
                    
                    // Skip if timestamp is already correct
                    if (existingTags.createdOnTimestamp == timestampUnix) {
                        Log.d("MusicPlayer", "  SKIP: Timestamp already correct ($timestampUnix)")
                        skippedCount++
                        return@forEachIndexed
                    }
                    
                    Log.d("MusicPlayer", "  Current timestamp: ${existingTags.createdOnTimestamp} → New: $timestampUnix")
                    Log.d("MusicPlayer", "  Existing tags - GUID: ${existingTags.guid?.take(8)}, Transcription: ${existingTags.transcription?.take(30)}, Checksum: ${existingTags.checksumAudio?.take(8)}")
                    
                    // Normalize existing tags (remove ALL control characters & whitespace) to match writer's sanitization
                    val normalizedExistingTags = existingTags.copy(
                        transcription = existingTags.transcription?.filter { it.code >= 32 }?.trim(),
                        guid = existingTags.guid?.filter { it.code >= 32 }?.trim(),
                        checksumAudio = existingTags.checksumAudio?.filter { it.code >= 32 }?.trim()
                    )
                    
                    // Update ONLY timestamp fields, keep EVERYTHING else (guid, transcription, checksum, duration, generationParams)
                    val updatedTags = normalizedExistingTags.copy(
                        createdOnDate = org.fossify.musicplayer.helpers.ID3TagsHelper.formatDate(timestampUnix),
                        createdOnTimestamp = timestampUnix
                    )
                    
                    Log.d("MusicPlayer", "  Updated timestamp: ${updatedTags.createdOnDate}, ${updatedTags.createdOnTimestamp}")
                    Log.d("MusicPlayer", "  Preserved - GUID: ${updatedTags.guid?.take(8)}, Transcription: ${updatedTags.transcription?.take(30)}")
                    
                    // Write updated tags (preserves all fields)
                    val success = tagsWriter.writeAllTags(file, updatedTags)
                    if (success) {
                        // VERIFY: Read tags back to ensure nothing was lost
                        val verifyTags = tagsReader.readAllTags(file)
                        if (verifyTags == null) {
                            Log.e("MusicPlayer", "  ❌ CRITICAL: Cannot read tags after write!")
                            runOnUiThread {
                                toast("CRITICAL ERROR: Tags lost after write! File: ${file.name}")
                            }
                            errorCount++
                            return@forEachIndexed
                        }
                        
                        // Check if critical fields are preserved (compare with NORMALIZED existing tags)
                        // For tags that were NULL before (no tag existed), accept any non-null value after write
                        // For tags that existed before, they must match exactly after write
                        val guidPreserved = if (normalizedExistingTags.guid == null) {
                            // Tag didn't exist before - just verify it was written successfully (can be any value)
                            true // Accept whatever was written (null or non-null)
                        } else {
                            // Tag existed before - must be preserved exactly
                            verifyTags.guid == normalizedExistingTags.guid
                        }
                        
                        val transcriptionPreserved = if (normalizedExistingTags.transcription == null) {
                            true // Accept whatever was written
                        } else {
                            verifyTags.transcription == normalizedExistingTags.transcription
                        }
                        
                        val checksumPreserved = if (normalizedExistingTags.checksumAudio == null) {
                            true // Accept whatever was written
                        } else {
                            verifyTags.checksumAudio == normalizedExistingTags.checksumAudio
                        }
                        
                        val durationPreserved = if (normalizedExistingTags.duration == null) {
                            true // Accept whatever was written
                        } else {
                            verifyTags.duration == normalizedExistingTags.duration
                        }
                        
                        val paramsPreserved = if (normalizedExistingTags.generationParams == null) {
                            true // Accept whatever was written
                        } else {
                            verifyTags.generationParams == normalizedExistingTags.generationParams
                        }
                        
                        if (!guidPreserved || !transcriptionPreserved || !checksumPreserved || !durationPreserved || !paramsPreserved) {
                            Log.e("MusicPlayer", "  ❌ CRITICAL: Tags corrupted after write!")
                            Log.e("MusicPlayer", "    GUID preserved: $guidPreserved")
                            Log.e("MusicPlayer", "      Before (trimmed): '${normalizedExistingTags.guid}' (len=${normalizedExistingTags.guid?.length})")
                            Log.e("MusicPlayer", "      Before (raw): '${existingTags.guid}' (len=${existingTags.guid?.length})")
                            Log.e("MusicPlayer", "      After:  '${verifyTags.guid}' (len=${verifyTags.guid?.length})")
                            Log.e("MusicPlayer", "    Transcription preserved: $transcriptionPreserved")
                            Log.e("MusicPlayer", "      Before (trimmed): '${normalizedExistingTags.transcription?.take(50)}...' (len=${normalizedExistingTags.transcription?.length})")
                            Log.e("MusicPlayer", "      Before (raw): '${existingTags.transcription?.take(50)}...' (len=${existingTags.transcription?.length})")
                            Log.e("MusicPlayer", "      After:  '${verifyTags.transcription?.take(50)}...' (len=${verifyTags.transcription?.length})")
                            Log.e("MusicPlayer", "    Checksum preserved: $checksumPreserved")
                            Log.e("MusicPlayer", "      Before (trimmed): '${normalizedExistingTags.checksumAudio}' (len=${normalizedExistingTags.checksumAudio?.length})")
                            Log.e("MusicPlayer", "      Before (raw): '${existingTags.checksumAudio}' (len=${existingTags.checksumAudio?.length})")
                            Log.e("MusicPlayer", "      After:  '${verifyTags.checksumAudio}' (len=${verifyTags.checksumAudio?.length})")
                            Log.e("MusicPlayer", "    Duration preserved: $durationPreserved")
                            Log.e("MusicPlayer", "    GenerationParams preserved: $paramsPreserved")
                            
                            Log.e("MusicPlayer", "🛑 STOPPING fixElevenLabsTimestamps - data integrity compromised!")
                            
                            runOnUiThread {
                                toast("CRITICAL ERROR: Process stopped at file ${index+1}/${elevenLabsTracks.size}\nData integrity check failed!")
                            }
                            
                            // STOP THE ENTIRE PROCESS
                            throw Exception("Tags verification failed for ${file.name}: GUID=$guidPreserved, Trans=$transcriptionPreserved, Check=$checksumPreserved")
                        }
                        
                        Log.d("MusicPlayer", "  ✅ VERIFIED: All tags preserved correctly")
                        Log.d("MusicPlayer", "    GUID: ${verifyTags.guid?.take(8)}, Transcription: ${verifyTags.transcription?.take(30)}")
                        
                        // Set file lastModified to Unix timestamp
                        val lastModSuccess = file.setLastModified(timestampUnix * 1000)
                        
                        // UPDATE DATABASE: Update tag_txxx_created_at_unix AND added_at_timestamp_unix
                        val db = org.fossify.musicplayer.databases.SongsDatabase.getInstance(this@MainActivity)
                        db.SongsDao().updateTimestamps(
                            timestamp = timestampUnix,
                            dateAdded = timestampUnix.toInt(),
                            id = track.id
                        )
                        
                        // Verify DB update
                        val updatedTrack = db.SongsDao().getTrackWithMediaStoreId(track.mediaStoreId)
                        val timestampMatch = updatedTrack?.tagTxxxCreatedAtUnix == timestampUnix
                        val dateAddedMatch = updatedTrack?.addedAtTimestampUnix == timestampUnix.toInt()
                        
                        if (timestampMatch && dateAddedMatch) {
                            fixedCount++
                            Log.d("MusicPlayer", "  ✅ Fixed successfully (file lastModified: $lastModSuccess, DB verified: trackId=${track.id})")
                            Log.d("MusicPlayer", "     tag_txxx_created_at_unix: $timestampUnix, added_at_timestamp_unix: ${timestampUnix.toInt()}")
                        } else {
                            Log.e("MusicPlayer", "  ❌ CRITICAL: DB update failed!")
                            Log.e("MusicPlayer", "     Timestamp match: $timestampMatch (expected: $timestampUnix, got: ${updatedTrack?.tagTxxxCreatedAtUnix})")
                            Log.e("MusicPlayer", "     DateAdded match: $dateAddedMatch (expected: ${timestampUnix.toInt()}, got: ${updatedTrack?.addedAtTimestampUnix})")
                            runOnUiThread {
                                toast("CRITICAL: DB update failed for ${file.name}")
                            }
                            throw Exception("DB update verification failed")
                        }
                    } else {
                        Log.e("MusicPlayer", "  ❌ Failed to write tags")
                        errorCount++
                    }
                    
                } catch (e: Exception) {
                    val fileName = track.path.substringAfterLast('/')
                    when {
                        e.message?.contains("do not have permissions") == true -> {
                            Log.w("MusicPlayer", "  ⚠️  SKIP: No write permission for $fileName")
                            errorCount++
                        }
                        e.message?.contains("CannotWrite") == true -> {
                            Log.w("MusicPlayer", "  ⚠️  SKIP: Cannot write to $fileName")
                            errorCount++
                        }
                        else -> {
                            Log.e("MusicPlayer", "  ❌ ERROR: ${e.message} for $fileName", e)
                            errorCount++
                        }
                    }
                }
            }
            
            Log.i("MusicPlayer", "=== fixElevenLabsTimestamps Complete ===")
            Log.i("MusicPlayer", "Total: ${elevenLabsTracks.size}, Fixed: $fixedCount, Skipped: $skippedCount, Errors: $errorCount")
            
            runOnUiThread {
                val message = when {
                    errorCount == 0 -> "✅ Fixed $fixedCount/${elevenLabsTracks.size} timestamps"
                    errorCount < 10 -> "Fixed $fixedCount/${elevenLabsTracks.size} timestamps ($errorCount permission errors)"
                    else -> "Fixed $fixedCount/${elevenLabsTracks.size} timestamps ($errorCount errors - mostly permission issues in /Download)"
                }
                toast(message)
            }
            
        } catch (e: Exception) {
            Log.e("MusicPlayer", "FATAL error in fixElevenLabsTimestamps", e)
            runOnUiThread {
                toast("Error fixing timestamps: ${e.message}")
            }
        }
    }
}
