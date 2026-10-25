package com.nazcarbon.music

import androidx.compose.animation.core.*
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
        Text("Pipes", color = PipesPalette.Text,
            fontSize = 24.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable internal fun SwipeArtwork(track: MusicTrack, modifier: Modifier, previous: () -> Unit, next: () -> Unit, hasNext: Boolean) {
    var drag by remember(track.id) { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(drag, spring(dampingRatio = .55f, stiffness = 500f), label = "sleeve swipe")
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val latestPrevious by rememberUpdatedState(previous)
    val latestNext by rememberUpdatedState(next)
    val latestHasNext by rememberUpdatedState(hasNext)
    ArtworkImage(track.thumbnailUrl, "Artwork for ${track.title}. Swipe left for next, right for previous.",
        modifier.pointerInput(track.id, threshold) {
            detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f }, onDragEnd = {
                if (drag < -threshold && latestHasNext) latestNext()
                else if (drag > threshold) latestPrevious()
                drag = 0f
            }) { change, amount -> change.consume(); drag = (drag + amount).coerceIn(-threshold * 2, threshold * 2) }
        }.graphicsLayer { translationX = offset * .45f; rotationZ = offset / threshold * 4f }
            .clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
}
