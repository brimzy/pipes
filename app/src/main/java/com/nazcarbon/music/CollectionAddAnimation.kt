package com.nazcarbon.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

/** A right-facing Kirby inhales the cover stack. Saving is complete before this feedback begins. */
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
            // The cover approaches from above, sweeps around to the right, then enters the mouth.
            val mouthX = widthPx - 35 * unit
            val mouthY = 103 * unit
            event.tracks.take(3).reversed().forEachIndexed { index, track ->
                val flight = ((t - index * .07f) / .78f).coerceIn(0f, 1f)
                val startX = widthPx * .3f + index * 18 * unit
                val x = (1-flight)*(1-flight)*startX + 2*(1-flight)*flight*(widthPx + 70*unit) + flight*flight*mouthX
                val y = (1-flight)*24*unit + flight*mouthY - sin(flight * Math.PI).toFloat()*35*unit
                ArtworkImage(track.thumbnailUrl, null, Modifier.size(64.dp).graphicsLayer {
                    translationX = x - 32*unit; translationY = y - 32*unit
                    scaleX = (1f - flight) * .95f + .05f; scaleY = scaleX
                    rotationZ = flight * -35f
                    alpha = if (flight >= .99f) 0f else 1f
                }.artworkSurface(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            }
            Canvas(Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 8.dp).size(104.dp, 88.dp)
                .graphicsLayer {
                    val bounce = sin(t * Math.PI * 4).toFloat() * .035f
                    scaleX = 1f + bounce; scaleY = 1f - bounce
                }) {
                fun oval(color: Color, x: Float, y: Float, w: Float, h: Float) =
                    drawOval(color, Offset(size.width*x, size.height*y), Size(size.width*w, size.height*h))
                val pink = Color(0xFFFFA5C7)
                val shade = Color(0xFFEE719F)
                val foot = Color(0xFFCC376C)
                oval(foot, .13f, .72f, .39f, .24f)
                oval(foot, .51f, .72f, .39f, .24f)
                oval(shade, .02f, .36f, .35f, .34f)
                oval(pink, .14f, .04f, .75f, .84f)
                oval(shade, .56f, .49f, .14f, .09f)
                oval(Color(0xFF332744), .63f, .21f, .065f, .21f)
                oval(Color.White, .644f, .235f, .027f, .075f)
                // Profile faces right; the expanding mouth meets the incoming artwork.
                oval(Color(0xFF61223F), .75f, .39f, .22f, .31f)
                oval(Color(0xFF251A2D), .81f, .43f, .14f, .23f)
                oval(pink, .14f, .39f, .23f, .28f)
                if (t > .8f) {
                    val sparkle = PipesPalette.Cyan.copy(alpha = (1f - t) * 5f)
                    drawCircle(sparkle, 3.dp.toPx(), Offset(size.width*.9f, size.height*.1f))
                }
            }
            Text("Added ${event.tracks.size} to ${event.name}", color = PipesPalette.Text,
                modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}
