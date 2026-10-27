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
            P.Raised.copy(alpha = .88f), P.Dock.copy(alpha = .96f),
        )), shape)
        .border(1.dp, Brush.linearGradient(listOf(
            P.White.copy(alpha = .14f), P.White.copy(alpha = .025f),
        )), shape)
        .clip(shape)
