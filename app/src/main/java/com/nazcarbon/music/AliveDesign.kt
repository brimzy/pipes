package com.nazcarbon.music

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
    val threshold = with(LocalDensity.current) { 88.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val offset by animateFloatAsState(drag, spring(dampingRatio = .72f), label = "queue swipe")
    val add by rememberUpdatedState(onAdd)
    val remove by rememberUpdatedState(onRemove)
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.matchParentSize().background(if (drag > 0) P.Cyan.copy(alpha = .12f) else if (drag < 0) P.Violet.copy(alpha = .12f) else P.Raised).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (drag > 0) if (armed) "Release to queue" else "Add to queue" else "", color = P.Cyan, fontSize = 12.sp)
            Text(if (drag < 0) if (armed) "Release to remove" else "Remove" else "", color = P.Violet, fontSize = 12.sp)
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }.background(P.Background)
            .semantics {
                customActions = listOf(CustomAccessibilityAction("Add to queue") { add(); true },
                    CustomAccessibilityAction("Remove from queue") { remove(); true },
                    CustomAccessibilityAction("Song actions") { actions = true; true })
            }
            .pointerInput(track.id, threshold) {
                detectHorizontalDragGestures(onDragCancel = { drag = 0f; armed = false }, onDragEnd = {
                    if (drag >= threshold) add() else if (drag <= -threshold) remove()
                    drag = 0f; armed = false
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
            Text(track.title, color = P.Text, style = MaterialTheme.typography.titleLarge)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun FloatingPlayer(track: MusicTrack, playing: Boolean, toggle: () -> Unit, expand: () -> Unit,
    next: () -> Unit, previous: () -> Unit, position: Long, duration: Long) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 88.dp, start = 16.dp, end = 16.dp, top = 64.dp)) {
        val density = LocalDensity.current
        val limitX = with(density) { (maxWidth - 80.dp).coerceAtLeast(0.dp).toPx() }
        val limitY = with(density) { (maxHeight - 80.dp).coerceAtLeast(0.dp).toPx() }
        var x by remember { mutableFloatStateOf(Float.NaN) }
        var y by remember { mutableFloatStateOf(Float.NaN) }
        var dragging by remember { mutableStateOf(false) }
        val px by animateFloatAsState((if (x.isNaN()) limitX else x).coerceIn(0f, limitX), if (dragging) snap() else spring(.58f, 350f), label = "orb x")
        val py by animateFloatAsState((if (y.isNaN()) limitY else y).coerceIn(0f, limitY), if (dragging) snap() else spring(.58f, 350f), label = "orb y")
        var controls by remember { mutableStateOf(false) }
        Box(Modifier.offset { IntOffset(px.roundToInt(), py.roundToInt()) }.size(80.dp)
            .shadow(if (dragging) 24.dp else 16.dp, CircleShape).clip(CircleShape)
            .background(P.Raised).border(1.dp, P.Line, CircleShape)
            .semantics { contentDescription = "${track.title}, ${if (playing) "playing" else "paused"}. Tap to open player. Hold for controls. Drag to move."
                customActions = listOf(CustomAccessibilityAction(if (playing) "Pause" else "Play") { toggle(); true },
                    CustomAccessibilityAction("Next song") { next(); true }, CustomAccessibilityAction("Previous song") { previous(); true }) }
            .pointerInput(limitX, limitY) {
                detectDragGestures(onDragStart = { dragging = true; x = px; y = py }, onDragCancel = { dragging = false }, onDragEnd = {
                    dragging = false; x = if (x < limitX / 2) 0f else limitX
                }) { change, delta -> change.consume(); x = (x + delta.x).coerceIn(0f, limitX); y = (y + delta.y).coerceIn(0f, limitY) }
            }.combinedClickable(onClick = expand, onLongClick = { controls = true }), contentAlignment = Alignment.Center) {
            ArtworkImage(track.thumbnailUrl, null, Modifier.size(68.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                drawArc(P.Text.copy(alpha = .16f), -90f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawArc(P.Cyan, -90f, if (duration > 0) 360f * (position.toFloat() / duration).coerceIn(0f, 1f) else 0f, false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            Box(Modifier.size(28.dp).background(P.Background.copy(alpha = .72f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = P.Text, modifier = Modifier.size(16.dp))
            }
        }
        if (controls) AlertDialog(onDismissRequest = { controls = false }, title = { Text(track.title) },
            text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = previous) { Icon(Icons.Default.SkipPrevious, "Previous song") }
                IconButton(onClick = toggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play / pause") }
                IconButton(onClick = next) { Icon(Icons.Default.SkipNext, "Next song") }
            } }, confirmButton = { TextButton(onClick = { controls = false; expand() }) { Text("Open player") } })
    }
}
