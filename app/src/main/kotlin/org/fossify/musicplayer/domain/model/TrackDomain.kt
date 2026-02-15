package org.fossify.musicplayer.domain.model

import java.util.UUID

data class TrackDomain(
    val guid: UUID,
    val mediaStoreId: Long,
    val path: String,
    val duration: Int,
    val folderName: String,
    val year: Int,
    val playlistId: Int
) {
    val title: String
        get() = path.substringAfterLast('/')
}
