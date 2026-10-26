package com.nazcarbon.music.data

import java.net.URI
import java.net.URLDecoder

/** Recognize links by host, so ordinary search terms and lookalike domains stay searches. */
object YouTubeLinks {
    data class Target(val videoId: String?, val playlistId: String?) {
        val videoUrl: String? get() = videoId?.let { "https://www.youtube.com/watch?v=$it" }
        val playlistUrl: String? get() = playlistId?.let { "https://www.youtube.com/playlist?list=$it" }
        val isRadio: Boolean get() = playlistId?.startsWith("RD") == true
    }

    fun parse(value: String): Target? {
        val input = value.trim()
        val uri = runCatching { URI(if (input.contains("://")) input else "https://$input") }.getOrNull() ?: return null
        if (uri.scheme !in setOf("https", "http") || uri.userInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be")) return null
        val params = runCatching { uri.rawQuery.orEmpty().split('&').associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        } }.getOrNull() ?: return null
        val path = uri.path.orEmpty().trim('/').split('/')
        val video = when {
            host.endsWith("youtu.be") -> path.firstOrNull()
            path.firstOrNull() in setOf("shorts", "live", "embed") -> path.getOrNull(1)
            uri.path == "/watch" -> params["v"]
            else -> null
        }?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
        val playlist = params["list"]?.takeIf { Regex("[A-Za-z0-9_-]{2,200}").matches(it) }
        return if (video != null || playlist != null) Target(video, playlist) else null
    }
}
