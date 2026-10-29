package com.nazcarbon.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.ui.theme.PipesPalette
import kotlinx.coroutines.delay
import kotlin.math.sin

internal data class CollectionAdded(val tracks: List<MusicTrack>, val name: String, val token: Long = System.nanoTime())

/** A left-facing Kirby GIF inhales the cover stack. Saving is complete before this feedback begins. */
@Composable
internal fun CollectionAddAnimation(event: CollectionAdded?, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    if (event == null) return
    key(event.token) {
        val progress = remember { Animatable(0f) }
        val latestFinished by rememberUpdatedState(onFinished)
        LaunchedEffect(event.token) {
            progress.animateTo(1f, tween(1250, easing = FastOutSlowInEasing))
            delay(650)
            latestFinished()
        }
        BoxWithConstraints(modifier.fillMaxWidth().height(190.dp)) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val unit = with(density) { 1.dp.toPx() }
            val t = progress.value
            // The 200x165 GIF is mirrored: its mouth sits at (130, 100).
            val mouthX = widthPx - (8 + 160 * .35f) * unit
            val mouthY = (32 + 132 * (100f / 165f)) * unit
            event.tracks.take(3).reversed().forEachIndexed { index, track ->
                val flight = ((t - index * .07f) / .78f).coerceIn(0f, 1f)
                val startX = minOf(widthPx * .25f + index * 18 * unit, mouthX - 80 * unit)
                val x = (1-flight)*(1-flight)*startX + 2*(1-flight)*flight*(mouthX - 80*unit) + flight*flight*mouthX
                val y = (1-flight)*24*unit + flight*mouthY - sin(flight * Math.PI).toFloat()*35*unit
                ArtworkImage(track.thumbnailUrl, null, Modifier.size(64.dp).graphicsLayer {
                    translationX = x - 32*unit; translationY = y - 32*unit
                    scaleX = (1f - flight) * .95f + .05f; scaleY = scaleX
                    rotationZ = flight * -35f
                    alpha = if (flight >= .99f) 0f else 1f
                }.artworkSurface(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            }
            AnimatedAssetGif("kirby-inhale.gif", null,
                Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 8.dp).size(160.dp, 132.dp)
                    .graphicsLayer { scaleX = -1f })
            Text("Added ${event.tracks.size} to ${event.name}", color = PipesPalette.Text,
                modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}
