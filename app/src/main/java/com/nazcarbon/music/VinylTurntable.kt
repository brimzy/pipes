package com.nazcarbon.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.data.vinylAngleDelta
import com.nazcarbon.music.data.vinylSeekPosition
import com.nazcarbon.music.ui.theme.PipesPalette
import kotlin.math.atan2

@Composable internal fun VinylTurntable(
    track: MusicTrack, playing: Boolean, position: Long, duration: Long, enabled: Boolean,
    modifier: Modifier, onSeek: (Long) -> Unit, onPause: () -> Unit, onResume: () -> Unit,
) {
    var touching by remember(track.id) { mutableStateOf(false) }
    var rotation by remember(track.id) { mutableFloatStateOf(0f) }
    var swept by remember(track.id) { mutableFloatStateOf(0f) }
    var startPosition by remember(track.id) { mutableLongStateOf(0) }
    var wasPlaying by remember(track.id) { mutableStateOf(false) }
    val latestPosition by rememberUpdatedState(position)
    val latestPlaying by rememberUpdatedState(playing)
    val latestSeek by rememberUpdatedState(onSeek)
    val latestPause by rememberUpdatedState(onPause)
    val latestResume by rememberUpdatedState(onResume)
    val tilt by animateFloatAsState(if (touching) 34f else 64f,
        spring(dampingRatio = .52f, stiffness = 200f), label = "record lift")
    LaunchedEffect(playing, touching) {
        if (playing && !touching) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                rotation = (rotation + (now - last) / 1_000_000_000f * 36f) % 360f
                last = now
            }
        }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth - 24.dp, 290.dp)
        VinylDisc(track.thumbnailUrl, Modifier.size(diameter).graphicsLayer {
            rotationX = tilt
            cameraDistance = 28f * density
            shadowElevation = 8.dp.toPx()
            shape = CircleShape
        }.graphicsLayer { rotationZ = rotation })
        Box(Modifier.fillMaxSize().semantics {
            contentDescription = "Vinyl record for ${track.title}. Rotate to seek. Seek slider and track buttons below."
        }.pointerInput(track.id, enabled, duration) {
            if (!enabled || duration <= 0) return@pointerInput
            var previousAngle = 0f
            fun angle(point: Offset): Float = (atan2(
                (point.y - size.height / 2f) / .55f,
                point.x - size.width / 2f) * 180f / Math.PI).toFloat()
            fun finish(commit: Boolean) {
                if (!touching) return
                if (commit) latestSeek(vinylSeekPosition(startPosition, swept, duration))
                touching = false
                if (wasPlaying) latestResume()
            }
            detectDragGestures(onDragStart = { point ->
                previousAngle = angle(point)
                startPosition = latestPosition
                swept = 0f
                wasPlaying = latestPlaying
                touching = true
                if (wasPlaying) latestPause()
            }, onDragEnd = { finish(true) }, onDragCancel = { finish(false) }) { change, _ ->
                change.consume()
                val next = angle(change.position)
                val delta = vinylAngleDelta(previousAngle, next)
                previousAngle = next
                swept += delta
                rotation += delta
            }
        })
        Text(if (touching) {
            val delta = (vinylSeekPosition(startPosition, swept, duration) - startPosition) / 1000
            "${if (delta >= 0) "+" else ""}${delta}s"
        } else if (enabled) "Rotate the record to seek" else "Vinyl playback",
            color = if (touching) PipesPalette.Accent else PipesPalette.TextSoft,
            fontSize = 11.sp, modifier = Modifier.align(if (touching) Alignment.TopCenter else Alignment.BottomCenter))
    }
}
