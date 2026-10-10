package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class RecommendationRegionTest {
    private fun song(title: String) = MusicTrack("id", title, "Artist", 200, "", "", "url")
    @Test fun `automatic preference does not invent a country`() {
        assertNull(RecommendationRegion.country("AUTO"))
        assertNull(RecommendationRegion.country("invalid"))
    }
    @Test fun `manual preference accepts ISO countries`() {
        assertEquals("PH", RecommendationRegion.country("ph"))
        assertEquals("JP", RecommendationRegion.country("JP"))
    }
    @Test fun `automatic language follows song metadata`() {
        assertEquals("ko", RecommendationRegion.language(song("봄날")))
        assertEquals("ja", RecommendationRegion.language(song("さくら")))
        assertEquals("en", RecommendationRegion.language(song("Unknown language")))
    }
}
