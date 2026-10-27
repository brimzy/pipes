package com.nazcarbon.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazcarbon.music.ui.theme.PipesPalette as P

// Reserved by the floating player and snackbar as well as the dock itself.
internal val DockClearance = 96.dp
private val destinations = listOf("Home", "Search", "Explore", "Library")

@Composable
internal fun NavigationDock(page: String, onSelect: (String) -> Unit) {
    val selected = when (page) {
        "Playlist" -> 3
        "Artist Search", "Artist", "Release" -> 1
        else -> destinations.indexOf(page)
    }
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val weights = destinations.indices.map { index ->
        animateFloatAsState(if (index == selected) 1.9f else 1f,
            spring(dampingRatio = .86f, stiffness = 520f), label = "dock width $index")
    }
    // Keep the last anchor while a secondary screen hides the marker.
    var anchor by remember { mutableIntStateOf(selected.coerceAtLeast(0)) }
    LaunchedEffect(selected) { if (selected >= 0) anchor = selected }
    val marker by animateFloatAsState(anchor.toFloat(), spring(.86f, 520f), label = "dock anchor")
    val markerAlpha by animateFloatAsState(if (selected >= 0) 1f else 0f, label = "dock marker visibility")
    val shape = RoundedCornerShape(20.dp)

    Box(Modifier.fillMaxWidth().padding(horizontal = PipesSpace.Page, vertical = 12.dp),
        contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 420.dp).fillMaxWidth()
            .playerSurface(shape)
            .padding(horizontal = 8.dp)
            .drawBehind {
                val widths = weights.map { it.value }
                val total = widths.sum()
                // Interpolate between the live centers as the active tab expands.
                val leftIndex = marker.toInt().coerceIn(0, 3)
                val rightIndex = (leftIndex + 1).coerceAtMost(3)
                fun center(index: Int) = (widths.take(index).sum() + widths[index] / 2) / total
                var x = (center(leftIndex) + (center(rightIndex) - center(leftIndex)) * (marker - leftIndex)) * size.width
                if (rtl) x = size.width - x
                val half = 10.dp.toPx()
                drawLine(P.Accent.copy(alpha = markerAlpha), Offset(x - half, size.height - 8.dp.toPx()),
                    Offset(x + half, size.height - 8.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            destinations.forEachIndexed { index, title ->
                val active = selected == index
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val focused by interaction.collectIsFocusedAsState()
                val compression by animateFloatAsState(if (pressed) .91f else 1f,
                    spring(.72f, 650f), label = "dock press $title")
                Row(Modifier.weight(weights[index].value).heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, if (focused) P.Accent else Color.Transparent, RoundedCornerShape(12.dp))
                    .selectable(active, interactionSource = interaction, indication = null, role = Role.Tab) {
                        if (!active) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSelect(title)
                    }.semantics { contentDescription = title }
                    .padding(horizontal = 8.dp, vertical = 16.dp)
                    .graphicsLayer { scaleX = compression; scaleY = compression },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    DockGlyph(index, if (active) P.Text else P.TextSoft.copy(alpha = .75f))
                    AnimatedVisibility(active, enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)) {
                        Text(title, Modifier.padding(start = 8.dp).clearAndSetSemantics {}, color = P.Text,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** A single 24-unit grid and stroke weight for the four navigation tools. */
@Composable
private fun DockGlyph(index: Int, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val line = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val path = Path()
            when (index) {
                0 -> {
                    path.moveTo(3f, 10f); path.lineTo(12f, 3f); path.lineTo(21f, 10f)
                    path.moveTo(5f, 9f); path.lineTo(5f, 21f); path.lineTo(10f, 21f)
                    path.lineTo(10f, 15f); path.lineTo(14f, 15f); path.lineTo(14f, 21f)
                    path.lineTo(19f, 21f); path.lineTo(19f, 9f)
                }
                1 -> {
                    drawCircle(color, 7f, Offset(10f, 10f), style = line)
                    path.moveTo(15f, 15f); path.lineTo(21f, 21f)
                }
                2 -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = line)
                    path.moveTo(16f, 8f); path.lineTo(14f, 14f); path.lineTo(8f, 16f)
                    path.lineTo(10f, 10f); path.close()
                }
                else -> {
                    path.moveTo(4f, 4f); path.lineTo(4f, 20f)
                    path.moveTo(9f, 4f); path.lineTo(9f, 20f)
                    path.moveTo(14f, 5f); path.lineTo(18f, 4f); path.lineTo(22f, 19f)
                    path.lineTo(18f, 20f); path.close()
                }
            }
            drawPath(path, color, style = line)
        }
    }
}
