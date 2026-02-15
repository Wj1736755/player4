package org.fossify.musicplayer.domain.model

data class PlaylistDomain(
    val id: Int,
    val title: String,
    val trackCount: Int = 0
)

data class PlaylistWithTracks(
    val playlist: PlaylistDomain,
    val tracks: List<TrackDomain>
)
