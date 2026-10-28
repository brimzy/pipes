package com.nazcarbon.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nazcarbon.music.data.MusicTrack

internal data class TrackSelection(val tracks: List<MusicTrack> = emptyList(), val toggle: (MusicTrack) -> Unit = {}) {
    val active get() = tracks.isNotEmpty()
    fun contains(track: MusicTrack) = tracks.any { it.id == track.id }
}
internal val LocalTrackSelection = staticCompositionLocalOf { TrackSelection() }
internal val LocalDownloadTrack = staticCompositionLocalOf<(MusicTrack) -> Unit> { {} }

@Composable
internal fun SelectionBar(count: Int, onAll: () -> Unit, onAdd: () -> Unit, onCancel: () -> Unit, onRemove: (() -> Unit)?) {
    Surface(tonalElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("$count selected", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = onAll) { Text("Select all") }
                TextButton(onClick = onAdd) { Text("Add to…") }
                onRemove?.let { TextButton(onClick = it) { Text("Remove") } }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
