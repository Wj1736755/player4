package org.fossify.musicplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.databinding.ItemDatabaseBackupBinding
import org.fossify.musicplayer.models.DatabaseBackup

class DatabaseBackupsAdapter(
    private val activity: SimpleActivity,
    private val backups: List<DatabaseBackup>
) : RecyclerView.Adapter<DatabaseBackupsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDatabaseBackupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val backup = backups[position]
        holder.bind(backup)
    }

    override fun getItemCount() = backups.size

    inner class ViewHolder(private val binding: ItemDatabaseBackupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(backup: DatabaseBackup) {
            binding.apply {
                backupName.text = backup.displayName
                backupSize.text = backup.size.formatSize()
                
                val textColor = activity.getProperTextColor()
                backupName.setTextColor(textColor)
                backupSize.setTextColor(textColor)
                
                if (backup.isCurrent) {
                    backupName.setTextColor(activity.getProperPrimaryColor())
                }
            }
        }
    }
}

