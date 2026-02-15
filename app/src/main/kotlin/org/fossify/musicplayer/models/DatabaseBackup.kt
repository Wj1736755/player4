package org.fossify.musicplayer.models

import java.io.File

data class DatabaseBackup(
    val file: File,
    val displayName: String,
    val size: Long,
    val isCurrent: Boolean = false
)


