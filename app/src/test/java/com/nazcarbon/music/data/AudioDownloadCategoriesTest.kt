package com.nazcarbon.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AudioDownloadCategoriesTest {
    @Test fun `adding a category trims its name and protects reserved names`() {
        assertEquals(listOf("Unsorted", "Jazz"), AudioDownloads.addCategory(listOf("Unsorted"), " Jazz "))
        assertThrows(IllegalArgumentException::class.java) {
            AudioDownloads.addCategory(listOf("Unsorted", "Jazz"), "jAzZ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioDownloads.addCategory(listOf("Unsorted"), " ")
        }
    }

    @Test fun `removing a category moves its downloads to Unsorted and preserves files`() {
        val downloads = listOf(
            AudioDownload(1L, "One", "Artist", "Jazz", "audio/flac"),
            AudioDownload(2L, "Two", "Artist", "Unsorted", "audio/mpeg"),
        )

        val (categories, movedDownloads) = AudioDownloads.removeCategory(
            listOf("Unsorted", "Jazz"),
            downloads,
            "jazz",
        )

        assertEquals(listOf("Unsorted"), categories)
        assertEquals("Unsorted", movedDownloads.first().category)
        assertEquals(downloads[0].id, movedDownloads.first().id)
        assertEquals(downloads[1], movedDownloads.last())
    }

    @Test fun `Unsorted cannot be removed`() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioDownloads.removeCategory(listOf("Unsorted"), emptyList(), "Unsorted")
        }
    }
}
