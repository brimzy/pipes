package com.nazcarbon.music

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nazcarbon.music.ui.theme.PipesPalette
import kotlin.math.abs

@Composable
fun PipesLoading(modifier: Modifier = Modifier) {
    val phase by rememberInfiniteTransition(label = "loading signal").animateFloat(0f, 4f,
        infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "loading phase")
    Canvas(modifier.size(48.dp).semantics { contentDescription = "Loading music" }) {
        for (i in 0..3) {
            val strength = (1f - abs(i - phase) / 2).coerceIn(0f, 1f)
            drawCircle(PipesPalette.Text.copy(alpha = .2f + .8f * strength),
                size.minDimension * (.035f + .025f * strength), Offset(size.width * (.2f + i * .2f), size.height / 2))
        }
    }
}
