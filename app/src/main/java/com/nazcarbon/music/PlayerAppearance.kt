package com.nazcarbon.music

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nazcarbon.music.ui.theme.PipesPalette as P

/** Stays behind the scrolling content, including the space around the floating dock. */
@Composable
internal fun PlayerBackdrop(artwork: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(androidx.compose.ui.graphics.RectangleShape).background(P.Background)) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(P.Dock, P.Background, P.Dock))))
        Crossfade(artwork, animationSpec = tween(600), label = "artwork atmosphere") { url ->
            if (url.isNotBlank()) {
                ArtworkImage(url, null,
                    Modifier.fillMaxSize().blur(64.dp, BlurredEdgeTreatment.Unbounded),
                    contentScale = ContentScale.Crop, alpha = .48f)
            }
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            P.Background.copy(alpha = .38f),
            P.Background.copy(alpha = .60f),
            P.Background.copy(alpha = .78f),
        ))))
    }
}

/** A translucent ink finish, a lit edge, and a soft cast shadow shared by both players. */
internal fun Modifier.playerSurface(shape: Shape, elevation: Dp = 16.dp): Modifier =
    shadow(elevation, shape, clip = false,
        ambientColor = Color.Black.copy(alpha = .30f),
        spotColor = Color.Black.copy(alpha = .50f))
        .background(Brush.linearGradient(listOf(
            P.Raised.copy(alpha = .72f), P.Dock.copy(alpha = .84f),
        )), shape)
        .border(1.dp, Brush.linearGradient(listOf(
            P.White.copy(alpha = .14f), P.White.copy(alpha = .025f),
        )), shape)
        .clip(shape)

/** Stable star positions; a slow drift stays behind artwork and never intercepts input. */
@Composable
internal fun PlayerStars(playing: Boolean, modifier: Modifier = Modifier) {
    val phase = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(playing) {
        if (playing && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            while (true) {
                phase.animateTo(1f, androidx.compose.animation.core.tween(24000,
                    easing = androidx.compose.animation.core.LinearEasing))
                phase.snapTo(0f)
                kotlinx.coroutines.delay(16)
            }
        }
    }
    androidx.compose.foundation.Canvas(modifier) {
        val t = phase.value * kotlin.math.PI.toFloat() * 2f
        repeat(36) { i ->
            val x = ((i * .6180339f) % 1f) * size.width
            val y = ((i * .381966f + .13f) % 1f) * size.height
            val pulse = .5f + .5f * kotlin.math.sin(t + i * 2.4f)
            val center = androidx.compose.ui.geometry.Offset(
                x + kotlin.math.sin(t + i) * 3.dp.toPx(),
                y + kotlin.math.cos(t + i) * 5.dp.toPx())
            val radius = (if (i % 7 == 0) 1.2f else .7f).dp.toPx()
            drawCircle(P.White.copy(alpha = .06f + pulse * .16f), radius, center)
            if (i % 7 == 0) {
                val reach = radius * 3
                val color = P.White.copy(alpha = .04f + pulse * .08f)
                drawLine(color, center.copy(x = center.x - reach), center.copy(x = center.x + reach), .5.dp.toPx())
                drawLine(color, center.copy(y = center.y - reach), center.copy(y = center.y + reach), .5.dp.toPx())
            }
        }
    }
}
