package com.nazcarbon.music

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.nazcarbon.music.data.MusicPlaylist
import com.nazcarbon.music.data.PlaylistArtworkFiles
import com.nazcarbon.music.ui.theme.PipesPalette
import java.io.File

internal fun playlistArtworkDirectory(context: Context) = File(context.filesDir, "playlist-artwork")

/** Call on IO. Keep the original bytes so animated GIFs retain all their frames. */
internal suspend fun importPlaylistArtwork(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open this image.")
    val file = PlaylistArtworkFiles.import(playlistArtworkDirectory(context), input) { candidate ->
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(candidate.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a supported image or animated GIF." }
        require(bounds.outWidth.toLong() * bounds.outHeight <= 16_777_216L) {
            "This image is too large. Choose one with fewer than 16 megapixels."
        }
    }
    try {
        val result = context.imageLoader.execute(ImageRequest.Builder(context).data(file).size(512)
            .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build())
        require(result is SuccessResult) { "Couldn't read this image. Choose another image or GIF." }
        return Uri.fromFile(file).toString()
    } catch (failure: Exception) {
        file.delete()
        throw failure
    }
}

@Composable
internal fun PlaylistArtwork(playlist: MusicPlaylist, modifier: Modifier = Modifier) {
    val artwork = playlist.artworkUri.ifBlank { playlist.tracks.firstOrNull()?.thumbnailUrl.orEmpty() }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(PipesPalette.Raised), contentAlignment = Alignment.Center) {
        if (artwork.isNotBlank()) ArtworkImage(artwork, null, Modifier.fillMaxSize())
        else Icon(Icons.Default.QueueMusic, null, tint = PipesPalette.Text, modifier = Modifier.fillMaxSize(.4f))
    }
}

@Composable
internal fun PlaylistArtworkEditor(playlist: MusicPlaylist, busy: Boolean, onChoose: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PlaylistArtwork(playlist, Modifier.size(112.dp))
        Column(Modifier.weight(1f)) {
            Text("Images and animated GIFs", color = PipesPalette.TextSoft, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onChoose, enabled = !busy) {
                Text(if (busy) "Saving artwork…" else if (playlist.artworkUri.isBlank()) "Choose artwork" else "Change artwork")
            }
            if (playlist.artworkUri.isNotBlank()) TextButton(onClick = onRemove, enabled = !busy) { Text("Use default artwork") }
        }
    }
}
