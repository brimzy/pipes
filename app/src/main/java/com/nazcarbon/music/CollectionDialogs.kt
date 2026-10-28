package com.nazcarbon.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nazcarbon.music.data.MusicPlaylist
import com.nazcarbon.music.data.MusicTrack

@Composable
internal fun CollectionNameDialog(
    existing: MusicPlaylist? = null,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> String?,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var album by remember { mutableStateOf(existing?.isAlbum ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create a collection" else "Edit collection") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it; error = null }, label = { Text("Name") }, singleLine = true, isError = error != null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!album, { album = false }, label = { Text("Playlist") })
                FilterChip(album, { album = true }, label = { Text("Album") })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = { error = onSave(name, album) }, enabled = name.isNotBlank()) { Text(if (existing == null) "Create" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun CollectionPicker(
    tracks: List<MusicTrack>, collections: List<MusicPlaylist>, onDismiss: () -> Unit,
    onChoose: (String) -> Unit, onCreate: (String, Boolean) -> String?,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        CollectionNameDialog(onDismiss = { creating = false }, onSave = onCreate)
        return
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Add ${tracks.size} ${if (tracks.size == 1) "song" else "songs"}") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text("Choose a playlist or album. Songs already inside are skipped.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            collections.forEach { collection ->
                ListItem(headlineContent = { Text(collection.name) },
                    supportingContent = { Text("${if (collection.isAlbum) "Album" else "Playlist"} · ${collection.tracks.size} songs") },
                    modifier = Modifier.clickable { onChoose(collection.name) })
            }
            TextButton(onClick = { creating = true }) { Text("+ Create playlist or album") }
        } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun DownloadTrackDialog(track: MusicTrack, categories: List<String>, busy: Boolean, error: String?, onDismiss: () -> Unit, onDownload: (String) -> Unit) {
    var category by remember(track.id) { mutableStateOf(categories.firstOrNull() ?: "Unsorted") }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Download song") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium)
            Text(track.artist)
            Text("Save the best available audio for offline playback.")
            Text("Category", style = MaterialTheme.typography.labelLarge)
            categories.forEach { name -> FilterChip(category == name, { if (!busy) category = name }, label = { Text(name) }, enabled = !busy) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = { onDownload(category) }, enabled = !busy) { Text(if (busy) "Preparing…" else "Download") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } })
}
