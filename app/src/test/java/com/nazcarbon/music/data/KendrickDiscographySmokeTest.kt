package com.nazcarbon.music.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Live catalog regression. Run with PIPES_LIVE_DISCOGRAPHY_TEST=1. */
class KendrickDiscographySmokeTest {
    @Test fun `Kendrick albums and major standalone singles appear in the catalog`() = runBlocking {
        assumeTrue(System.getenv("PIPES_LIVE_DISCOGRAPHY_TEST") == "1")

        val source = NewPipeMusicSource().also { it.initialize() }
        val artists = source.searchArtists("Kendrick Lamar")
        println("Kendrick artist results: ${artists.joinToString { "${it.name} <${it.url}>" }}")
        val artist = artists.firstOrNull { it.name.catalogKey() == "kendricklamar" }
            ?: artists.firstOrNull { it.name.catalogKey().contains("kendricklamar") }
            ?: error("Kendrick Lamar was not returned by artist search")

        val releases = source.loadArtistDiscography(artist).releases
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
        val expectedSingles = listOf("notlikeus", "euphoria", "meetthegrahams", "heartpart5", "humble", "hiiipower")
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
    }

    private fun String.catalogKey(): String = lowercase().filter(Char::isLetterOrDigit)

    private fun isSingleRelease(release: ArtistRelease): Boolean =
        release.kind.contains("single", true) || release.kind.contains("ep", true) || release.trackCount == 1L
}
