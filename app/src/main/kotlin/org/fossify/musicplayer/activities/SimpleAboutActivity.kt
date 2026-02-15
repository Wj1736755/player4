package org.fossify.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.musicplayer.BuildConfig
import org.fossify.musicplayer.databinding.ActivitySimpleAboutBinding

class SimpleAboutActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySimpleAboutBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.aboutNestedScrollview))
        setupMaterialScrollListener(binding.aboutNestedScrollview, binding.aboutAppbar)
        
        binding.aboutShowBackups.setOnClickListener {
            startActivity(Intent(this, DatabaseBackupsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.aboutAppbar, NavigationIcon.Arrow)
        
        binding.apply {
            aboutVersion.text = "${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}"
            aboutVersion.setTextColor(getProperTextColor())
            aboutCoordinator.setBackgroundColor(getProperBackgroundColor())
        }
    }
}

