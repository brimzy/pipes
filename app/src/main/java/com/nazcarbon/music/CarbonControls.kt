package com.nazcarbon.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazcarbon.music.ui.theme.PipesPalette
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CarbonSeekBar(value: Float, maximum: Float, enabled: Boolean, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val dragged by interactions.collectIsDraggedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val light by animateFloatAsState(if (dragged || hovered || pressed) 1f else 0f, label = "seek light")
    Slider(value = value.coerceIn(0f, maximum), onValueChange = onChange,
        onValueChangeFinished = onFinished, valueRange = 0f..maximum, enabled = enabled,
        interactionSource = interactions, modifier = Modifier.fillMaxWidth().hoverable(interactions),
        thumb = {
            Box(Modifier.size(20.dp).background(PipesPalette.Cyan, CircleShape))
        },
        track = { state ->
            Spacer(Modifier.fillMaxWidth().height(44.dp).drawWithCache {
                val count = (size.width / 3.dp.toPx()).toInt().coerceAtLeast(1)
                val bars = List(count) { i ->
                    val x = (i + .5f) * size.width / count
                    val envelope = .25f + .75f * sin(i * .13f).let { it * it }
                    val height = 4.dp.toPx() + 26.dp.toPx() * envelope * (.35f + .65f * sin(i * 1.71f).let { it * it })
                    Offset(x, (size.height - height) / 2) to Offset(x, (size.height + height) / 2)
                }
                val radius = (20 + 24 * light).dp.toPx()
                val width = 1.3.dp.toPx()
                onDrawBehind {
                    val fraction = ((state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
                    val cursor = size.width * fraction
                    if (enabled) {
                        drawCircle(Brush.radialGradient(listOf(PipesPalette.Cyan.copy(alpha = .12f + .18f * light), Color.Transparent),
                            center = Offset(cursor, size.height / 2), radius = radius), radius, Offset(cursor, size.height / 2))
                    }
                    for ((start, end) in bars) {
                        val glow = if (enabled) light * (1f - abs(start.x - cursor) / radius).coerceIn(0f, 1f) else 0f
                        val base = if (start.x <= cursor) PipesPalette.Cyan else PipesPalette.Text.copy(alpha = .22f)
                        val color = androidx.compose.ui.graphics.lerp(base, PipesPalette.Text, glow)
                        val grow = 1f + glow * .45f
                        val middle = size.height / 2
                        drawLine(if (enabled) color else PipesPalette.Line,
                            Offset(start.x, middle + (start.y - middle) * grow),
                            Offset(end.x, middle + (end.y - middle) * grow), width + glow * 1.dp.toPx(), StrokeCap.Round)
                    }
                }
            })
        })
}

@Composable
internal fun CarbonPlayButton(playing: Boolean, buffering: Boolean, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val compression = animateFloatAsState(if (pressed) .94f else 1f,
        spring(dampingRatio = .85f, stiffness = 650f), label = "transport press")
    val morph = animateFloatAsState(if (playing) 1f else 0f, tween(220), label = "play pause morph")
    val edge by animateColorAsState(if (playing || hovered) PipesPalette.Accent.copy(alpha = .55f) else PipesPalette.Line,
        tween(220), label = "transport edge")
    IconButton(onClick = onClick, interactionSource = interactions,
        modifier = Modifier.size(80.dp).hoverable(interactions)
            .semantics { contentDescription = if (playing) "Pause" else "Play" }) {
        Box(Modifier.fillMaxSize().carbonSurface(CircleShape, 4.dp, PipesPalette.Card, edge), contentAlignment = Alignment.Center) {
            Box(Modifier.size(60.dp).graphicsLayer {
                scaleX = compression.value; scaleY = compression.value
                translationY = (1f - compression.value) * 24.dp.toPx()
            }.carbonSurface(CircleShape, if (pressed) 2.dp else 4.dp, PipesPalette.Raised,
                PipesPalette.Text.copy(alpha = if (pressed) .05f else .1f)), contentAlignment = Alignment.Center) {
                Crossfade(targetState = buffering, animationSpec = tween(160), label = "transport buffering") { busy ->
                    if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = PipesPalette.Cyan, strokeWidth = 2.dp)
                    else Canvas(Modifier.size(36.dp)) {
                        val t = morph.value
                        fun mix(a: Float, b: Float) = (a + (b - a) * t) * size.width / 36f
                        fun polygon(play: List<Offset>, pause: List<Offset>) {
                            val path = Path()
                            play.indices.forEach { i ->
                                val x = mix(play[i].x, pause[i].x)
                                val y = mix(play[i].y, pause[i].y)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            path.close()
                            drawPath(path, PipesPalette.Accent)
                        }
                        polygon(listOf(Offset(10f, 6f), Offset(20f, 12f), Offset(20f, 24f), Offset(10f, 30f)),
                            listOf(Offset(10f, 8f), Offset(15f, 8f), Offset(15f, 28f), Offset(10f, 28f)))
                        polygon(listOf(Offset(20f, 12f), Offset(30f, 18f), Offset(30f, 18f), Offset(20f, 24f)),
                            listOf(Offset(21f, 8f), Offset(26f, 8f), Offset(26f, 28f), Offset(21f, 28f)))
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerSeekControls(position: Long, duration: Long, seekable: Boolean, playing: Boolean, onSeek: (Long) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    var pending by remember { mutableStateOf<Float?>(null) }
    val animated by animateFloatAsState(position.toFloat(), tween(if (playing && pending == null) 300 else 0), label = "playhead")
    val progress = (drag ?: pending ?: animated).coerceIn(0f, duration.coerceAtLeast(1).toFloat())
    LaunchedEffect(position, pending) {
        if (pending != null && abs(position - pending!!.toLong()) < 1000) pending = null
    }
    LaunchedEffect(pending) {
        if (pending != null) { delay(1500); pending = null }
    }
    Column {
        CarbonSeekBar(progress, duration.coerceAtLeast(1).toFloat(), seekable && duration > 0,
            onChange = { drag = it }, onFinished = {
                drag?.let { pending = it; onSeek(it.toLong()) }
                drag = null
            })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            fun time(value: Long): String {
                val seconds = value.coerceAtLeast(0) / 1000
                return "%d:%02d".format(seconds / 60, seconds % 60)
            }
            Text(time(progress.toLong()), color = PipesPalette.TextSoft, fontSize = 12.sp)
            Text(if (duration > 0) time(duration) else "--:--", color = PipesPalette.TextSoft, fontSize = 12.sp)
        }
    }
}
