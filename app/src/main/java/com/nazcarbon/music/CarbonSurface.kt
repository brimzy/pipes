package com.nazcarbon.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nazcarbon.music.ui.theme.PipesPalette

// One shape defines the shadow, edge, fill and clipping. Elevation follows 2 / 4 / 8 dp.
internal fun Modifier.carbonSurface(
    shape: Shape,
    elevation: Dp = 4.dp,
    color: Color = PipesPalette.Card,
    edge: Color = PipesPalette.Line.copy(alpha = .55f),
): Modifier = shadow(elevation, shape, clip = false,
    ambientColor = Color.Black.copy(alpha = .18f), spotColor = Color.Black.copy(alpha = .32f))
    .background(color, shape).border(1.dp, edge, shape).clip(shape)
