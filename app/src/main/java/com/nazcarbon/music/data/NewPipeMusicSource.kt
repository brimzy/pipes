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
import java.net.HttpURLConnection
import java.net.URL

/** NewPipe's extractor uses a pluggable Downloader. Keep blocking network work off the UI thread. */
class NewPipeMusicSource {
    private val service: StreamingService by lazy { NewPipe.getService("YouTube") }

    fun initialize() {
        NewPipe.init(AndroidDownloader())
    }

    fun searchSongs(query: String): List<MusicTrack> {
        val handler: SearchQueryHandler = service.searchQHFactory.fromQuery(
            query.trim(),
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
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

    /** Artist tabs provide album and single playlists; load only their summaries here. */
    fun loadArtistDiscography(artist: MusicArtist): ArtistDiscography {
        val channel = service.getChannelExtractor(artist.url)
        channel.fetchPage()
        val profile = artist.copy(
            name = channel.name.ifBlank { artist.name },
            thumbnailUrl = channel.avatars.lastOrNull()?.url ?: artist.thumbnailUrl,
            description = channel.description.orEmpty(),
        )
        val channelReleases = channel.tabs.flatMap { tab ->
            runCatching {
                val tabExtractor = service.getChannelTabExtractor(tab)
                val tabName = tabExtractor.name.ifBlank { tab.contentFilters.joinToString(" ") }
                val isSingleTab = tabName.contains("single", true) || tabName.contains("ep", true)
                val isAlbumTab = tabName.contains("album", true)
                if (!isSingleTab && !isAlbumTab) {
                    emptyList()
                } else {
                    collectPlaylistItems(tabExtractor, maxPages = 20)
                        // A channel tab is already scoped to this artist. Still reject
                        // entries explicitly attributed to another uploader.
                        .filter { item -> item.belongsTo(profile) || item.uploaderName.isNullOrBlank() }
                        .map { item ->
                        ArtistRelease(
                            title = item.name,
                            url = item.url,
                            thumbnailUrl = item.thumbnails.lastOrNull()?.url.orEmpty(),
                            trackCount = item.streamCount.coerceAtLeast(0),
                            kind = if (isSingleTab) "Singles & EPs" else "Albums",
                            year = releaseYear(item.name),
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }.distinctBy { it.url }

        // Channel tabs can be incomplete or unavailable. Merge them with the
        // artist's album search results so partial tabs do not hide releases.
        val searchedAlbums = runCatching { searchAlbumReleases(profile) }.getOrDefault(emptyList())
        val searchedSingles = runCatching { searchSingles(profile) }.getOrDefault(emptyList())
        val releases = (channelReleases + searchedAlbums + searchedSingles)
            .distinctBy { it.url }
            .sortedWith(compareByDescending<ArtistRelease> { it.year ?: Int.MIN_VALUE })
        return ArtistDiscography(profile, releases)
    }

    private fun collectPlaylistItems(extractor: ListExtractor<InfoItem>, maxPages: Int = 20): List<PlaylistInfoItem> {
        extractor.fetchPage()
        var page = extractor.initialPage
        val items = mutableListOf<PlaylistInfoItem>()
        repeat(maxPages) {
            items += page.items.filterIsInstance<PlaylistInfoItem>()
            if (!page.hasNextPage()) return items
            page = runCatching { extractor.getPage(page.nextPage) }.getOrNull() ?: return items
        }
        return items
    }

    private fun PlaylistInfoItem.belongsTo(artist: MusicArtist): Boolean {
        val artistUrl = artist.url.trimEnd('/').lowercase()
        val uploaderUrl = uploaderUrl.orEmpty().trimEnd('/').lowercase()
        val sameChannel = uploaderUrl.isNotBlank() && artistUrl.isNotBlank() && uploaderUrl == artistUrl
        val sameArtistName = uploaderName.orEmpty().normalizedArtistName() == artist.name.normalizedArtistName()
        return sameChannel || sameArtistName
    }

    private fun String.normalizedArtistName(): String = lowercase()
        .trim()
        .removeSuffix(" - topic")
        .replace(Regex("\\s+"), " ")

    private fun releaseYear(title: String): Int? = Regex("\\b(19\\d{2}|20\\d{2})\\b")
        .find(title)?.value?.toIntOrNull()

    fun loadReleaseTracks(release: ArtistRelease, artistName: String): List<MusicTrack> {
        val extractor = service.getPlaylistExtractor(release.url)
        extractor.fetchPage()
        return extractor.initialPage.items.filterIsInstance<StreamInfoItem>().map { item ->
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

    private fun searchSingles(artist: MusicArtist): List<ArtistRelease> {
        val handler = service.searchQHFactory.fromQuery(
            "${artist.name} singles",
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS),
            "",
        )
        val extractor = service.getSearchExtractor(handler)
        return collectPlaylistItems(extractor, maxPages = 20)
            .filter { it.belongsTo(artist) }
            .filter { item -> item.streamCount in 1..6 || item.name.contains("single", true) || item.name.contains("EP", true) }
            .map { item ->
                ArtistRelease(
                    title = item.name,
                    url = item.url,
                    thumbnailUrl = item.thumbnails.lastOrNull()?.url.orEmpty(),
                    trackCount = item.streamCount.coerceAtLeast(0),
                    kind = if (item.streamCount in 2..6) "EP" else "Single",
                    year = releaseYear(item.name),
                )
            }
            .distinctBy { it.url }
    }

    private fun searchAlbumReleases(artist: MusicArtist): List<ArtistRelease> {
        val handler = service.searchQHFactory.fromQuery(
            artist.name,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS),
            "",
        )
        val extractor = service.getSearchExtractor(handler)
        return collectPlaylistItems(extractor, maxPages = 20)
            // Album search is global, so only retain releases with an exact artist
            // attribution. This avoids unrelated artists leaking into discography.
            .filter { it.belongsTo(artist) }
            .map { item ->
            ArtistRelease(
                title = item.name,
                url = item.url,
                thumbnailUrl = item.thumbnails.lastOrNull()?.url.orEmpty(),
                trackCount = item.streamCount.coerceAtLeast(0),
                kind = when {
                    item.name.contains("single", true) -> "Single"
                    item.name.contains("EP", true) || item.streamCount in 2..6 -> "EP"
                    item.streamCount == 1L -> "Single"
                    else -> "Album"
                },
                year = releaseYear(item.name),
            )
        }.distinctBy { it.url }
    }

    fun resolveAudio(track: MusicTrack): MusicTrack {
        val streamExtractor = service.getStreamExtractor(track.pageUrl)
        streamExtractor.fetchPage()
        val audio = streamExtractor.audioStreams
            .filter { it.isUrl }
            .maxByOrNull { it.averageBitrate }
            ?: error("YouTube returned no playable audio URL for this track")
        return track.copy(
            streamUrl = audio.content,
            audioQuality = listOfNotNull(
                audio.format?.name,
                audio.averageBitrate.takeIf { it > 0 }?.let { "$it kbps" },
            ).joinToString(" · "),
        )
    }

    fun relatedSongs(track: MusicTrack): List<MusicTrack> {
        val streamExtractor = service.getStreamExtractor(track.pageUrl)
        streamExtractor.fetchPage()
        val related = streamExtractor.relatedItems?.items.orEmpty()
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                MusicTrack(
                    id = item.url,
                    title = item.name,
                    artist = item.uploaderName.orEmpty().ifBlank { "YouTube Music" },
                    durationSeconds = item.duration,
                    thumbnailUrl = item.thumbnails.lastOrNull()?.url.orEmpty(),
                    streamUrl = "",
                    pageUrl = item.url,
                )
            }
            .filter { it.id != track.id && it.durationSeconds > 0 }
            .distinctBy { it.id }
            .take(20)

        if (related.isNotEmpty()) return related
        return searchSongs("${track.artist} ${track.title} similar songs")
            .filter { it.id != track.id }
            .distinctBy { it.id }
            .take(20)
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
