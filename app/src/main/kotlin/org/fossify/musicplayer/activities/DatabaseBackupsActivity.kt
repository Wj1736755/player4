package org.fossify.musicplayer.activities

import android.os.Bundle
import android.os.Environment
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.musicplayer.R
import org.fossify.musicplayer.adapters.DatabaseBackupsAdapter
import org.fossify.musicplayer.databinding.ActivityDatabaseBackupsBinding
import org.fossify.musicplayer.models.DatabaseBackup
import java.io.File

class DatabaseBackupsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDatabaseBackupsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.backupsRecyclerView))
        setupMaterialScrollListener(binding.backupsRecyclerView, binding.backupsAppbar)
        
        loadBackups()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.backupsAppbar, NavigationIcon.Arrow)
        
        binding.backupsCoordinator.setBackgroundColor(getProperBackgroundColor())
    }

    private fun getBackupDirectory(): File {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MusicPlayer/Backups")
    }

    private fun loadBackups() {
        ensureBackgroundThread {
            val backups = arrayListOf<DatabaseBackup>()
            val dbPath = getDatabasePath("songs.db")
            
            // Add current database as first item
            if (dbPath.exists()) {
                backups.add(
                    DatabaseBackup(
                        file = dbPath,
                        displayName = getString(R.string.current_database),
                        size = dbPath.length(),
                        isCurrent = true
                    )
                )
            }
            
            // Add backup files from external storage
            val backupDir = getBackupDirectory()
            if (backupDir.exists()) {
                backupDir.listFiles { file ->
                    file.name.startsWith("songs.db.") && 
                    file.name.matches(Regex("songs\\.db\\.\\d{4}\\.\\d{2}\\.\\d{2}\\.\\d{6}"))
                }?.sortedByDescending { it.name }
                    ?.forEach { backupFile ->
                        val versionName = backupFile.name.substringAfter("songs.db.")
                        backups.add(
                            DatabaseBackup(
                                file = backupFile,
                                displayName = versionName,
                                size = backupFile.length(),
                                isCurrent = false
                            )
                        )
                    }
            }
            
            runOnUiThread {
                if (backups.isEmpty() || backups.size == 1) {
                    val message = getString(R.string.no_backups_found) + "\n\n" + 
                                 getString(R.string.backup_location, backupDir.absolutePath)
                    binding.backupsPlaceholder.text = message
                    binding.backupsPlaceholder.setTextColor(getProperTextColor())
                    binding.backupsPlaceholder.visibility = android.view.View.VISIBLE
                    binding.backupsRecyclerView.visibility = android.view.View.GONE
                } else {
                    binding.backupsRecyclerView.apply {
                        layoutManager = LinearLayoutManager(this@DatabaseBackupsActivity)
                        adapter = DatabaseBackupsAdapter(this@DatabaseBackupsActivity, backups)
                        visibility = android.view.View.VISIBLE
                    }
                    binding.backupsPlaceholder.visibility = android.view.View.GONE
                }
            }
        }
    }
}

