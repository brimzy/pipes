package com.nazcarbon.music.data

object OfflinePlayback {
    fun isLocal(track: MusicTrack): Boolean =
        track.pageUrl.startsWith("content:", ignoreCase = true) || track.pageUrl.startsWith("file:", ignoreCase = true)

    fun resolve(track: MusicTrack, localUri: (String) -> String?, online: (MusicTrack) -> MusicTrack): MusicTrack {
        if (isLocal(track)) {
            return track.copy(streamUrl = track.pageUrl)
        }
        val uri = localUri(track.id)
        return if (uri != null) track.copy(streamUrl = uri, audioQuality = "Offline · downloaded audio") else online(track)
    }
}
