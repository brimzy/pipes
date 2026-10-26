package com.nazcarbon.music

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.ui.theme.PipesPalette as P
import kotlin.math.abs
import kotlin.math.roundToInt

// The layout uses a 4dp unit: 8 / 12 / 16 / 24 / 32, with 48dp touch targets.
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable internal fun QueueGesture(
    track: MusicTrack, onPlay: () -> Unit, onAdd: () -> Unit, onRemove: () -> Unit,
    onLike: (() -> Unit)? = null, onRadio: () -> Unit, onSave: () -> Unit,
    onSpotify: (() -> Unit)? = null, content: @Composable () -> Unit,
) {
    var drag by remember(track.id) { mutableFloatStateOf(0f) }
    var actions by remember(track.id) { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var dragging by remember(track.id) { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 88.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val offset by animateFloatAsState(drag, if (dragging) snap() else spring(dampingRatio = .88f, stiffness = 420f), label = "queue swipe")
    val add by rememberUpdatedState(onAdd)
    val remove by rememberUpdatedState(onRemove)
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.matchParentSize().background(if (drag > 0) P.Cyan.copy(alpha = .12f) else if (drag < 0) P.Raised else P.Raised).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (drag > 0) if (armed) "Release to queue" else "Add to queue" else "", color = P.Cyan, fontSize = 12.sp)
            Text(if (drag < 0) if (armed) "Release to remove" else "Remove" else "", color = P.TextSoft, fontSize = 12.sp)
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }.background(P.Background)
            .semantics {
                customActions = listOf(CustomAccessibilityAction("Add to queue") { add(); true },
                    CustomAccessibilityAction("Remove from queue") { remove(); true },
                    CustomAccessibilityAction("Song actions") { actions = true; true })
            }
            .pointerInput(track.id, threshold) {
                detectHorizontalDragGestures(onDragStart = { dragging = true }, onDragCancel = { dragging = false; drag = 0f; armed = false }, onDragEnd = {
                    if (drag >= threshold) add() else if (drag <= -threshold) remove()
                    dragging = false; drag = 0f; armed = false
                }) { change, delta ->
                    change.consume()
                    drag = (drag + delta).coerceIn(-threshold * 1.8f, threshold * 1.8f)
                    val crossed = abs(drag) >= threshold
                    if (crossed && !armed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    armed = crossed
                }
            }.combinedClickable(onClick = onPlay, onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress); actions = true
            })) { content() }
    }
    if (actions) ModalBottomSheet(onDismissRequest = { actions = false }, containerColor = P.Card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(track.title, color = P.SongTitle, style = MaterialTheme.typography.titleLarge)
            Text(track.artist, color = P.TextSoft, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            listOf<Pair<String, () -> Unit>>("Add to queue →" to onAdd, "← Remove from queue" to onRemove,
                "Start song radio" to onRadio, "Save to playlist" to onSave).plus(
                listOfNotNull(onLike?.let { "Like / unlike song" to it }, onSpotify?.let { "Search on Spotify" to it })
            ).forEach { (label, action) ->
                TextButton(onClick = { actions = false; action() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label, modifier = Modifier.fillMaxWidth(), color = P.Text) }
            }
        }
    }
}
