package com.nazcarbon.music.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class KendrickDiscographySmokeTest {
    @Test fun `Kendrick albums and major standalone singles appear in the catalog`() = runBlocking {
        assumeTrue(System.getenv("PIPES_LIVE_DISCOGRAPHY_TEST") == "1")

        val source = NewPipeMusicSource().also { it.initialize() }
        val artists = source.searchArtists("Kendrick Lamar")
        println("Kendrick artist results: ${artists.joinToString { "${it.name} <${it.url}>" }}")
        val artist = artists.firstOrNull { it.name.catalogKey() == "kendricklamar" }
            ?: artists.firstOrNull { it.name.catalogKey().contains("kendricklamar") }
            ?: error("Kendrick Lamar was not returned by artist search")

        val started = System.nanoTime()
        val releases = source.loadArtistDiscography(artist).releases
        println("Catalog loaded in ${(System.nanoTime() - started) / 1_000_000} ms")
        org.junit.Assert.assertSame(releases, source.loadArtistDiscography(artist).releases)
        println("Kendrick releases (${releases.size}): ${releases.joinToString { "${it.kind}: ${it.title}" }}")
        val albumTitles = releases.filterNot(::isSingleRelease).map { it.title.catalogKey() }
        val singleTitles = releases.filter(::isSingleRelease).map { it.title.catalogKey() }

        val expectedAlbums = listOf(
            "section80",
            "goodkid",
            "pimpabutterfly",
            "damn",
            "untitledunmastered",
            "blackpanther",
            "mrmorale",
            "gnx",
            "overlydedicated",
        )
        val missingAlbums = expectedAlbums.filterNot { expected -> albumTitles.any { it.contains(expected) } }
        val expectedSingles = listOf("notlikeus", "euphoria", "meetthegrahams", "heartpart5", "heartpart4", "humble", "hiiipower")
        val missingSingles = expectedSingles.filterNot { expected -> singleTitles.any { it.contains(expected) } }
        assertTrue(
            "Missing expected Kendrick albums/mixtapes: $missingAlbums; missing expected singles: $missingSingles; " +
                "found ${releases.joinToString { "${it.kind}: ${it.title}" }}",
            missingAlbums.isEmpty() && missingSingles.isEmpty(),
        )

        val gnx = releases.firstOrNull { it.title.catalogKey().contains("gnx") && !isSingleRelease(it) }
            ?: error("GNX was missing; cannot verify the complete album track list")
        val tracks = source.loadReleaseTracks(gnx, artist.name)
        val trackTitles = tracks.map { it.title.catalogKey() }
        val expectedGnxTracks = listOf(
            "waccedoutmurals", "squabbleup", "luther", "manatthegarden", "heynow", "reincarnated",
            "tvoff", "dodgerblue", "peekaboo", "heartpt6", "gnx", "gloria",
        )
        val missingTracks = expectedGnxTracks.filterNot { it in trackTitles }
        assertTrue("GNX is missing tracks $missingTracks; found ${tracks.map { it.title }}", missingTracks.isEmpty())
        assertTrue("GNX should have 12 tracks; found ${tracks.size}", tracks.size == 12)
        val damn = releases.first { it.kind == "Album" && it.title.catalogKey().contains("damn") }
        val damnTracks = source.loadReleaseTracks(damn, artist.name)
        assertTrue("DAMN should load all 14 tracks", damnTracks.size == 14)
        val single = releases.first { it.title == "Not Like Us" }
        val singleTracks = source.loadReleaseTracks(single, artist.name)
        assertTrue("Not Like Us should load its single track", singleTracks.size == 1 && singleTracks[0].title == "Not Like Us")
        val playable = source.resolveAudio(singleTracks.single())
        assertTrue("The single should resolve playable audio", playable.streamUrl.startsWith("https://"))

    }

    @Test fun `catalog discovery works across different artists`() = runBlocking {
        assumeTrue(System.getenv("PIPES_LIVE_DISCOGRAPHY_TEST") == "1")
        val source = NewPipeMusicSource().also { it.initialize() }
        for ((name, expected) in listOf(
            "Taylor Swift" to listOf("1989", "folklore", "midnights"),
            "Daft Punk" to listOf("discovery", "homework", "randomaccessmemories"),
        )) {
            val artist = source.searchArtists(name).first { it.name.equals(name, true) }
            val started = System.nanoTime()
            val releases = source.loadArtistDiscography(artist).releases
            val albums = releases.filter { it.kind == "Album" }.map { it.title.catalogKey() }
            println("$name: ${albums.size} albums, ${releases.size - albums.size} singles/EPs in ${(System.nanoTime() - started) / 1_000_000} ms")
            assertTrue("$name missing albums: $albums", expected.all { key -> albums.any { it.contains(key) } })
            assertTrue("$name should expose standalone singles/EPs", releases.any { it.kind == "Single" || it.kind == "EP" })
            assertTrue(releases.all { it.url.contains("playlist?list=") })
        }
    }

    private fun String.catalogKey(): String = lowercase().filter(Char::isLetterOrDigit)

    private fun isSingleRelease(release: ArtistRelease): Boolean =
        release.kind.contains("single", true) || release.kind.contains("ep", true) || release.trackCount == 1L
}
