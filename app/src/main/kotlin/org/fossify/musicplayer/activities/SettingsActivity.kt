package org.fossify.musicplayer.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.IS_CUSTOMIZING_COLORS
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.commons.models.RadioItem
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.ActivitySettingsBinding
import org.fossify.musicplayer.dialogs.ManageVisibleTabsDialog
import org.fossify.musicplayer.BuildConfig
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.sendCommand
import org.fossify.musicplayer.helpers.DatabaseExporter
import org.fossify.musicplayer.helpers.DatabaseRestorer
import org.fossify.musicplayer.helpers.getPermissionToRequest
import org.fossify.musicplayer.helpers.M3U_DURATION_SEPARATOR
import org.fossify.musicplayer.helpers.M3U_ENTRY
import org.fossify.musicplayer.helpers.M3U_HEADER
import org.fossify.musicplayer.helpers.SHOW_FILENAME_ALWAYS
import org.fossify.musicplayer.helpers.SHOW_FILENAME_IF_UNAVAILABLE
import org.fossify.musicplayer.helpers.SHOW_FILENAME_NEVER
import org.fossify.musicplayer.playback.CustomCommands
import android.widget.Toast
import org.fossify.musicplayer.databases.SongsDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleControllerActivity() {

    private val binding by viewBinding(ActivitySettingsBinding::inflate)
    private val PICK_EXPORT_FOLDER_INTENT = 1001
    private val PICK_EXPORT_DATABASE_FILE_INTENT = 1002
    private val PICK_RESTORE_DATABASE_FILE_INTENT = 1003

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.settingsNestedScrollview))
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow)

        setupCustomizeColors()
        setupCustomizeWidgetColors()
        setupUseEnglish()
        setupLanguage()
        setupManageExcludedFolders()
        setupManageShownTabs()
        setupSwapPrevNext()
        setupReplaceTitle()
        setupExportAllPlaylists()
        setupExportDatabase()
        setupRestoreDatabase()
        setupAutoQueueEnabled()
        setupAutoQueueInterval()
        setupElevenLabsSettings()
        updateTextColors(binding.settingsNestedScrollview)

        arrayOf(
            binding.settingsColorCustomizationSectionLabel,
            binding.settingsGeneralSettingsLabel,
            binding.settingsAutoQueueSectionLabel,
            binding.settingsElevenlabsSectionLabel
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupCustomizeColors() = binding.apply {
        settingsColorCustomizationHolder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupCustomizeWidgetColors() {
        binding.settingsWidgetColorCustomizationHolder.setOnClickListener {
            Intent(this, WidgetConfigureActivity::class.java).apply {
                putExtra(IS_CUSTOMIZING_COLORS, true)
                startActivity(this)
            }
        }
    }

    private fun setupUseEnglish() = binding.apply {
        settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settingsUseEnglish.isChecked = config.useEnglish
        settingsUseEnglishHolder.setOnClickListener {
            settingsUseEnglish.toggle()
            config.useEnglish = settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = Locale.getDefault().displayLanguage
        settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupSwapPrevNext() = binding.apply {
        settingsSwapPrevNext.isChecked = config.swapPrevNext
        settingsSwapPrevNextHolder.setOnClickListener {
            settingsSwapPrevNext.toggle()
            config.swapPrevNext = settingsSwapPrevNext.isChecked
        }
    }

    private fun setupReplaceTitle() = binding.apply {
        settingsShowFilename.text = getReplaceTitleText()
        settingsShowFilenameHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SHOW_FILENAME_NEVER, getString(org.fossify.commons.R.string.never)),
                RadioItem(SHOW_FILENAME_IF_UNAVAILABLE, getString(R.string.title_is_not_available)),
                RadioItem(SHOW_FILENAME_ALWAYS, getString(org.fossify.commons.R.string.always))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.showFilename) {
                config.showFilename = it as Int
                settingsShowFilename.text = getReplaceTitleText()
                refreshQueueAndTracks()
            }
        }
    }

    private fun getReplaceTitleText() = getString(
        when (config.showFilename) {
            SHOW_FILENAME_NEVER -> org.fossify.commons.R.string.never
            SHOW_FILENAME_IF_UNAVAILABLE -> R.string.title_is_not_available
            else -> org.fossify.commons.R.string.always
        }
    )

    private fun setupManageShownTabs() = binding.apply {
        settingsManageShownTabsHolder.setOnClickListener {
            ManageVisibleTabsDialog(this@SettingsActivity) { result ->
                val tabsMask = config.showTabs
                if (tabsMask != result) {
                    config.showTabs = result
                    withPlayer {
                        sendCommand(CustomCommands.RELOAD_CONTENT)
                    }
                }
            }
        }
    }

    private fun setupManageExcludedFolders() {
        binding.settingsManageExcludedFoldersHolder.setOnClickListener {
            startActivity(Intent(this, ExcludedFoldersActivity::class.java))
        }
    }

    private fun setupExportAllPlaylists() {
        binding.settingsExportAllPlaylistsHolder.setOnClickListener {
            // Try to use Storage Access Framework (works on Android 5.0+)
            try {
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    
                    startActivityForResult(this, PICK_EXPORT_FOLDER_INTENT)
                }
            } catch (e: ActivityNotFoundException) {
                // Fallback: use default location if SAF is not available
                toast("System folder picker not available. Using default location...")
                handlePermission(getPermissionToRequest()) { granted ->
                    if (granted) {
                        DatabaseExporter.exportAllPlaylists(this) { success, exportedCount, failedCount, path ->
                            runOnUiThread {
                                if (success && path != null) {
                                    val message = "Exported $exportedCount playlists to: $path"
                                    if (failedCount > 0) {
                                        toast("$message\n($failedCount failed)", Toast.LENGTH_LONG)
                                    } else {
                                        toast(message, Toast.LENGTH_LONG)
                                    }
                                } else {
                                    toast("Failed to export playlists.")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        
        if (requestCode == PICK_EXPORT_FOLDER_INTENT && resultCode == Activity.RESULT_OK && resultData != null) {
            val treeUri = resultData.data
            if (treeUri != null) {
                // Take persistent permission
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                
                toast("Exporting playlists...")
                DatabaseExporter.exportAllPlaylistsToFolder(this, treeUri) { success, exportedCount, failedCount ->
                    runOnUiThread {
                        if (success) {
                            val message = "Exported $exportedCount playlists to selected folder"
                            if (failedCount > 0) {
                                toast("$message\n($failedCount failed)", Toast.LENGTH_LONG)
                            } else {
                                toast(message, Toast.LENGTH_LONG)
                            }
                        } else {
                            toast("Failed to export playlists. Please ensure you have write permission to the selected folder.")
                        }
                    }
                }
            }
        } else if (requestCode == PICK_EXPORT_DATABASE_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData != null) {
            val fileUri = resultData.data
            if (fileUri != null) {
                toast("Exporting database...")
                DatabaseExporter.exportDatabaseToFile(this, fileUri) { success ->
                    runOnUiThread {
                        if (success) {
                            toast("Database exported successfully", Toast.LENGTH_LONG)
                        } else {
                            toast("Failed to export database.")
                        }
                    }
                }
            }
        } else if (requestCode == PICK_RESTORE_DATABASE_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData != null) {
            val fileUri = resultData.data
            if (fileUri != null) {
                // First, validate the file
                val (isValid, message) = DatabaseRestorer.validateDatabaseFile(this, fileUri)
                
                if (!isValid) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Invalid Database")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                    return
                }
                
                // Show confirmation with validation info
                android.app.AlertDialog.Builder(this)
                    .setTitle("Restore Database")
                    .setMessage("$message\n\nRestore now? App will restart.")
                    .setPositiveButton("Restore") { _, _ ->
                        toast("Restoring database...")
                        
                        Thread {
                            val (success, resultMessage) = DatabaseRestorer.restoreDatabase(this, fileUri)
                            
                            runOnUiThread {
                                if (success) {
                                    toast(resultMessage, Toast.LENGTH_LONG)
                                    // Restart app after short delay
                                    DatabaseRestorer.restartApp(this)
                                } else {
                                    android.app.AlertDialog.Builder(this)
                                        .setTitle("Restore Failed")
                                        .setMessage(resultMessage)
                                        .setPositiveButton("OK", null)
                                        .show()
                                }
                            }
                        }.start()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun setupExportDatabase() {
        // Show only in debug build
        binding.settingsExportDatabaseHolder.beVisibleIf(BuildConfig.DEBUG)
        
        binding.settingsExportDatabaseHolder.setOnClickListener {
            // Try to use Storage Access Framework (works on Android 5.0+)
            try {
                // Generate filename: songs_manual_backup_dbv_<version>_yyyy_MM_dd_HH_mm.db
                val timestamp = SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.US).format(Date())
                val filename = "songs_manual_backup_dbv_${SongsDatabase.DB_VERSION}_$timestamp.db"
                
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"  // Use wildcard to allow any file type
                    putExtra(Intent.EXTRA_TITLE, filename)
                    
                    startActivityForResult(this, PICK_EXPORT_DATABASE_FILE_INTENT)
                }
            } catch (e: ActivityNotFoundException) {
                // Fallback: use default location if SAF is not available
                toast("System file picker not available. Using default location...")
                DatabaseExporter.exportDatabase(this) { success, path ->
                    runOnUiThread {
                        if (success && path != null) {
                            toast("Database exported to: $path", Toast.LENGTH_LONG)
                        } else {
                            toast("Failed to export database.")
                        }
                    }
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun setupRestoreDatabase() {
        // Show only in debug build
        binding.settingsRestoreDatabaseHolder.beVisibleIf(BuildConfig.DEBUG)
        
        binding.settingsRestoreDatabaseHolder.setOnClickListener {
            val message = """
                ⚠️ WARNING: Database Restore
                
                This will:
                • Backup current database
                • Replace it with selected file
                • Restart the app
                
                Make sure the backup file is:
                • Valid SQLite database
                • Compatible version
                • From this app
                
                Continue?
            """.trimIndent()
            
            android.app.AlertDialog.Builder(this)
                .setTitle("Restore Database")
                .setMessage(message)
                .setPositiveButton("Select Backup File") { _, _ ->
                    try {
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/x-sqlite3", "*/*"))
                            startActivityForResult(this, PICK_RESTORE_DATABASE_FILE_INTENT)
                        }
                    } catch (e: ActivityNotFoundException) {
                        toast("No file picker found")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupAutoQueueEnabled() = binding.apply {
        settingsAutoQueueEnabled.isChecked = config.autoQueueEnabled
        settingsAutoQueueEnabledHolder.setOnClickListener {
            settingsAutoQueueEnabled.toggle()
            config.autoQueueEnabled = settingsAutoQueueEnabled.isChecked
            
            if (config.autoQueueEnabled) {
                // When enabling auto-queue, reset timestamp so tracks are added immediately
                config.autoQueueLastAddedTimestamp = 0
                android.util.Log.d("SettingsActivity", "Auto-queue enabled, reset timestamp for immediate add")
            } else {
                // When disabling auto-queue, clear timestamp and notify service
                config.autoQueueLastAddedTimestamp = 0
                config.autoQueueTrackToRemoveNext = null
                android.util.Log.d("SettingsActivity", "Auto-queue disabled, cleared timestamp")
                
                // Notify PlaybackService to remove auto-added tracks from queue and clear in-memory tracking
                try {
                    val intent = android.content.Intent("org.fossify.musicplayer.CLEAR_AUTO_QUEUE")
                    sendBroadcast(intent)
                    android.util.Log.d("SettingsActivity", "Sent CLEAR_AUTO_QUEUE broadcast")
                } catch (e: Exception) {
                    android.util.Log.e("SettingsActivity", "Failed to send broadcast", e)
                }
            }
        }
    }

    private fun setupAutoQueueInterval() = binding.apply {
        settingsAutoQueueIntervalValue.text = getAutoQueueIntervalText()
        settingsAutoQueueIntervalHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(1, "1 minute"),
                RadioItem(5, "5 minutes"),
                RadioItem(10, "10 minutes"),
                RadioItem(15, "15 minutes"),
                RadioItem(30, "30 minutes"),
                RadioItem(60, "1 hour")
            )

            RadioGroupDialog(this@SettingsActivity, items, config.autoQueueIntervalMinutes) {
                config.autoQueueIntervalMinutes = it as Int
                settingsAutoQueueIntervalValue.text = getAutoQueueIntervalText()
            }
        }
    }

    private fun getAutoQueueIntervalText(): String {
        val minutes = config.autoQueueIntervalMinutes
        return when {
            minutes < 60 -> "$minutes minutes"
            minutes == 60 -> "1 hour"
            else -> "${minutes / 60} hours"
        }
    }

    private fun setupElevenLabsSettings() {
        binding.settingsElevenlabsApiKeysHolder.setOnClickListener {
            startActivity(Intent(this, ElevenLabsSettingsActivity::class.java))
        }
        
        binding.settingsElevenlabsTestHolder.setOnClickListener {
            startActivity(Intent(this, ElevenLabsTestActivity::class.java))
        }
    }
}
