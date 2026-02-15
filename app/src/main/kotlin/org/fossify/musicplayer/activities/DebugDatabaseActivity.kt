package org.fossify.musicplayer.activities

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databases.SongsDatabase

/**
 * Debug activity to check database state
 * Accessible from Settings → ElevenLabs Settings (long press on title)
 */
class DebugDatabaseActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val textView = TextView(this).apply {
            textSize = 14f
            setTextIsSelectable(true)
        }
        
        val refreshButton = Button(this).apply {
            text = "Refresh"
            setOnClickListener {
                loadDatabaseInfo(textView)
            }
        }
        
        layout.addView(refreshButton)
        layout.addView(textView)
        setContentView(layout)
        
        loadDatabaseInfo(textView)
    }
    
    private fun loadDatabaseInfo(textView: TextView) {
        Thread {
            val db = SongsDatabase.getInstance(this)
            val allKeys = db.ElevenLabsApiKeyDao().getAll()
            val activeKey = db.ElevenLabsApiKeyDao().getActive()
            
            val info = buildString {
                appendLine("=== DATABASE DEBUG INFO ===\n")
                appendLine("Total keys: ${allKeys.size}\n")
                appendLine("Active key: ${activeKey?.email ?: "NONE"}\n")
                appendLine("Active key ID: ${activeKey?.id ?: "N/A"}\n")
                appendLine("\n=== ALL KEYS ===\n")
                
                allKeys.forEach { key ->
                    appendLine("ID: ${key.id}")
                    appendLine("Email: ${key.email}")
                    appendLine("Key: ${key.apiKey.take(10)}...")
                    appendLine("Active: ${key.isActive}")
                    appendLine("---")
                }
                
                if (allKeys.isEmpty()) {
                    appendLine("No keys in database!")
                }
            }
            
            runOnUiThread {
                textView.text = info
            }
        }.start()
    }
}
