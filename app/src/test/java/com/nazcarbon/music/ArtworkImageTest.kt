package com.nazcarbon.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkImageTest {
    @Test fun normalizesCdnUrls() {
        assertEquals("https://i.ytimg.com/image?a=1&b=2", normalizeArtworkUrl(" //i.ytimg.com/image?a=1&amp;b=2 "))
        assertEquals("https://example.com/art", normalizeArtworkUrl("http://example.com/art"))
        assertEquals("content://art/1", normalizeArtworkUrl("content://art/1"))
        assertEquals("", normalizeArtworkUrl("  "))
    }
}
