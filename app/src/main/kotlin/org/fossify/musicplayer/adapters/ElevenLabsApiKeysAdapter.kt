package org.fossify.musicplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.databinding.ItemElevenlabsApiKeyBinding
import org.fossify.musicplayer.models.ElevenLabsApiKey

class ElevenLabsApiKeysAdapter(
    private val activity: SimpleActivity,
    private var keys: ArrayList<ElevenLabsApiKey>,
    private val onItemClick: (ElevenLabsApiKey) -> Unit,
    private val onEditClick: (ElevenLabsApiKey) -> Unit,
    private val onDeleteClick: (ElevenLabsApiKey) -> Unit
) : RecyclerView.Adapter<ElevenLabsApiKeysAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemElevenlabsApiKeyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindView(keys[position])
    }

    override fun getItemCount() = keys.size

    fun updateItems(newKeys: ArrayList<ElevenLabsApiKey>) {
        keys = newKeys
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemElevenlabsApiKeyBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindView(key: ElevenLabsApiKey) {
            binding.apply {
                val textColor = activity.getProperTextColor()
                apiKeyEmail.text = key.email
                apiKeyEmail.setTextColor(textColor)

                // Show first 8 and last 8 characters of API key
                val maskedKey = if (key.apiKey.length > 16) {
                    "${key.apiKey.substring(0, 8)}...${key.apiKey.substring(key.apiKey.length - 8)}"
                } else {
                    key.apiKey
                }
                apiKeyPreview.text = maskedKey
                apiKeyPreview.setTextColor(textColor)

                apiKeyRadio.isChecked = key.isActive

                apiKeyHolder.setOnClickListener {
                    onItemClick(key)
                }

                apiKeyEdit.setOnClickListener {
                    onEditClick(key)
                }

                apiKeyDelete.setOnClickListener {
                    onDeleteClick(key)
                }
            }
        }
    }
}
