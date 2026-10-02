package com.nazcarbon.music.data

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val streamUrl: String,
    val pageUrl: String,
)
