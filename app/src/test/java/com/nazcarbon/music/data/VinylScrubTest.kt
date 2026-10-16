package com.nazcarbon.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VinylScrubTest {
    @Test fun `crossing the angular seam keeps the seek continuous`() {
        assertEquals(2f, vinylAngleDelta(179f, -179f), .001f)
        assertEquals(-2f, vinylAngleDelta(-179f, 179f), .001f)
        assertEquals(90f, vinylAngleDelta(-45f, 45f), .001f)
    }
    @Test fun `scratching seeks both ways and clamps to recording bounds`() {
        assertEquals(45_000L, vinylSeekPosition(30_000, 180f, 120_000))
        assertEquals(15_000L, vinylSeekPosition(30_000, -180f, 120_000))
        assertEquals(0L, vinylSeekPosition(5_000, -360f, 120_000))
        assertEquals(120_000L, vinylSeekPosition(110_000, 360f, 120_000))
        assertEquals(0L, vinylSeekPosition(30_000, 360f, 0))
    }
}
