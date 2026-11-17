package com.nazcarbon.music.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

internal data class LyricLine(val startMs: Long, val text: String)

internal data class TrackLyrics(val timed: List<LyricLine>, val plain: String) {
    fun activeLine(positionMs: Long): Int {
        val index = timed.binarySearch { it.startMs.compareTo(positionMs) }
        return if (index >= 0) index else -index - 2
    }

    companion object {
        private val timestamp = Regex("""\[(\d{1,5}):([0-5]\d)(?:[.:](\d{1,3}))?]""")
        private val offsetTag = Regex("""\[offset:([+-]?\d{1,9})]""", RegexOption.IGNORE_CASE)

        fun parse(synced: String, plain: String): TrackLyrics {
            val offset = offsetTag.find(synced)?.groupValues?.get(1)?.toLong() ?: 0L
            val timed = synced.lineSequence().flatMap { row ->
                val line = row.trimStart()
                val starts = mutableListOf<Long>()
                var textStart = 0
                while (true) {
                    val match = timestamp.matchAt(line, textStart) ?: break
                    val (minutes, seconds, fraction) = match.destructured
                    starts += minutes.toLong() * 60_000 + seconds.toLong() * 1_000 +
                        fraction.padEnd(3, '0').toLong() + offset
                    textStart = match.range.last + 1
                }
                val text = line.substring(textStart).trim()
                starts.map { LyricLine(it, text) }
            }.groupBy { it.startMs }.map { (start, lines) ->
                LyricLine(start, lines.map { it.text }.filter { it.isNotBlank() }.distinct().joinToString("\n"))
            }.sortedBy { it.startMs }
            return TrackLyrics(timed.takeIf { lines -> lines.any { it.text.isNotBlank() } }.orEmpty(), plain.trim())
        }
    }
}

internal object LyricsSource {
    fun fetch(track: MusicTrack, seconds: Long): TrackLyrics? {
        if (track.title.isBlank() || track.artist.isBlank()) return null
        val parameters = buildMap {
            put("track_name", track.title)
            put("artist_name", track.artist.removeSuffix(" - Topic"))
            if (seconds in 1..3600) put("duration", seconds.toString())
        }.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        val connection = URI("https://lrclib.net/api/get?$parameters").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "Pipes Music (https://github.com/brimzy/pipes)")
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
            val record = connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            if (record.optBoolean("instrumental")) return null
            return TrackLyrics.parse(
                record.optString("syncedLyrics").takeUnless { record.isNull("syncedLyrics") }.orEmpty(),
                record.optString("plainLyrics").takeUnless { record.isNull("plainLyrics") }.orEmpty(),
            ).takeIf { it.timed.isNotEmpty() || it.plain.isNotBlank() }
        } finally {
            connection.disconnect()
        }
    }
}
