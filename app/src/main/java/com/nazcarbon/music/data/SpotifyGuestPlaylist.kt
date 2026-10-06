package com.nazcarbon.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.Base64
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Public playlist metadata through the web player's anonymous session. No user credentials. */
internal object SpotifyGuestPlaylist {
    private const val PAGE_SIZE = 343
    private const val ORIGIN = "https://open.spotify.com"
    private const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    private val lock = Any()
    private var cachedBootstrap: Pair<Long, Bootstrap>? = null
    private var cachedSession: Session? = null
    private val playlists = object : LinkedHashMap<String, Pair<Long, SpotifyPlaylistPreview>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, SpotifyPlaylistPreview>>?) = size > 8
    }

    internal data class Bootstrap(val clientVersion: String, val publicValue: String, val valueVersion: Int, val playlistHash: String)
    private data class Session(val bootstrap: Bootstrap, val accessToken: String, val clientToken: String, val expires: Long)
    internal data class Page(val name: String, val total: Int, val offset: Int, val rowCount: Int,
        val revision: String, val rowIds: List<String>, val tracks: List<SpotifyPlaylistTrack>, val skipped: Int)
    private class SessionChanged : IllegalStateException("Spotify's guest session changed. Retry the import.")

    suspend fun load(id: String, progress: (Int, Int) -> Unit = { _, _ -> }): SpotifyPlaylistPreview {
        synchronized(lock) {
            playlists[id]?.takeIf { System.currentTimeMillis() - it.first < 5 * 60 * 1000 }?.let {
                progress(it.second.totalTracks, it.second.totalTracks)
                return it.second
            }
        }
        repeat(2) { attempt ->
            try {
                val session = runInterruptible(Dispatchers.IO) { session() }
                val result = loadPages(progress) { offset -> runInterruptible(Dispatchers.IO) { requestPage(session, id, offset) } }
                synchronized(lock) { playlists[id] = System.currentTimeMillis() to result }
                return result
            } catch (changed: SessionChanged) {
                synchronized(lock) { cachedBootstrap = null; cachedSession = null }
                if (attempt == 1) throw changed
            }
        }
        error("Couldn't open Spotify's public playlist session.")
    }

    /** Read all raw rows, including unavailable entries, before declaring an import complete. */
    internal suspend fun loadPages(progress: (Int, Int) -> Unit = { _, _ -> }, fetch: suspend (Int) -> Page): SpotifyPlaylistPreview = coroutineScope {
        val first = fetch(0)
        require(first.offset == 0 && first.total >= 0 && first.rowCount <= first.total) { "Spotify returned an invalid playlist page." }
        require(first.name.isNotBlank()) { "Spotify didn't return a playlist name." }
        require(first.rowCount > 0 || first.total == 0) { "Spotify returned an empty playlist page. Retry the import." }
        progress(first.rowCount, first.total)
        val completed = AtomicInteger(first.rowCount)
        val semaphore = Semaphore(3)
        val offsets = if (first.rowCount == 0) emptyList() else (first.rowCount until first.total step first.rowCount).toList()
        val remaining = offsets.map { offset -> async {
            semaphore.withPermit {
                val page = fetch(offset)
                require(page.offset == offset && page.total == first.total && page.revision == first.revision) {
                    "This playlist changed while importing. Retry to read a consistent list."
                }
                require(page.rowCount == minOf(first.rowCount, first.total - offset)) {
                    "Spotify didn't return the complete playlist page. Retry the import."
                }
                synchronized(completed) { progress(completed.addAndGet(page.rowCount), first.total) }
                page
            }
        } }.awaitAll()
        val pages = listOf(first) + remaining
        require(pages.sumOf { it.rowCount } == first.total) { "Spotify didn't return every playlist item. Retry the import." }
        val rowIds = pages.flatMap { it.rowIds }
        require(rowIds.size == rowIds.distinct().size) { "Spotify repeated a playlist page. Retry the import." }
        SpotifyPlaylistPreview(first.name, pages.flatMap { it.tracks }, first.total, pages.sumOf { it.skipped })
    }

    private fun session(): Session = synchronized(lock) {
        cachedSession?.takeIf { System.currentTimeMillis() < it.expires }?.let { return it }
        val bootstrap = cachedBootstrap?.takeIf { System.currentTimeMillis() - it.first < 6 * 60 * 60 * 1000 }?.second
            ?: discoverBootstrap().also { cachedBootstrap = System.currentTimeMillis() to it }
        val serverTime = JSONObject(request("$ORIGIN/api/server-time")).getLong("serverTime")
        val query = mapOf("reason" to "init", "productType" to "web-player",
            "totp" to guestCode(bootstrap.publicValue, System.currentTimeMillis() / 1000),
            "totpServer" to guestCode(bootstrap.publicValue, serverTime), "totpVer" to bootstrap.valueVersion.toString())
        val token = JSONObject(request("$ORIGIN/api/token?${query.encode()}", extra = mapOf("Spotify-App-Version" to bootstrap.clientVersion)))
        if (!token.has("accessToken") || token.has("totpVerExpired")) throw SessionChanged()
        require(token.optBoolean("isAnonymous")) { "Spotify didn't issue an anonymous session. No sign-in was attempted." }
        val access = token.getString("accessToken")
        val clientPayload = JSONObject().put("client_data", JSONObject().put("client_version", bootstrap.clientVersion)
            .put("client_id", token.getString("clientId")).put("js_sdk_data", JSONObject()
                .put("device_brand", "unknown").put("device_model", "unknown").put("os", "linux")
                .put("os_version", "unknown").put("device_type", "computer")))
        val client = JSONObject(request("https://clienttoken.spotify.com/v1/clienttoken", clientPayload))
            .optJSONObject("granted_token") ?: error("Spotify couldn't start a guest session. Retry in a moment.")
        val expires = minOf(token.getLong("accessTokenExpirationTimestampMs"),
            System.currentTimeMillis() + client.optLong("expires_after_seconds", 3600) * 1000) - 60_000
        Session(bootstrap, access, client.getString("token"), expires).also { cachedSession = it }
    }

    private fun discoverBootstrap(): Bootstrap {
        val html = request(ORIGIN)
        val configText = Regex("""<script[^>]*id="appServerConfig"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: error("Spotify's public player configuration couldn't be read.")
        val config = JSONObject(String(Base64.getDecoder().decode(configText), Charsets.UTF_8))
        val bundleUrl = Regex("""<script[^>]*src="(https://open\.spotifycdn\.com/cdn/build/web-player/web-player\.[a-f0-9]+\.js)"""
            ).find(html)?.groupValues?.get(1) ?: error("Spotify's public player bundle couldn't be found.")
        return parseBootstrap(config.getString("clientVersion"), request(bundleUrl))
    }

    internal fun parseBootstrap(clientVersion: String, js: String): Bootstrap {
        val entry = Regex("""\{secret:("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'),version:(\d+)\}""")
            .findAll(js).maxByOrNull { it.groupValues[2].toInt() }
            ?: error("Spotify's guest-session configuration changed. Retry later.")
        val hash = Regex(""""fetchPlaylist","query","([a-f0-9]{64})"""")
            .find(js)?.groupValues?.get(1) ?: error("Spotify's playlist query changed. Retry later.")
        return Bootstrap(clientVersion, decodeJsString(entry.groupValues[1]), entry.groupValues[2].toInt(), hash)
    }

    internal fun decodeJsString(literal: String): String {
        val input = literal.substring(1, literal.length - 1)
        val result = StringBuilder()
        var index = 0
        while (index < input.length) {
            val char = input[index++]
            if (char != '\\') { result.append(char); continue }
            require(index < input.length) { "Invalid public player string." }
            when (val escape = input[index++]) {
                'x', 'u' -> {
                    val length = if (escape == 'x') 2 else 4
                    result.append(input.substring(index, index + length).toInt(16).toChar())
                    index += length
                }
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                else -> result.append(escape)
            }
        }
        return result.toString()
    }

    internal fun guestCode(publicValue: String, epochSeconds: Long): String {
        val key = publicValue.mapIndexed { index, char -> char.code xor ((index % 33) + 9) }
            .joinToString("").toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }
        val digest = mac.doFinal(ByteBuffer.allocate(8).putLong(epochSeconds / 30).array())
        val offset = digest.last().toInt() and 15
        val number = ByteBuffer.wrap(digest, offset, 4).int and Int.MAX_VALUE
        return String.format(Locale.US, "%06d", number % 1_000_000)
    }

    private fun requestPage(session: Session, id: String, offset: Int): Page {
        val variables = JSONObject().put("uri", "spotify:playlist:$id").put("offset", offset).put("limit", PAGE_SIZE)
            .put("enableWatchFeedEntrypoint", false).put("includeEpisodeContentRatingsV2", false)
        val extensions = JSONObject().put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", session.bootstrap.playlistHash))
        val params = mapOf("operationName" to "fetchPlaylist", "variables" to variables.toString(), "extensions" to extensions.toString())
        val response = JSONObject(request("https://api-partner.spotify.com/pathfinder/v1/query?${params.encode()}", extra = mapOf(
            "Authorization" to "Bearer ${session.accessToken}", "Client-Token" to session.clientToken,
            "Spotify-App-Version" to session.bootstrap.clientVersion, "App-Platform" to "WebPlayer")))
        val errors = response.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            val message = errors.optJSONObject(0)?.optString("message").orEmpty()
            if (message.contains("persisted", true) || message.contains("hash", true)) throw SessionChanged()
            error("Spotify couldn't read this public playlist. Check the link and retry.")
        }
        return parsePage(response, offset)
    }

    internal fun parsePage(response: JSONObject, offset: Int): Page {
        val playlist = response.getJSONObject("data").getJSONObject("playlistV2")
        require(playlist.optString("__typename", "Playlist") == "Playlist") { "This Spotify playlist is private or unavailable." }
        val content = playlist.getJSONObject("content")
        val items = content.getJSONArray("items")
        val actualOffset = content.optJSONObject("pagingInfo")?.optInt("offset", offset) ?: offset
        val tracks = mutableListOf<SpotifyPlaylistTrack>()
        val rowIds = mutableListOf<String>()
        for (index in 0 until items.length()) {
            val row = items.getJSONObject(index)
            row.optString("uid").takeIf { it.isNotBlank() }?.let { rowIds += it }
            val item = row.optJSONObject("itemV2")?.optJSONObject("data") ?: continue
            if (item.optString("__typename") != "Track") continue
            val id = item.optString("uri").substringAfterLast(':')
            val name = item.optString("name").trim()
            val artistRows = item.optJSONObject("artists")?.optJSONArray("items") ?: continue
            val artists = (0 until artistRows.length()).mapNotNull { artistIndex ->
                artistRows.optJSONObject(artistIndex)?.optJSONObject("profile")?.optString("name")?.takeIf { it.isNotBlank() }
            }.joinToString(", ")
            if (!Regex("[A-Za-z0-9]{22}").matches(id) || name.isBlank() || artists.isBlank()) continue
            val images = item.optJSONObject("albumOfTrack")?.optJSONObject("coverArt")?.optJSONArray("sources")
            val artwork = images?.let { rows -> (0 until rows.length()).mapNotNull(rows::optJSONObject)
                .maxByOrNull { it.optInt("width") }?.optString("url") }.orEmpty()
            tracks += SpotifyPlaylistTrack(name, artists, id,
                (item.optJSONObject("trackDuration")?.optLong("totalMilliseconds") ?: 0) / 1000, artwork, offset + index)
        }
        return Page(playlist.getString("name"), content.getInt("totalCount"), actualOffset, items.length(),
            playlist.optString("revisionId"), rowIds, tracks, items.length() - tracks.size)
    }

    private fun Map<String, String>.encode() = entries.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}" }

    private fun request(url: String, payload: JSONObject? = null, extra: Map<String, String> = emptyMap()): String {
        repeat(3) { attempt ->
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Origin", ORIGIN)
                setRequestProperty("Referer", "$ORIGIN/")
                extra.forEach { (key, value) -> setRequestProperty(key, value) }
                if (payload != null) {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            try {
                payload?.let { connection.outputStream.use { stream -> stream.write(it.toString().toByteArray(Charsets.UTF_8)) } }
                val status = connection.responseCode
                if (status == 401) throw SessionChanged()
                if ((status == 429 || status in 500..599) && attempt < 2) {
                    val seconds = connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1, 15) ?: (attempt + 1).toLong()
                    connection.disconnect()
                    Thread.sleep(seconds * 1000)
                } else {
                    require(status in 200..299) { when (status) {
                        403 -> "Spotify couldn't start a guest import. Retry in a moment."
                        429 -> "Spotify is rate limiting imports. Wait a moment and retry."
                        else -> "Spotify playlist request failed ($status). Retry the import."
                    } }
                    return connection.inputStream.bufferedReader().use { it.readText() }
                }
            } finally { connection.disconnect() }
        }
        error("Spotify playlist request couldn't finish.")
    }
}
