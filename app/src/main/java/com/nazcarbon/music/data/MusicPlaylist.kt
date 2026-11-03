package com.nazcarbon.music.data

data class MusicPlaylist(
    val name: String,
    val tracks: List<MusicTrack>,
    val sourceTotal: Int = 0,
    val sourceSkipped: Int = 0,
    val isAlbum: Boolean = false,
    val artworkUri: String = "",
    val spotifyPlaylistId: String = "",
    val spotifyTrackIds: List<String> = emptyList(),
    val lastSpotifySync: Long = 0,
)
