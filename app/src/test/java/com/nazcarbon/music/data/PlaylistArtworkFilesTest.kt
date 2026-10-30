package com.nazcarbon.music.data

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaylistArtworkFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun importsPreserveOriginalBytesAndHaveIndependentNames() {
        val directory = temporary.newFolder("artwork")
        val bytes = ByteArray(4096) { (it % 256).toByte() }
        val first = PlaylistArtworkFiles.import(directory, ByteArrayInputStream(bytes)) { assertArrayEquals(bytes, it.readBytes()) }
        val second = PlaylistArtworkFiles.import(directory, ByteArrayInputStream(bytes)) {}
        assertNotEquals(first, second)
        assertArrayEquals(bytes, first.readBytes())
        assertArrayEquals(bytes, second.readBytes())
    }

    @Test fun failedValidationAndReadErrorsLeaveExistingArtworkIntact() {
        val directory = temporary.newFolder("artwork")
        val original = PlaylistArtworkFiles.import(directory, ByteArrayInputStream(byteArrayOf(1, 2, 3))) {}
        assertTrue(runCatching {
            PlaylistArtworkFiles.import(directory, ByteArrayInputStream(byteArrayOf(4))) { error("Unsupported image") }
        }.isFailure)
        val broken = object : java.io.InputStream() {
            override fun read(): Int = throw IOException("Read failed")
        }
        assertTrue(runCatching { PlaylistArtworkFiles.import(directory, broken) {} }.isFailure)
        assertEquals(listOf(original), directory.listFiles()!!.toList())
        assertArrayEquals(byteArrayOf(1, 2, 3), original.readBytes())
    }

    @Test fun emptyAndOversizedFilesAreRejectedWithoutLeavingPartialCopies() {
        val directory = temporary.newFolder("artwork")
        for (bytes in listOf(byteArrayOf(), ByteArray(PlaylistArtworkFiles.MAX_BYTES + 1))) {
            assertTrue(runCatching { PlaylistArtworkFiles.import(directory, ByteArrayInputStream(bytes)) {} }.isFailure)
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }

    @Test fun removingArtworkOnlyDeletesFilesOwnedByTheArtworkDirectory() {
        val directory = temporary.newFolder("artwork")
        val owned = PlaylistArtworkFiles.import(directory, ByteArrayInputStream(byteArrayOf(1))) {}
        val outside = temporary.newFile("original.gif").apply { writeText("original") }
        PlaylistArtworkFiles.remove(directory, outside.toURI().toString())
        PlaylistArtworkFiles.remove(directory, "content://downloads/original.gif")
        PlaylistArtworkFiles.remove(directory, "")
        assertTrue(outside.exists())
        assertTrue(owned.exists())
        PlaylistArtworkFiles.remove(directory, owned.toURI().toString())
        assertFalse(owned.exists())
    }
}
