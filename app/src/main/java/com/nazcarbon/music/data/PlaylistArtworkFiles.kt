package com.nazcarbon.music.data

import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.UUID

/** Owns byte-for-byte artwork copies; failed imports never replace the current cover. */
object PlaylistArtworkFiles {
    const val MAX_BYTES = 20 * 1024 * 1024

    fun import(directory: File, input: InputStream, validate: (File) -> Unit): File {
        val file = File(directory, "${UUID.randomUUID()}.image")
        try {
            input.use { source ->
                check(directory.isDirectory || directory.mkdirs()) { "Couldn't save playlist artwork." }
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { "Choose an image or GIF smaller than 20 MB." }
                        output.write(buffer, 0, count)
                    }
                    require(total > 0) { "This image is empty. Choose another image or GIF." }
                }
            }
            validate(file)
            return file
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }

    fun remove(directory: File, artworkUri: String) {
        runCatching {
            val file = File(URI(artworkUri))
            if (file.canonicalFile.parentFile == directory.canonicalFile) file.delete()
        }
    }
}
