package com.nazcarbon.music

import androidx.compose.animation.core.*
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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

@Composable internal fun SwipeArtwork(track: MusicTrack, modifier: Modifier, previous: () -> Unit, next: () -> Unit, hasNext: Boolean) {
    var drag by remember(track.id) { mutableFloatStateOf(0f) }
    var dragging by remember(track.id) { mutableStateOf(false) }
    val offset = animateFloatAsState(drag, if (dragging) snap() else spring(dampingRatio = .88f, stiffness = 420f), label = "artwork swipe")
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val latestPrevious by rememberUpdatedState(previous)
    val latestNext by rememberUpdatedState(next)
    val latestHasNext by rememberUpdatedState(hasNext)
    Box(modifier.graphicsLayer { translationX = offset.value * .4f; rotationZ = offset.value / threshold * .8f }
        .artworkSurface(RoundedCornerShape(12.dp), 4.dp)
        .pointerInput(track.id, threshold) {
            detectHorizontalDragGestures(onDragStart = { dragging = true; drag = 0f },
                onDragCancel = { dragging = false; drag = 0f }, onDragEnd = {
                    if (drag < -threshold && latestHasNext) latestNext()
                    else if (drag > threshold) latestPrevious()
                    dragging = false; drag = 0f
                }) { change, amount -> change.consume(); drag = (drag + amount).coerceIn(-threshold * 2, threshold * 2) }
        }) {
        Crossfade(track.thumbnailUrl, animationSpec = tween(240), label = "artwork change") { artwork ->
            ArtworkImage(artwork, "Artwork for ${track.title}. Swipe left for next, right for previous.", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
