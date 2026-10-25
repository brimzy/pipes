package com.nazcarbon.music

import androidx.compose.animation.core.animateFloatAsState
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
        thumb = { Spacer(Modifier.size(0.dp)) },
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
                        drawCircle(Brush.radialGradient(listOf(PipesPalette.Text.copy(alpha = .12f + .18f * light), Color.Transparent),
                            center = Offset(cursor, size.height / 2), radius = radius), radius, Offset(cursor, size.height / 2))
                    }
                    for ((start, end) in bars) {
                        val glow = if (enabled) light * (1f - abs(start.x - cursor) / radius).coerceIn(0f, 1f) else 0f
                        val base = if (start.x <= cursor) PipesPalette.Accent else PipesPalette.Text.copy(alpha = .22f)
                        val color = androidx.compose.ui.graphics.lerp(base, Color(0xFFDEFFFF), glow)
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
internal fun CarbonPlayButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val shade by animateFloatAsState(if (pressed) .12f else if (hovered) .09f else .05f, label = "button shade")
    FilledIconButton(onClick = onClick, interactionSource = interactions,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent, contentColor = PipesPalette.Text),
        modifier = Modifier.size(64.dp).hoverable(interactions)
            .shadow(if (pressed) 3.dp else 10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Brush.verticalGradient(listOf(Color(.14f + shade, .14f + shade, .14f + shade), PipesPalette.Raised)), CircleShape),
        content = content)
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
