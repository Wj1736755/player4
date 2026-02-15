package org.fossify.musicplayer.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.fossify.musicplayer.R
import java.io.File
import kotlin.math.roundToInt

class GeneratedAudioAdapter(
    private val onPlayClick: (File) -> Unit,
    private val onSaveClick: (File, Int) -> Unit,
    private val onSaveAndQueueClick: (File, Int) -> Unit,
    private val onDeleteClick: (File, Int) -> Unit
) : RecyclerView.Adapter<GeneratedAudioAdapter.AudioViewHolder>() {

    private val audioFiles = mutableListOf<File>()
    private var activeFileIndex: Int = -1  // Index of currently playing file

    fun updateFiles(files: List<File>) {
        audioFiles.clear()
        audioFiles.addAll(files)
        activeFileIndex = -1  // Reset active file
        notifyDataSetChanged()
    }

    fun removeFile(position: Int) {
        if (position in audioFiles.indices) {
            audioFiles.removeAt(position)
            if (activeFileIndex == position) {
                activeFileIndex = -1
            } else if (activeFileIndex > position) {
                activeFileIndex--
            }
            notifyItemRemoved(position)
        }
    }
    
    fun setActiveFile(file: File?) {
        val oldIndex = activeFileIndex
        activeFileIndex = if (file != null) {
            audioFiles.indexOf(file)
        } else {
            -1
        }
        
        // Notify changes for old and new active items
        if (oldIndex >= 0) {
            notifyItemChanged(oldIndex)
        }
        if (activeFileIndex >= 0) {
            notifyItemChanged(activeFileIndex)
        }
    }
    
    fun getFiles(): List<File> = audioFiles.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_generated_audio, parent, false)
        return AudioViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        holder.bind(audioFiles[position], position)
    }

    override fun getItemCount() = audioFiles.size

    inner class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: com.google.android.material.card.MaterialCardView = 
            itemView as com.google.android.material.card.MaterialCardView
        private val variantTitle: TextView = itemView.findViewById(R.id.audio_variant_title)
        private val fileInfo: TextView = itemView.findViewById(R.id.audio_file_info)
        private val playButton: ImageButton = itemView.findViewById(R.id.play_button)
        private val saveButton: ImageButton = itemView.findViewById(R.id.save_button)
        private val saveAndQueueButton: ImageButton = itemView.findViewById(R.id.save_and_queue_button)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)

        fun bind(file: File, position: Int) {
            val isActive = position == activeFileIndex
            
            // Read transcription from TXXX tags
            val transcription = try {
                val tags = org.fossify.musicplayer.helpers.TXXXTagsReader.readAllTags(file)
                tags?.transcription?.take(100) ?: "Variant ${position + 1}"
            } catch (e: Exception) {
                "Variant ${position + 1}"
            }
            
            variantTitle.text = "${transcription}${if (isActive) " ▶" else ""}"
            
            // Show file size and creation time
            val fileSize = formatFileSize(file.length())
            val createdTime = formatCreationTime(file.lastModified())
            fileInfo.text = "$fileSize • $createdTime"
            
            // Highlight active file with colored stroke
            if (isActive) {
                card.strokeWidth = 4
                card.strokeColor = itemView.context.getColor(android.R.color.holo_blue_dark)
            } else {
                card.strokeWidth = 0
            }

            playButton.setOnClickListener {
                onPlayClick(file)
            }

            saveButton.setOnClickListener {
                onSaveClick(file, bindingAdapterPosition)
            }

            saveAndQueueButton.setOnClickListener {
                onSaveAndQueueClick(file, bindingAdapterPosition)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(file, bindingAdapterPosition)
            }
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${(bytes / 1024.0).roundToInt()} KB"
                else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            }
        }
        
        private fun formatCreationTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60_000 -> "just now" // < 1 minute
                diff < 3600_000 -> "${(diff / 60_000).toInt()}m ago" // < 1 hour
                diff < 86400_000 -> "${(diff / 3600_000).toInt()}h ago" // < 1 day
                diff < 172800_000 -> "yesterday" // < 2 days
                else -> "${(diff / 86400_000).toInt()}d ago" // days ago
            }
        }
    }
}
