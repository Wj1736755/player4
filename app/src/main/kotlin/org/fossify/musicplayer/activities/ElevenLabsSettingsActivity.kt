package org.fossify.musicplayer.activities

import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.adapters.ElevenLabsApiKeysAdapter
import org.fossify.musicplayer.databases.SongsDatabase
import org.fossify.musicplayer.databinding.ActivityElevenlabsSettingsBinding
import org.fossify.musicplayer.dialogs.AddElevenLabsKeyDialog
import org.fossify.musicplayer.helpers.ElevenLabsSettingsImporter
import org.fossify.musicplayer.models.ElevenLabsApiKey

class ElevenLabsSettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityElevenlabsSettingsBinding::inflate)
    private var adapter: ElevenLabsApiKeysAdapter? = null
    private val keys = ArrayList<ElevenLabsApiKey>()

    companion object {
        private const val PICK_DB_FOR_ELEVENLABS_IMPORT = 4001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.elevenlabsKeysList))
        setupMaterialScrollListener(binding.elevenlabsKeysList, binding.elevenlabsSettingsAppbar)
        setupToolbar()
        setupFab()
        setupImportMenu()
        loadKeys()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.elevenlabsSettingsAppbar, NavigationIcon.Arrow)
    }

    private fun setupToolbar() {
        // AppBar color is set from theme
    }

    private fun setupImportMenu() {
        binding.elevenlabsSettingsToolbar.inflateMenu(R.menu.menu_elevenlabs_settings)
        binding.elevenlabsSettingsToolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.import_elevenlabs_from_db) {
                openDbFilePickerForImport()
                true
            } else {
                false
            }
        }
    }

    private fun openDbFilePickerForImport() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/x-sqlite3", "*/*"))
                },
                PICK_DB_FOR_ELEVENLABS_IMPORT
            )
        } catch (e: ActivityNotFoundException) {
            toast("No file picker found")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_DB_FOR_ELEVENLABS_IMPORT && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            ensureBackgroundThread {
                val (success, message) = ElevenLabsSettingsImporter.importFromUri(this@ElevenLabsSettingsActivity, uri)
                runOnUiThread {
                    toast(message)
                    if (success) loadKeys()
                }
            }
        }
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
            val defaultEmail = "wojtekadamski0005@op.pl"
            if (existingKeys.none { it.email == defaultEmail }) {
                val defaultKey = ElevenLabsApiKey(
                    id = 0,
                    email = defaultEmail,
                    apiKey = "sk_4ac3d077535ca5b46273a738544626c79c7b1f999175ad1d",
                    voiceId = "ErXwobaYiN019PkySvjV",
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
