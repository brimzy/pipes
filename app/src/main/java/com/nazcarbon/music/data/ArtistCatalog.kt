package com.nazcarbon.music.data

data class MusicArtist(
    val name: String,
    val url: String,
    val thumbnailUrl: String = "",
    val description: String = "",
)

data class ArtistRelease(
    val title: String,
    val url: String,
    val thumbnailUrl: String = "",
    val trackCount: Long = 0,
    val kind: String = "Release",
    val year: Int? = null,
)

data class ArtistDiscography(
    val artist: MusicArtist,
    val releases: List<ArtistRelease>,
)
