package com.nazcarbon.music.data

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AudioDownload(
    val id: Long,
    val title: String,
    val artist: String,
    val category: String,
    val mimeType: String,
)

data class AudioDownloadStatus(
    val status: Int,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val reason: Int = 0,
) {
    /** DownloadManager reports byte counts that can be unknown until the response headers arrive. */
    val progress: Float?
        get() = totalBytes.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
}

/** Keeps DownloadManager jobs and the app's category metadata in sync across app restarts. */
object AudioDownloads {
    private const val PREF_KEY = "audio_downloads"
    private val supportedTypes = mapOf(
        "flac" to "audio/flac",
        "wav" to "audio/wav",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "opus" to "audio/opus",
        "aiff" to "audio/aiff",
        "aif" to "audio/aiff",
        "alac" to "audio/alac",
    )

    /** Accept direct audio files only; streaming watch pages do not provide a lossless file to save. */
    fun validateUrl(value: String): Pair<Uri, String> {
        val uri = Uri.parse(value.trim())
        require(uri.scheme.equals("https", ignoreCase = true)) { "Use a secure HTTPS link to an audio file." }
        val host = uri.host?.lowercase().orEmpty()
        require(host.isNotBlank()) { "Enter a valid audio file URL." }
        require(host != "youtube.com" && !host.endsWith(".youtube.com") && host != "youtu.be" && host != "music.youtube.com") {
            "YouTube Music offers compressed streams, not lossless audio downloads. Use a direct file link from a source that permits downloads."
        }
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val mimeType = supportedTypes[extension]
        require(mimeType != null) { "Use a direct link ending in FLAC, WAV, MP3, M4A, AAC, OGG, OPUS, AIFF, or ALAC." }
        return uri to mimeType
    }

    /** Store the transfer in this app's music directory so completed files remain playable offline. */
    fun enqueue(
        context: Context,
        manager: DownloadManager,
        url: String,
        title: String,
        artist: String,
        category: String,
    ): AudioDownload {
        val (uri, mimeType) = validateUrl(url)
        val cleanTitle = title.trim().ifBlank { uri.lastPathSegment?.substringBeforeLast('.') ?: "Audio download" }
        val cleanArtist = artist.trim().ifBlank { "Unknown artist" }
        val cleanCategory = category.trim().ifBlank { "Unsorted" }
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val fileName = "${UUID.randomUUID()}.$extension"
        val request = DownloadManager.Request(uri)
            .setTitle(cleanTitle)
            .setDescription("$cleanArtist · $cleanCategory")
            .setMimeType(mimeType)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, fileName)
        val id = manager.enqueue(request)
        return AudioDownload(id, cleanTitle, cleanArtist, cleanCategory, mimeType)
    }

    fun load(preferences: SharedPreferences): List<AudioDownload> = runCatching {
        val json = JSONArray(preferences.getString(PREF_KEY, "[]"))
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            AudioDownload(
                id = item.getLong("id"),
                title = item.getString("title"),
                artist = item.getString("artist"),
                category = item.optString("category", "Unsorted"),
                mimeType = item.optString("mimeType", "audio/mpeg"),
            )
        }
    }.getOrDefault(emptyList())

    /** The source URL is deliberately omitted because signed download links can contain credentials. */
    fun save(preferences: SharedPreferences, downloads: List<AudioDownload>) {
        val json = JSONArray().apply {
            downloads.forEach { download ->
                put(JSONObject().apply {
                    put("id", download.id)
                    put("title", download.title)
                    put("artist", download.artist)
                    put("category", download.category)
                    put("mimeType", download.mimeType)
                })
            }
        }
        preferences.edit().putString(PREF_KEY, json.toString()).apply()
    }

    fun status(manager: DownloadManager, id: Long): AudioDownloadStatus? = runCatching {
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            AudioDownloadStatus(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        }
    }.getOrNull()
}
