package com.nazcarbon.music

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
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
import kotlin.math.roundToInt

internal enum class QueueSwipeAction(val label: String) {
    ADD("Add to queue"),
    PLAY_NEXT("Play next"),
    REMOVE("Remove from queue"),
}

internal fun queueSwipeAction(distance: Float, threshold: Float, nextThreshold: Float): QueueSwipeAction? = when {
    distance <= -nextThreshold -> QueueSwipeAction.PLAY_NEXT
    distance <= -threshold -> QueueSwipeAction.ADD
    distance >= threshold -> QueueSwipeAction.REMOVE
    else -> null
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable internal fun QueueGesture(
    track: MusicTrack, onPlay: () -> Unit, onAdd: () -> Unit, onRemove: () -> Unit,
    onLike: (() -> Unit)? = null, onRadio: () -> Unit, onSave: () -> Unit,
    onSpotify: (() -> Unit)? = null, modifier: Modifier = Modifier,
    onClickLabel: String = "Play ${track.title}", content: @Composable () -> Unit,
) {
    var drag by remember(track.id) { mutableFloatStateOf(0f) }
    var actions by remember(track.id) { mutableStateOf(false) }
    var dragging by remember(track.id) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val offset by animateFloatAsState(drag, if (dragging) snap() else spring(dampingRatio = .88f, stiffness = 420f), label = "queue swipe")
    val playNext = LocalPlayNext.current
    val next by rememberUpdatedState { playNext(track) }
    val selection = LocalTrackSelection.current
    val latestSelection by rememberUpdatedState(selection)
    val download = LocalDownloadTrack.current
    val selected = selection.contains(track)
    LaunchedEffect(selection.active) {
        if (selection.active) {
            drag = 0f
            dragging = false
        }
    }
    val add by rememberUpdatedState(onAdd)
    val remove by rememberUpdatedState(onRemove)
    BoxWithConstraints(modifier.fillMaxWidth().clipToBounds()
        .then(if (selected) Modifier.background(P.Cyan.copy(alpha = .12f)) else Modifier)) {
        val addDistance = minOf(64.dp, maxWidth * .38f)
        val nextDistance = minOf(112.dp, maxWidth * .55f)
        val threshold = with(LocalDensity.current) { addDistance.toPx() }
        val nextThreshold = with(LocalDensity.current) { nextDistance.toPx() }
        val armed = queueSwipeAction(drag, threshold, nextThreshold)
        if (offset != 0f) {
            val removing = offset > 0
            val preview = armed ?: if (removing) QueueSwipeAction.REMOVE else QueueSwipeAction.ADD
            val tint = if (removing) P.TextSoft else P.Cyan
            Box(Modifier.matchParentSize()
                .background(if (removing) P.Raised else P.Cyan.copy(alpha = if (armed == QueueSwipeAction.PLAY_NEXT) .24f else .12f))
                .padding(horizontal = 8.dp),
                contentAlignment = if (removing) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight,
            ) {
                Text(when {
                    removing -> "Remove"
                    preview == QueueSwipeAction.ADD && addDistance < 64.dp -> "Add to\nqueue"
                    else -> preview.label
                }, color = tint, fontSize = 12.sp)
            }
        }
        Box(Modifier.absoluteOffset { IntOffset(offset.roundToInt(), 0) }
            .background(if (dragging || offset != 0f) P.Background else Color.Transparent)
            .semantics {
                customActions = listOf(CustomAccessibilityAction("Play next") { playNext(track); true },
                    CustomAccessibilityAction("Add to queue") { add(); true },
                    CustomAccessibilityAction("Remove from queue") { remove(); true },
                    CustomAccessibilityAction("Song actions") { actions = true; true },
                    CustomAccessibilityAction(if (selected) "Deselect song" else "Select song") { selection.toggle(track); true })
                this.selected = selected
            }
            .pointerInput(track.id, threshold, nextThreshold, selection.active) {
                if (selection.active) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragCancel = { dragging = false; drag = 0f },
                    onDragEnd = {
                        val action = queueSwipeAction(drag, threshold, nextThreshold)
                        dragging = false
                        drag = 0f
                        when (action) {
                            QueueSwipeAction.ADD -> add()
                            QueueSwipeAction.PLAY_NEXT -> next()
                            QueueSwipeAction.REMOVE -> remove()
                            null -> Unit
                        }
                    },
                ) { change, delta ->
                    change.consume()
                    val previousAction = queueSwipeAction(drag, threshold, nextThreshold)
                    drag = (drag + delta).coerceIn(-nextThreshold * 1.35f, threshold * 1.5f)
                    val action = queueSwipeAction(drag, threshold, nextThreshold)
                    if (action != null && action != previousAction) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            }.combinedClickable(
                role = Role.Button,
                onClickLabel = if (selection.active) "Select ${track.title}" else onClickLabel,
                onClick = { if (latestSelection.active) latestSelection.toggle(track) else onPlay() },
                onLongClickLabel = "Select song",
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    latestSelection.toggle(track)
                },
            )) { content() }
        if (selected) Icon(Icons.Default.CheckCircle, "Selected", tint = P.Cyan,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp))
        if (!selection.active) IconButton(onClick = { actions = true },
            modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
            Icon(Icons.Default.MoreVert, "Song actions for ${track.title}", tint = P.Text,
                modifier = Modifier.size(20.dp).background(P.Background.copy(alpha = .75f), androidx.compose.foundation.shape.CircleShape))
        }
    }
    if (actions) ModalBottomSheet(onDismissRequest = { actions = false }, containerColor = P.Card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(track.title, color = P.SongTitle, style = MaterialTheme.typography.titleLarge)
            Text(track.artist, color = P.TextSoft, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            listOf<Pair<String, () -> Unit>>("Play next" to { playNext(track) }, "Add to queue" to onAdd, "Remove from queue" to onRemove,
                "Start song radio" to onRadio, "Add to playlist or album" to onSave,
                "Select songs" to { selection.toggle(track) }, trackDownloadState(track).label to { download(track) }).plus(
                listOfNotNull(onLike?.let { "Like / unlike song" to it }, onSpotify?.let { "Search on Spotify" to it })
            ).forEach { (label, action) ->
                TextButton(onClick = { actions = false; action() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label, modifier = Modifier.fillMaxWidth(), color = P.Text) }
            }
        }
    }
}
