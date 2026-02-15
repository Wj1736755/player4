package org.fossify.musicplayer.domain.model

import java.util.UUID

data class AudioMetadata(
    val guid: UUID,
    val title: String,
    val artist: String,
    val album: String,
    val voiceId: String?,
    val voiceName: String?,
    val model: String?,
    val generatedAt: Long,
    val transcription: String
)
