package org.fossify.musicplayer.activities

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.musicplayer.R
import org.fossify.musicplayer.adapters.ElevenLabsApiKeysAdapter
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.databinding.ActivityElevenlabsSettingsBinding
import org.fossify.musicplayer.dialogs.AddElevenLabsKeyDialog
import org.fossify.musicplayer.models.ElevenLabsApiKey

class ElevenLabsSettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityElevenlabsSettingsBinding::inflate)
    private var adapter: ElevenLabsApiKeysAdapter? = null
    private val keys = ArrayList<ElevenLabsApiKey>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.elevenlabsKeysList))
        setupMaterialScrollListener(binding.elevenlabsKeysList, binding.elevenlabsSettingsAppbar)
        setupToolbar()
        setupFab()
        loadKeys()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.elevenlabsSettingsAppbar, NavigationIcon.Arrow)
    }

    private fun setupToolbar() {
        // AppBar color is set from theme
    }

    private fun setupFab() {
        binding.elevenlabsAddKeyFab.setOnClickListener {
            AddElevenLabsKeyDialog(
                activity = this,
                existingKey = null
            ) { email, apiKey, voiceId ->
                addKey(email, apiKey, voiceId)
            }
        }
    }

    private fun loadKeys() {
        Thread {
            val db = SongsDatabase.getInstance(this)
            
            // Auto-add default API key if not exists
            val existingKeys = db.ElevenLabsApiKeyDao().getAll()
            val defaultEmail = "some-default@any.com"
            if (existingKeys.none { it.email == defaultEmail }) {
                val defaultKey = ElevenLabsApiKey(
                    id = 0,
                    email = defaultEmail,
                    apiKey = "some_api_key",
                    voiceId = "Some_voiceId",
                    isActive = true,
                    createdAtUtc = System.currentTimeMillis(),
                    lastUsedAtUtc = null,
                    characterLimit = 10000,
                    characterCount = 8051,
                    characterLimitRemaining = 1949,
                    nextCharacterCountResetUnix = null
                )
                db.ElevenLabsApiKeyDao().insert(defaultKey)
                android.util.Log.d("ElevenLabsSettings", "Auto-added default API key for $defaultEmail")
            }
            
            keys.clear()
            keys.addAll(db.ElevenLabsApiKeyDao().getAll())

            runOnUiThread {
                setupAdapter()
            }
        }.start()
    }

    private fun setupAdapter() {
        adapter = ElevenLabsApiKeysAdapter(
            activity = this,
            keys = keys,
            onItemClick = { key ->
                setActiveKey(key)
            },
            onEditClick = { key ->
                editKey(key)
            },
            onDeleteClick = { key ->
                deleteKey(key)
            }
        )

        binding.elevenlabsKeysList.apply {
            layoutManager = LinearLayoutManager(this@ElevenLabsSettingsActivity)
            adapter = this@ElevenLabsSettingsActivity.adapter
        }
    }

    private fun addKey(email: String, apiKey: String, voiceId: String) {
        Thread {
            val db = SongsDatabase.getInstance(this)
            val newKey = ElevenLabsApiKey(
                email = email,
                apiKey = apiKey,
                voiceId = voiceId,
                isActive = keys.isEmpty() // First key is automatically active
            )

            val id = db.runInTransaction<Long> {
                if (newKey.isActive) {
                    db.ElevenLabsApiKeyDao().deactivateAll()
                }
                db.ElevenLabsApiKeyDao().insert(newKey)
            }
            
            val insertedKey = newKey.copy(id = id)
            
            // Verify
            android.util.Log.d("ElevenLabsSettings", "Added key: ${insertedKey.email} (id: $id, active: ${insertedKey.isActive})")

            runOnUiThread {
                keys.add(0, insertedKey)
                adapter?.updateItems(keys)
                if (insertedKey.isActive) {
                    toast("API key added and activated: $email")
                } else {
                    toast(R.string.api_key_added)
                }
            }
        }.start()
    }

    private fun setActiveKey(key: ElevenLabsApiKey) {
        Thread {
            val db = SongsDatabase.getInstance(this)
            
            // Use transaction to ensure atomicity
            db.runInTransaction {
                db.ElevenLabsApiKeyDao().deactivateAll()
                db.ElevenLabsApiKeyDao().setActive(key.id)
            }
            
            // Verify the change
            val activeKey = db.ElevenLabsApiKeyDao().getActive()
            android.util.Log.d("ElevenLabsSettings", "Active key after update: ${activeKey?.email} (id: ${activeKey?.id})")

            runOnUiThread {
                keys.forEach { it.isActive = (it.id == key.id) }
                adapter?.updateItems(keys)
                toast("API key activated: ${key.email}")
            }
        }.start()
    }

    private fun editKey(key: ElevenLabsApiKey) {
        AddElevenLabsKeyDialog(
            activity = this,
            existingKey = key
        ) { email, apiKey, voiceId ->
            updateKey(key.id, email, apiKey, voiceId)
        }
    }

    private fun updateKey(id: Long, email: String, apiKey: String, voiceId: String) {
        Thread {
            android.util.Log.i("ElevenLabsSettings", "updateKey: id=$id, email=$email, voiceId=$voiceId")
            val db = SongsDatabase.getInstance(this)
            
            // Find the key in the list and update it
            val keyIndex = keys.indexOfFirst { it.id == id }
            android.util.Log.i("ElevenLabsSettings", "updateKey: keyIndex=$keyIndex")
            if (keyIndex != -1) {
                val oldKey = keys[keyIndex]
                android.util.Log.i("ElevenLabsSettings", "updateKey: oldKey voiceId=${oldKey.voiceId}")
                
                val updatedKey = keys[keyIndex].copy(
                    email = email,
                    apiKey = apiKey,
                    voiceId = voiceId
                )
                android.util.Log.i("ElevenLabsSettings", "updateKey: updatedKey voiceId=${updatedKey.voiceId}")
                
                db.ElevenLabsApiKeyDao().update(updatedKey)
                android.util.Log.i("ElevenLabsSettings", "updateKey: Database updated")
                
                // Verify update
                val verifyKey = db.ElevenLabsApiKeyDao().getActive()
                android.util.Log.i("ElevenLabsSettings", "updateKey: Verified from DB - voiceId=${verifyKey?.voiceId}")
                
                runOnUiThread {
                    keys[keyIndex] = updatedKey
                    adapter?.updateItems(keys)
                    toast(R.string.api_key_added) // Reusing string for now
                }
            } else {
                android.util.Log.e("ElevenLabsSettings", "updateKey: Key not found in list!")
            }
        }.start()
    }

    private fun deleteKey(key: ElevenLabsApiKey) {
        Thread {
            val db = SongsDatabase.getInstance(this)
            db.ElevenLabsApiKeyDao().delete(key)

            runOnUiThread {
                keys.remove(key)
                adapter?.updateItems(keys)
                toast(R.string.api_key_deleted)
            }
        }.start()
    }
}
