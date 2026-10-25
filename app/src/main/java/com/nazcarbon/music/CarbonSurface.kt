package com.nazcarbon.music

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nazcarbon.music.ui.theme.PipesPalette

internal object PipesSpace {
    val Page = 24.dp
    val Section = 32.dp
    val Gap = 16.dp
    val ArtworkText = 8.dp
    val Metadata = 4.dp
}

// Surfaces use their fill and shadow for depth. Edges are opt-in, never a default frame.
internal fun Modifier.carbonSurface(
    shape: Shape,
    elevation: Dp = 4.dp,
    color: Color = PipesPalette.Card,
    edge: Color = Color.Transparent,
): Modifier = shadow(elevation, shape, clip = false,
    ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .24f))
    .background(color, shape).clip(shape).then(if (edge.alpha > 0f) Modifier.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            // The clip retains the inside half of this stroke: exactly two physical pixels.
            drawOutline(outline, edge, style = Stroke(width = 4f))
        }
    } else Modifier)

internal fun Modifier.artworkSurface(shape: Shape, elevation: Dp = 4.dp): Modifier =
    carbonSurface(shape, elevation, PipesPalette.Dock, PipesPalette.Ice.copy(alpha = .20f))
