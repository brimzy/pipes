package com.nazcarbon.music.data

object OfflinePlayback {
    fun resolve(track: MusicTrack, localUri: (String) -> String?, online: (MusicTrack) -> MusicTrack): MusicTrack {
        if (track.pageUrl.startsWith("content://") || track.pageUrl.startsWith("file://")) {
            return track.copy(streamUrl = track.pageUrl)
        }
        val uri = localUri(track.id)
        return if (uri != null) track.copy(streamUrl = uri, audioQuality = "Offline · downloaded audio") else online(track)
    }
}
