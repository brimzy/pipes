package com.nazcarbon.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal fun trackSwipeDirection(distance: Float, threshold: Float, hasNext: Boolean): Int = when {
    distance <= -threshold && hasNext -> 1
    distance >= threshold -> -1
    else -> 0
}

/** Attach to the metadata row so the seek bar retains its own horizontal gesture. */
internal fun Modifier.trackSwipe(key: String, hasNext: Boolean, previous: () -> Unit, next: () -> Unit): Modifier = composed {
    var drag by remember(key) { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val latestPrevious by rememberUpdatedState(previous)
    val latestNext by rememberUpdatedState(next)
    val latestHasNext by rememberUpdatedState(hasNext)
    val offset by animateFloatAsState(drag * .25f, spring(.85f, 500f), label = "mini track swipe")
    this.graphicsLayer { translationX = offset }.semantics {
        customActions = listOfNotNull(
            CustomAccessibilityAction("Previous song") { latestPrevious(); true },
            if (hasNext) CustomAccessibilityAction("Next song") { latestNext(); true } else null)
    }.pointerInput(key, threshold) {
        detectHorizontalDragGestures(onDragCancel = { drag = 0f }, onDragEnd = {
            val direction = trackSwipeDirection(drag, threshold, latestHasNext)
            drag = 0f
            if (direction != 0) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (direction > 0) latestNext() else latestPrevious()
            }
        }) { change, amount ->
            change.consume()
            drag = (drag + amount).coerceIn(-threshold * 2, threshold * 2)
        }
    }
}
