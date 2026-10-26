package com.nazcarbon.music.data

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

class NewPipeMusicSource {
    private companion object {
        const val MAX_RELEASE_PAGES = 100
        const val MAX_SEARCH_PAGES = 3
        const val DISCOGRAPHY_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        const val YOUTUBE_MUSIC_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    }

    private val service: StreamingService by lazy { NewPipe.getService("YouTube") }
    private val matches = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, MusicTrack>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MusicTrack>?) = size > 512
    })
    private val audioCache = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, Pair<Long, MusicTrack>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, MusicTrack>>?) = size > 64
    })
    private val discographyCache = ConcurrentHashMap<String, CachedDiscography>()

    private data class CachedDiscography(
        val loadedAtMillis: Long,
        val value: ArtistDiscography,
    )

    fun initialize() {
        NewPipe.init(AndroidDownloader())
    }

    fun searchSongs(query: String, filter: String = YoutubeSearchQueryHandlerFactory.MUSIC_SONGS): List<MusicTrack> {
        val handler: SearchQueryHandler = service.searchQHFactory.fromQuery(
            query.trim(),
            listOf(filter),
            "",
        )
        val extractor = service.getSearchExtractor(handler)
        extractor.fetchPage()
        return extractor.initialPage.items.filterIsInstance<StreamInfoItem>().map { item ->
            MusicTrack(
                id = item.url,
                title = item.name,
                artist = item.uploaderName.orEmpty().ifBlank { "YouTube Music" },
                durationSeconds = item.duration,
                thumbnailUrl = item.thumbnails.lastOrNull()?.url.orEmpty(),
                streamUrl = "",
                pageUrl = item.url,
            )
        }.distinctBy { it.id }
    }

    suspend fun searchMusic(query: String): List<MusicTrack> = coroutineScope {
        val batches = listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, YoutubeSearchQueryHandlerFactory.VIDEOS)
            .map { filter -> async(Dispatchers.IO) {
                try { Result.success(searchSongs(query, filter)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { Result.failure<List<MusicTrack>>(failure) }
            } }.awaitAll()
        if (batches.all { it.isFailure }) throw batches.first().exceptionOrNull()!!
        MusicSearchResults.merge(query, batches.map { it.getOrDefault(emptyList()) })
    }

    fun searchYouTubeLink(target: YouTubeLinks.Target): List<MusicTrack> {
        if (target.isRadio && target.videoUrl != null) {
            val seed = youtubeTrack(target.videoUrl!!)
            return listOf(seed) + relatedSongs(seed, playlistId = target.playlistId)
        }
        target.playlistUrl?.let { url ->
            val extractor = service.getPlaylistExtractor(url)
            return collectStreamItems(extractor).map { item ->
                MusicTrack(item.url, item.name, item.uploaderName.orEmpty(), item.duration,
                    item.thumbnails.lastOrNull()?.url.orEmpty(), "", item.url)
            }.distinctBy { it.id }.also { require(it.isNotEmpty()) { "This YouTube playlist has no available songs." } }
        }
        return listOf(youtubeTrack(requireNotNull(target.videoUrl)))
    }

    private fun youtubeTrack(url: String): MusicTrack {
        val extractor = service.getStreamExtractor(url)
        extractor.fetchPage()
        return MusicTrack(url, extractor.name, extractor.uploaderName, extractor.length,
            extractor.thumbnails.lastOrNull()?.url.orEmpty(), "", url)
    }

    fun searchArtists(query: String): List<MusicArtist> {
        val handler = service.searchQHFactory.fromQuery(
            query.trim(),
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS),
            "",
        )
        val extractor = service.getSearchExtractor(handler)
        extractor.fetchPage()
        return extractor.initialPage.items.filterIsInstance<ChannelInfoItem>()
            .map { item -> MusicArtist(item.name, item.url, item.thumbnails.lastOrNull()?.url.orEmpty()) }
            .distinctBy { it.url }
            .take(12)
    }

    suspend fun loadArtistDiscography(artist: MusicArtist): ArtistDiscography = coroutineScope {
        val cacheKey = artist.url.canonicalChannelUrl()
        val now = System.currentTimeMillis()
        discographyCache[cacheKey]
            ?.takeIf { now - it.loadedAtMillis < DISCOGRAPHY_CACHE_TTL_MS }
            ?.let { return@coroutineScope it.value }

        val musicCatalog = async(Dispatchers.IO) {
            loadYouTubeMusicArtistReleases(artist)
        }.await()
        val releases = (if (musicCatalog.isNotEmpty()) musicCatalog else {
            val albums = async(Dispatchers.IO) { searchAlbumReleases(artist) }
            val singles = async(Dispatchers.IO) { searchSingles(artist) }
            albums.await() + singles.await()
        }).distinctBy { it.url }
            .sortedWith(compareByDescending<ArtistRelease> { it.year ?: Int.MIN_VALUE })
        ArtistDiscography(artist, releases).also { result ->
            if (releases.isNotEmpty()) {
                discographyCache[cacheKey] = CachedDiscography(now, result)
            }
        }
    }

    private data class ArtistReleaseShelf(val title: String, val browseId: String, val params: String)

    private suspend fun loadYouTubeMusicArtistReleases(artist: MusicArtist): List<ArtistRelease> {
        val channelId = Regex("(?:channel/)?(UC[\\w-]{22})")
            .find(artist.url)?.groupValues?.get(1)
            ?: return emptyList()
        val context = JSONObject().put("client", JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", youtubeMusicClientVersion())
            .put("hl", "en").put("gl", "US"))
        val artistPage = youtubeMusicBrowse(JSONObject().put("browseId", channelId), context)
        val carouselRenderers = findJsonObjects(artistPage, "musicCarouselShelfRenderer")
        val releaseCarousels = carouselRenderers.filter { shelf ->
            val title = shelf.optJSONObject("header")
                ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                ?.optJSONObject("title")?.let(::textFromRuns).orEmpty()
            title == "Albums" || title == "Singles & EPs" || title == "Singles" || title == "EPs"
        }
        val initialReleases = parseArtistReleaseCards(artistPage)
        val shelves = releaseCarousels.mapNotNull { it.toArtistReleaseShelf() }
            .distinctBy { "${it.browseId}|${it.params}" }
        return coroutineScope {
            val expanded = shelves.map { shelf -> async(Dispatchers.IO) {
                loadArtistReleaseShelf(shelf, context)
            } }.awaitAll().flatten()
            (initialReleases + expanded).distinctBy { it.url }
        }
    }

    internal fun parseArtistReleaseCards(artistPage: JSONObject): List<ArtistRelease> =
        findJsonObjects(artistPage, "musicCarouselShelfRenderer").flatMap { shelf ->
            val title = shelf.optJSONObject("header")
                ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                ?.optJSONObject("title")?.let(::textFromRuns).orEmpty()
            if (title !in listOf("Albums", "Singles & EPs", "Singles", "EPs")) emptyList()
            else findJsonObjects(shelf, "musicTwoRowItemRenderer").mapNotNull { releaseFromCard(it, title) }
        }

    private fun releaseFromCard(card: JSONObject, shelfTitle: String): ArtistRelease? {
        val title = card.optJSONObject("title")?.let(::textFromRuns).orEmpty()
        val playlistId = findPlaylistId(card)
        if (title.isBlank() || playlistId.isBlank()) return null
        val subtitle = card.optJSONObject("subtitle")?.let(::textFromRuns).orEmpty()
        val kind = when {
            shelfTitle == "Albums" -> "Album"
            Regex("\\bEP\\b", RegexOption.IGNORE_CASE).containsMatchIn(subtitle) || shelfTitle == "EPs" -> "EP"
            else -> "Single"
        }
        return ArtistRelease(
            title = title,
            url = "https://music.youtube.com/playlist?list=$playlistId",
            thumbnailUrl = findThumbnailUrl(card),
            kind = kind,
            year = releaseYear(subtitle),
        )
    }

    private fun JSONObject.toArtistReleaseShelf(): ArtistReleaseShelf? {
        val header = optJSONObject("header") ?: return null
        val basicHeader = header.optJSONObject("musicCarouselShelfBasicHeaderRenderer") ?: return null
        val titleRenderer = basicHeader.optJSONObject("title") ?: return null
        val title = textFromRuns(titleRenderer)
        val browse = titleRenderer.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
        val browseId = browse?.optString("browseId").orEmpty()
        val params = browse?.optString("params").orEmpty()
        if (browseId.isNotBlank()) return ArtistReleaseShelf(title, browseId, params)
        val fallback = findBrowseEndpoint(basicHeader) ?: return null
        return ArtistReleaseShelf(title, fallback.first, fallback.second)
    }

    private fun loadArtistReleaseShelf(
        shelf: ArtistReleaseShelf,
        context: JSONObject,
    ): List<ArtistRelease> {
        val baseRequest = JSONObject().put("browseId", shelf.browseId).put("params", shelf.params)
        val releases = mutableListOf<ArtistRelease>()
        var response = youtubeMusicBrowse(baseRequest, context)
        repeat(MAX_RELEASE_PAGES) {
            findJsonObjects(response, "musicTwoRowItemRenderer")
                .mapNotNull { releaseFromCard(it, shelf.title) }.forEach(releases::add)
            val continuation = findContinuation(response) ?: return releases
            response = youtubeMusicBrowse(JSONObject().put("continuation", continuation), context)
        }
        return releases
    }

    private fun youtubeMusicBrowse(request: JSONObject, context: JSONObject): JSONObject = youtubeMusicRequest("browse", request, context)

    private fun youtubeMusicRequest(endpoint: String, request: JSONObject, context: JSONObject): JSONObject {
        val payload = JSONObject(request.toString()).put("context", JSONObject(context.toString()))
        val url = URL("https://music.youtube.com/youtubei/v1/$endpoint?alt=json&key=$YOUTUBE_MUSIC_API_KEY")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 18_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Origin", "https://music.youtube.com")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val body = (if (status >= 400) connection.errorStream else connection.inputStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        check(status in 200..299) { "YouTube Music browse request failed with HTTP $status" }
        return JSONObject(body)
    }

    private fun youtubeMusicClientVersion(): String = "1." +
        LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE) + ".01.00"

    private fun findJsonObjects(root: JSONObject, key: String): List<JSONObject> {
        val found = mutableListOf<JSONObject>()
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val childKey = keys.next()
                        val child = value.opt(childKey)
                        if (childKey == key && child is JSONObject) found += child
                        visit(child)
                    }
                }
                is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i))
            }
        }
        visit(root)
        return found
    }

    private fun findBrowseEndpoint(root: JSONObject): Pair<String, String>? {
        fun visit(value: Any?): Pair<String, String>? {
            return when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = value.opt(key)
                        if (key == "browseEndpoint" && child is JSONObject) {
                            val id = child.optString("browseId")
                            val params = child.optString("params")
                            if (id.isNotBlank()) return id to params
                        }
                        visit(child)?.let { return it }
                    }
                    null
                }
                is JSONArray -> {
                    for (i in 0 until value.length()) visit(value.opt(i))?.let { return it }
                    null
                }
                else -> null
            }
        }
        return visit(root)
    }

    private fun textFromRuns(value: JSONObject): String =
        value.optJSONArray("runs")?.let { runs ->
            (0 until runs.length()).mapNotNull { runs.optJSONObject(it)?.optString("text") }.joinToString("")
        }?.ifBlank { value.optString("simpleText", value.optString("text")) }
            ?: value.optString("simpleText", value.optString("text"))

    private fun findPlaylistId(root: JSONObject): String {
        fun visit(value: Any?): String? {
            return when (value) {
                is JSONObject -> {
                    val watch = value.optJSONObject("watchPlaylistEndpoint")
                    watch?.optString("playlistId")?.takeIf { it.isNotBlank() }
                        ?: run {
                            val keys = value.keys()
                            while (keys.hasNext()) visit(value.opt(keys.next()))?.let { return it }
                            null
                        }
                }
                is JSONArray -> {
                    for (i in 0 until value.length()) visit(value.opt(i))?.let { return it }
                    null
                }
                else -> null
            }
        }
        return visit(root).orEmpty()
    }

    private fun findThumbnailUrl(root: JSONObject): String {
        fun visit(value: Any?): String? {
            return when (value) {
                is JSONObject -> {
                    val thumbs = value.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val url = if (thumbs != null && thumbs.length() > 0) {
                        thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                    } else null
                    url?.takeIf { it.isNotBlank() } ?: run {
                        val keys = value.keys()
                        while (keys.hasNext()) visit(value.opt(keys.next()))?.let { return it }
                        null
                    }
                }
                is JSONArray -> {
                    for (i in 0 until value.length()) visit(value.opt(i))?.let { return it }
                    null
                }
                else -> null
            }
        }
        return visit(root).orEmpty()
    }

    private fun findContinuation(root: JSONObject): String? =
        (findJsonObjects(root, "nextContinuationData").firstOrNull()?.optString("continuation")
            ?: findJsonObjects(root, "continuationCommand").firstOrNull()?.optString("token"))
            ?.takeIf { it.isNotBlank() }

    private fun collectItems(
        extractor: ListExtractor<out InfoItem>,
        maxPages: Int = MAX_RELEASE_PAGES,
    ): List<InfoItem> {
        extractor.fetchPage()
        var page = extractor.initialPage
        val items = mutableListOf<InfoItem>()
        repeat(maxPages) {
            items += page.items
            if (!page.hasNextPage()) return items
            page = runCatching { extractor.getPage(page.nextPage) }.getOrNull() ?: return items
        }
        return items
    }

    private fun collectPlaylistItems(
        extractor: ListExtractor<out InfoItem>,
        maxPages: Int = MAX_RELEASE_PAGES,
    ): List<PlaylistInfoItem> = collectItems(extractor, maxPages).filterIsInstance<PlaylistInfoItem>()

    private fun collectStreamItems(extractor: ListExtractor<out InfoItem>): List<StreamInfoItem> =
        collectItems(extractor, MAX_RELEASE_PAGES).filterIsInstance<StreamInfoItem>()

    private fun PlaylistInfoItem.belongsTo(artist: MusicArtist): Boolean {
        val artistUrl = artist.url.canonicalChannelUrl()
        val uploaderUrl = uploaderUrl.orEmpty().canonicalChannelUrl()
        val sameChannel = uploaderUrl.isNotBlank() && artistUrl.isNotBlank() && uploaderUrl == artistUrl
        val uploader = uploaderName.orEmpty().normalizedArtistName()
        val artistName = artist.name.normalizedArtistName()
        val sameArtistName = uploader == artistName ||
            uploader.startsWith("$artistName ") ||
            uploader.contains(" $artistName ") ||
            uploader.endsWith(" $artistName")
        return sameChannel || sameArtistName
    }

    private fun String.normalizedArtistName(): String = lowercase()
        .trim()
        .replace(Regex("\\btopic\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun String.canonicalChannelUrl(): String = lowercase()
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
        .removePrefix("https://www.")
        .removePrefix("http://www.")
        .removePrefix("https://")
        .removePrefix("http://")

    private fun releaseYear(title: String): Int? = Regex("\\b(19\\d{2}|20\\d{2})\\b")
        .find(title)?.value?.toIntOrNull()

    private fun releaseKind(title: String, trackCount: Long, fromSinglesTab: Boolean = false): String = when {
        title.contains("single", true) -> "Single"
        Regex("\\bEP\\b", RegexOption.IGNORE_CASE).containsMatchIn(title) || trackCount in 2..6 -> "EP"
        trackCount == 1L || fromSinglesTab -> "Single"
        else -> "Album"
    }

    fun loadReleaseTracks(release: ArtistRelease, artistName: String): List<MusicTrack> {
        val extractor = service.getPlaylistExtractor(release.url)
        return collectStreamItems(extractor).map { item ->
            MusicTrack(
                id = item.url,
                title = item.name,
                artist = item.uploaderName.orEmpty().ifBlank { artistName },
                durationSeconds = item.duration,
                thumbnailUrl = item.thumbnails.lastOrNull()?.url ?: release.thumbnailUrl,
                streamUrl = "",
                pageUrl = item.url,
            )
        }.distinctBy { it.id }
    }

    private suspend fun searchSingles(artist: MusicArtist): List<ArtistRelease> = coroutineScope {
        val queries = listOf("${artist.name} singles", "${artist.name} EP")
        queries.map { query -> async(Dispatchers.IO) { searchMusicAlbums(query, MAX_SEARCH_PAGES) } }
            .awaitAll().flatten()
            .filter { it.belongsTo(artist) }
            .filter { item ->
                item.streamCount in 1..6 || item.name.contains("single", true) ||
                    Regex("\\bEP\\b", RegexOption.IGNORE_CASE).containsMatchIn(item.name)
            }
            .map { item ->
                ArtistRelease(
                    title = item.name,
                    url = item.url,
                    thumbnailUrl = item.thumbnails.lastOrNull()?.url.orEmpty(),
                    trackCount = item.streamCount.coerceAtLeast(0),
                    kind = releaseKind(item.name, item.streamCount, fromSinglesTab = true),
                    year = releaseYear(item.name),
                )
            }
            .distinctBy { it.url }
    }

    private suspend fun searchAlbumReleases(artist: MusicArtist): List<ArtistRelease> = coroutineScope {
        val queries = listOf(
            "${artist.name} albums",
            "${artist.name} discography",
            "${artist.name} full albums",
        )
        val results = queries.map { query -> async(Dispatchers.IO) {
            searchMusicAlbums(query, MAX_SEARCH_PAGES)
        } }.awaitAll().flatten()
        results
            .filter { it.belongsTo(artist) }
            .map { item ->
            ArtistRelease(
                title = item.name,
                url = item.url,
                thumbnailUrl = item.thumbnails.lastOrNull()?.url.orEmpty(),
                trackCount = item.streamCount.coerceAtLeast(0),
                kind = releaseKind(item.name, item.streamCount),
                year = releaseYear(item.name),
            )
        }.distinctBy { it.url }
    }

    private fun searchMusicAlbums(query: String, maxPages: Int): List<PlaylistInfoItem> {
        val handler = service.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS),
            "",
        )
        return collectPlaylistItems(service.getSearchExtractor(handler), maxPages)
    }

    fun playableTrack(track: MusicTrack): MusicTrack {
        if (!track.pageUrl.startsWith("spotify-search:")) return track.copy(pageUrl = track.pageUrl.replace("https://music.youtube.com/", "https://www.youtube.com/"))
        val key = "${track.title.lowercase()}|${track.artist.lowercase()}"
        matches[key]?.let { return it.copy(id = track.id, title = track.title, artist = track.artist) }
        val ranked = searchSongs("${track.title} ${track.artist}")
            .sortedByDescending { MusicRecommendations.matchScore(track, it) }
        val match = ranked.firstOrNull { MusicRecommendations.matchScore(track, it) >= 0.40 }
            ?: error("Couldn't find a matching recording for ${track.title} by ${track.artist}.")
        val canonical = match.copy(pageUrl = match.pageUrl.replace("https://music.youtube.com/", "https://www.youtube.com/"))
        matches[key] = canonical
        return canonical.copy(id = track.id, title = track.title, artist = track.artist)
    }

    fun resolveForPlayback(track: MusicTrack, forceRefresh: Boolean = false): MusicTrack =
        if (track.pageUrl.startsWith("content://") || track.pageUrl.startsWith("file://")) track.copy(streamUrl = track.pageUrl)
        else resolveAudio(playableTrack(track), forceRefresh).let { resolved ->
            if (track.pageUrl.startsWith("spotify-search:")) resolved.copy(title = track.title, artist = track.artist) else resolved
        }

    fun resolveAudio(track: MusicTrack, forceRefresh: Boolean = false): MusicTrack {
        val cached = audioCache[track.pageUrl]
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.first < 10 * 60 * 1000) {
            return cached.second.copy(id = track.id, title = track.title, artist = track.artist)
        }
        val streamExtractor = service.getStreamExtractor(track.pageUrl)
        streamExtractor.fetchPage()
        val audio = streamExtractor.audioStreams
            .filter { it.isUrl }
            .maxByOrNull { it.averageBitrate }
            ?: error("YouTube returned no playable audio URL for this track")
        return track.copy(
            title = streamExtractor.name.ifBlank { track.title },
            artist = streamExtractor.uploaderName.ifBlank { track.artist },
            durationSeconds = streamExtractor.length.coerceAtLeast(0),
            thumbnailUrl = streamExtractor.thumbnails.lastOrNull()?.url ?: track.thumbnailUrl,
            streamUrl = audio.content,
            audioQuality = listOfNotNull(
                audio.format?.name,
                audio.averageBitrate.takeIf { it > 0 }?.let { "$it kbps" },
            ).joinToString(" · "),
        ).also { audioCache[track.pageUrl] = System.currentTimeMillis() to it }
    }

    fun relatedSongs(track: MusicTrack, region: String = "AUTO", playlistId: String? = null): List<MusicTrack> {
        val seed = playableTrack(track)
        val videoId = Regex("(?:[?&]v=|youtu\\.be/)([A-Za-z0-9_-]{11})")
            .find(seed.pageUrl)?.groupValues?.get(1) ?: error("This song has no radio source.")
        val client = JSONObject().put("clientName", "WEB_REMIX")
            .put("clientVersion", youtubeMusicClientVersion()).put("hl", RecommendationRegion.language(seed))
        RecommendationRegion.country(region)?.let { client.put("gl", it) }
        val context = JSONObject().put("client", client)
        var response = youtubeMusicRequest("next", JSONObject().put("videoId", videoId)
            .put("playlistId", playlistId ?: "RDAMVM$videoId").put("isAudioOnly", true), context)
        val tracks = mutableListOf<MusicTrack>()
        val continuations = mutableSetOf<String>()
        for (page in 0 until 4) {
            tracks += parseMusicRadio(response)
            val token = findContinuation(response)
            if (tracks.distinctBy { it.pageUrl }.size >= 60 || token == null || !continuations.add(token) || page == 3) break
            response = try {
                youtubeMusicRequest("next", JSONObject().put("continuation", token), context)
            } catch (_: Exception) { break }
        }
        return tracks.filter { it.id != seed.id && it.pageUrl != seed.pageUrl && MusicRecommendations.isMusic(it) }.distinctBy { it.pageUrl }
            .also { require(it.isNotEmpty()) { "No related music was returned. Try radio again in a moment." } }
    }

    internal fun parseMusicRadio(response: JSONObject): List<MusicTrack> =
        findJsonObjects(response, "playlistPanelVideoRenderer").mapNotNull { row ->
            val id = row.optString("videoId")
            val title = row.optJSONObject("title")?.let(::textFromRuns).orEmpty()
            val artist = row.optJSONObject("longBylineText")?.let(::textFromRuns).orEmpty()
                .substringBefore(" • ").substringBefore(" · ")
            val musicType = row.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
                ?.optJSONObject("watchEndpointMusicSupportedConfigs")?.optJSONObject("watchEndpointMusicConfig")
                ?.optString("musicVideoType").orEmpty()
            if (id.isBlank() || title.isBlank() || artist.isBlank() ||
                (musicType.isNotBlank() && musicType !in setOf("MUSIC_VIDEO_TYPE_ATV", "MUSIC_VIDEO_TYPE_OMV"))) return@mapNotNull null
            val durationText = row.optJSONObject("lengthText")?.let(::textFromRuns).orEmpty()
            val seconds = durationText.split(':').fold(0L) { sum, part -> sum * 60 + (part.toLongOrNull() ?: 0) }
            val url = "https://www.youtube.com/watch?v=$id"
            MusicTrack(url, title, artist, seconds, findThumbnailUrl(row), "", url)
        }

    private class AndroidDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
                requestMethod = request.httpMethod()
                connectTimeout = 20_000
                readTimeout = 25_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
                request.headers()?.forEach { (name, values) ->
                    values.forEach { value -> setRequestProperty(name, value) }
                }
            }
            val data = request.dataToSend()
            if (data != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(data) }
            }
            val code = connection.responseCode
            val body = (if (code >= 400) connection.errorStream else connection.inputStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key!! }
                .mapValues { it.value.orEmpty() }
            val response = Response(code, connection.responseMessage.orEmpty(), headers, body, connection.url.toString())
            connection.disconnect()
            return response
        }
    }
}
