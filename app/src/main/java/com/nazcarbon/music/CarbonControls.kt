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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.nazcarbon.music.ui.theme.PipesPalette
import kotlin.math.sin

/** Decorative waveform, not an amplitude measurement. Native Slider retains seeking/accessibility. */
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
            Canvas(Modifier.fillMaxWidth().height(44.dp)) {
                val fraction = ((state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
                val cursor = size.width * fraction
                val count = (size.width / 3.dp.toPx()).toInt().coerceAtLeast(1)
                repeat(count) { i ->
                    val x = (i + .5f) * size.width / count
                    val envelope = .25f + .75f * sin(i * .13f).let { it * it }
                    val height = (4.dp.toPx() + 26.dp.toPx() * envelope * (.35f + .65f * sin(i * 1.71f).let { it * it }))
                    if (enabled && light > .01f) {
                        // Light belongs to the waveform bars, with no handle or rectangular halo.
                        val proximity = (1f - kotlin.math.abs(x - cursor) / (36.dp.toPx())).coerceIn(0f, 1f)
                        drawLine(PipesPalette.Accent.copy(alpha = light * proximity * .65f), Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2), 5.dp.toPx(), StrokeCap.Round)
                    }
                    val glow = if (enabled) light * (1f - kotlin.math.abs(x - cursor) / 28.dp.toPx()).coerceIn(0f, 1f) else 0f
                    val base = if (x <= cursor) PipesPalette.Accent else PipesPalette.Text.copy(alpha = .22f)
                    val illuminated = androidx.compose.ui.graphics.lerp(base, Color(0xFFDEFFFF), glow)
                    drawLine(if (!enabled) PipesPalette.Line else illuminated, Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2), 1.3.dp.toPx(), StrokeCap.Round)
                }
            }
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
