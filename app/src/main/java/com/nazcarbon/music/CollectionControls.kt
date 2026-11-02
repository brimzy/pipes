package com.nazcarbon.music

import android.app.DownloadManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nazcarbon.music.data.CollectionSort
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.ui.theme.PipesPalette

internal enum class DownloadState(val label: String) {
    AVAILABLE("Download song"), QUEUED("Download queued"), SAVED("Available offline"), FAILED("Retry download")
}

internal fun downloadState(status: Int?): DownloadState = when (status) {
    DownloadManager.STATUS_SUCCESSFUL -> DownloadState.SAVED
    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> DownloadState.QUEUED
    else -> DownloadState.FAILED
}

internal val LocalDownloadStates = staticCompositionLocalOf<Map<String, DownloadState>> { emptyMap() }

@Composable internal fun trackDownloadState(track: MusicTrack): DownloadState =
    if (track.pageUrl.startsWith("content://") || track.pageUrl.startsWith("file://") || track.id.startsWith("download:")) DownloadState.SAVED
    else LocalDownloadStates.current[track.id] ?: DownloadState.AVAILABLE

@Composable internal fun DownloadStatusIcon(track: MusicTrack, modifier: Modifier = Modifier) {
    val state = trackDownloadState(track)
    Icon(when (state) {
        DownloadState.SAVED -> Icons.Default.OfflinePin
        DownloadState.QUEUED -> Icons.Default.Downloading
        DownloadState.FAILED -> Icons.Default.ErrorOutline
        DownloadState.AVAILABLE -> Icons.Default.Download
    }, state.label, modifier, tint = if (state == DownloadState.SAVED) PipesPalette.Accent else PipesPalette.TextSoft)
}

@Composable internal fun rememberCollectionSort(key: String): MutableState<CollectionSort> {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("pipes", Context.MODE_PRIVATE) }
    val state = remember(key) { mutableStateOf(CollectionSort.from(preferences.getString("sort:$key", null))) }
    LaunchedEffect(key, state.value) { preferences.edit().putString("sort:$key", state.value.name).apply() }
    return state
}

@Composable internal fun CollectionSortButton(
    sort: MutableState<CollectionSort>, choices: List<CollectionSort> = CollectionSort.entries,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
            Spacer(Modifier.width(6.dp))
            Text(sort.value.label)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(if (sort.value == choice) "✓ ${choice.label}" else choice.label) },
                    onClick = { sort.value = choice; expanded = false })
            }
        }
    }
}
