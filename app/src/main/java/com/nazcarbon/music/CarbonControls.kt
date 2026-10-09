package com.nazcarbon.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
        thumb = {
            Box(Modifier.size(12.dp, 36.dp).drawBehind {
                drawCircle(Brush.radialGradient(listOf(PipesPalette.Accent.copy(alpha = light * .5f), Color.Transparent), radius = 32.dp.toPx()), radius = 32.dp.toPx())
            }.padding(horizontal = 4.dp).clip(CircleShape)
                .background(if (enabled) PipesPalette.Text else PipesPalette.Line))
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(44.dp)) {
                val fraction = ((state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
                val cursor = size.width * fraction
                if (enabled) drawRect(Brush.radialGradient(listOf(PipesPalette.Accent.copy(alpha = .08f + light * .2f), Color.Transparent), center = Offset(cursor, size.height / 2), radius = size.height * 2))
                val count = (size.width / 3.dp.toPx()).toInt().coerceAtLeast(1)
                repeat(count) { i ->
                    val x = (i + .5f) * size.width / count
                    val envelope = .25f + .75f * sin(i * .13f).let { it * it }
                    val height = (4.dp.toPx() + 26.dp.toPx() * envelope * (.35f + .65f * sin(i * 1.71f).let { it * it })) * (1f + light * .15f)
                    drawLine(if (!enabled) PipesPalette.Line else if (x <= cursor) PipesPalette.Accent else PipesPalette.Text.copy(alpha = .22f + light * .1f), Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2), 1.3.dp.toPx(), StrokeCap.Round)
                }
            }
        })
}

@Composable
internal fun CarbonPlayButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val pressed by interactions.collectIsPressedAsState()
    val light by animateFloatAsState(if (hovered || pressed) 1f else .15f, label = "button light")
    FilledIconButton(onClick = onClick, interactionSource = interactions,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Transparent, contentColor = PipesPalette.Text),
        modifier = Modifier.size(72.dp).hoverable(interactions).drawBehind {
            drawCircle(Brush.radialGradient(listOf(PipesPalette.Accent.copy(alpha = light * .35f), Color.Transparent), radius = size.width * .8f), radius = size.width * .8f)
        }.border(1.dp, PipesPalette.Accent.copy(alpha = .2f + light * .5f), CircleShape)
            .background(Brush.linearGradient(listOf(PipesPalette.Text.copy(alpha = .15f + light * .1f), PipesPalette.Raised, PipesPalette.Background)), CircleShape), content = content)
}
