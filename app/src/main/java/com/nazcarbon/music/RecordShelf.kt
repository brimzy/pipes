package com.nazcarbon.music

import androidx.compose.animation.core.*
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.ui.theme.PipesPalette

@Composable internal fun PipesWordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Pipes", color = PipesPalette.Brand,
            fontSize = 24.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable internal fun PlayerArtwork(track: MusicTrack, modifier: Modifier) {
    Box(modifier.artworkSurface(RoundedCornerShape(16.dp), 28.dp)) {
        Crossfade(track.thumbnailUrl, animationSpec = tween(240), label = "artwork change") { artwork ->
            ArtworkImage(artwork, "Artwork for ${track.title}. Swipe left for lyrics.", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
