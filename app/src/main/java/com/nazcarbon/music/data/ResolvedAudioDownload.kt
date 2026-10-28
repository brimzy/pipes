package com.nazcarbon.music.data

import java.net.URI

/** Only extractor-resolved, progressive audio on the playback CDN can bypass file-link validation. */
data class ResolvedAudioDownload(val url: String, val mimeType: String, val extension: String) {
    fun validated(): ResolvedAudioDownload {
        val uri = URI(url)
        val host = uri.host?.lowercase().orEmpty()
        require(uri.scheme == "https" && host.endsWith(".googlevideo.com") &&
            uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null) { "This recording has no downloadable audio source." }
        require(mimeType in setOf("audio/mp4", "audio/webm", "audio/ogg", "audio/opus", "audio/mpeg") &&
            extension in setOf("m4a", "mp4", "webm", "ogg", "opus", "mp3")) { "This audio format cannot be downloaded." }
        return this
    }
}
