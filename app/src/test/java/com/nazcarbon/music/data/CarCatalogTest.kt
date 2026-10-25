package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class CarCatalogTest {
    private fun track(id: String, title: String = id) = MusicTrack(id, title, "Artist", 180, "", "", "https://example.com/$id")

    @Test fun `selection keeps the remaining order of its own playlist`() {
        val a = track("a"); val b = track("b"); val c = track("c")
        val catalog = CarCatalog(listOf(b, a), listOf(MusicPlaylist("Drive / 夜", listOf(a, b, c))), emptyList())
        val folder = catalog.folders(CarCatalog.PLAYLISTS).single()
        val selected = catalog.children(folder.id)[1]
        assertEquals(b, catalog.find(selected.id)!!.track)
        assertEquals(listOf(c), catalog.find(selected.id)!!.following)
        val liked = catalog.children(CarCatalog.LIKED).first()
        assertNotEquals(liked.id, selected.id)
        assertEquals(listOf(a), catalog.find(liked.id)!!.following)
    }

    @Test fun `external URIs and stale deleted recordings never resolve as saved IDs`() {
        val catalog = CarCatalog(listOf(track("a")), emptyList(), emptyList())
        assertNull(catalog.find("https://example.com/foreign.mp3"))
        assertNull(catalog.find("content://private/data"))
        val id = catalog.children(CarCatalog.LIKED).single().id
        assertNull(CarCatalog(emptyList(), emptyList(), emptyList()).find(id))
        assertFalse(catalog.containsFolder("unknown"))
    }

    @Test fun `car pagination handles large host requests without overflow`() {
        val items = (0..20).toList()
        assertEquals((10..19).toList(), CarCatalog.page(items, 1, 10))
        assertEquals(listOf(20), CarCatalog.page(items, 2, 10))
        assertTrue(CarCatalog.page(items, Int.MAX_VALUE, Int.MAX_VALUE).isEmpty())
        assertTrue(CarCatalog.page(items, -1, 10).isEmpty())
        assertTrue(CarCatalog.page(items, 0, 0).isEmpty())
    }

    @Test fun `voice library search matches title and artist without duplicate contexts`() {
        val song = track("a", "Night Drive")
        val catalog = CarCatalog(listOf(song), listOf(MusicPlaylist("Drive", listOf(song))), emptyList())
        assertEquals(listOf(song), catalog.search("artist NIGHT").map { it.track })
        assertTrue(catalog.search(" ").isEmpty())
    }
}
