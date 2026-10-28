package com.nazcarbon.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazcarbon.music.data.ExploreFeed
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.ui.theme.PipesPalette as P
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ExploreScene(val title: String, val subtitle: String, val query: String)
private val scenes = listOf(
    ExploreScene("After hours", "R&B, slow grooves", "alternative r&b late night"),
    ExploreScene("Focus", "Ambient, without words", "instrumental ambient focus"),
    ExploreScene("In motion", "Electronic, more energy", "electronic dance music"),
    ExploreScene("New music", "A different starting point", "new music ${java.time.Year.now().value}"),
)
private val genres = listOf("Indie", "Jazz", "Soul", "Hip-hop", "Alternative", "Electronic", "Classical", "Rock")

@Composable
internal fun ExploreScreen(feed: ExploreFeed, current: MusicTrack?, selection: String,
    onSelect: (String) -> Unit, list: LazyListState, onSearch: (String) -> Unit,
    onPlay: (MusicTrack) -> Unit, onLike: (MusicTrack) -> Unit,
    onRadio: (MusicTrack) -> Unit, onSave: (MusicTrack) -> Unit, onTracksLoaded: (List<MusicTrack>) -> Unit = {}) {
    val latestTracksLoaded by rememberUpdatedState(onTracksLoaded)
    val choice = scenes.firstOrNull { it.title == selection }
        ?: if (selection == "For you" && current != null) ExploreScene("Beyond ${current.artist}", "A familiar starting point", "${current.artist} music")
        else ExploreScene(selection, "Explore a sound", "$selection music")
    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var loadedQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var refreshTarget by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    fun reload() { refreshTarget = choice.query; refreshToken++ }

    LaunchedEffect(choice.query, refreshToken) {
        val request = ++generation
        val force = refreshTarget == choice.query
        refreshTarget = null
        if (loadedQuery != choice.query) { tracks = emptyList(); latestTracksLoaded(emptyList()) }
        loading = true
        error = null
        try {
            val result = withContext(Dispatchers.IO) { feed.load(choice.query, refresh = force) }
            tracks = result
            latestTracksLoaded(result)
            loadedQuery = choice.query
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Couldn't load this sound. Try again." }
        finally { if (request == generation) loading = false }
    }

    LazyColumn(state = list, contentPadding = PaddingValues(bottom = PipesSpace.Page)) {
        item(key = "explore-title") {
            Column(Modifier.padding(PipesSpace.Page)) {
                Text("Explore.", color = P.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Follow a mood. Find a new sound.", color = P.TextSoft, fontSize = 13.sp,
                    modifier = Modifier.padding(top = PipesSpace.ArtworkText))
            }
        }
        items(scenes.chunked(2), key = { "scenes:${it.first().title}" }) { pair ->
            Row(Modifier.padding(horizontal = PipesSpace.Page).padding(bottom = PipesSpace.Gap),
                horizontalArrangement = Arrangement.spacedBy(PipesSpace.Gap)) {
                pair.forEach { scene ->
                    val active = selection == scene.title
                    Column(Modifier.weight(1f).heightIn(min = 104.dp)
                        .clip(RoundedCornerShape(12.dp)).background(if (active) P.Card else P.Dock)
                        .clickable { onSelect(scene.title) }.semantics { selected = active }
                        .padding(PipesSpace.Gap), verticalArrangement = Arrangement.Center) {
                        Text(scene.title, color = if (active) P.Cyan else P.Text,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(scene.subtitle, color = P.TextSoft, fontSize = 11.sp,
                            modifier = Modifier.padding(top = PipesSpace.ArtworkText))
                    }
                }
            }
        }
        item(key = "genre-title") {
            Text("Browse by sound", color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = PipesSpace.Page).padding(top = PipesSpace.Gap))
        }
        item(key = "genres") {
            LazyRow(contentPadding = PaddingValues(horizontal = PipesSpace.Page, vertical = PipesSpace.ArtworkText),
                horizontalArrangement = Arrangement.spacedBy(PipesSpace.ArtworkText)) {
                items(genres, key = { it }) { genre ->
                    TextButton(onClick = { onSelect(genre) }, shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = 48.dp).semantics { selected = selection == genre },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (selection == genre) P.Card else P.Dock,
                            contentColor = if (selection == genre) P.Cyan else P.Text)) { Text(genre) }
                }
            }
        }
        current?.let { track -> item(key = "personal-direction") {
            TextButton(onClick = { onSelect("For you") }, modifier = Modifier.padding(horizontal = PipesSpace.Gap)) {
                Text("Go beyond ${track.artist} ↗", color = if (selection == "For you") P.Cyan else P.TextSoft,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } }
        item(key = "results-title") {
            Row(Modifier.fillMaxWidth().padding(horizontal = PipesSpace.Page)
                .padding(top = PipesSpace.Gap, bottom = PipesSpace.Gap), verticalAlignment = Alignment.CenterVertically) {
                Text(choice.title, color = P.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = ::reload, enabled = !loading) { Text("Refresh") }
            }
        }
        if (loading && tracks.isEmpty()) item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(PipesSpace.Section), contentAlignment = Alignment.Center) { PipesLoading() }
        }
        if (error != null) item(key = "error") {
            Column(Modifier.padding(horizontal = PipesSpace.Page)) {
                Text(error!!, color = P.TextSoft)
                TextButton(onClick = ::reload, enabled = !loading) { Text("Try again") }
            }
        }
        if (!loading && error == null && tracks.isEmpty()) item(key = "empty") {
            Column(Modifier.padding(horizontal = PipesSpace.Page)) {
                Text("No recordings here yet. Try another mood or search this sound.", color = P.TextSoft)
                TextButton(onClick = { onSearch(choice.query) }) { Text("Search this sound ↗") }
            }
        }
        items(tracks.chunked(2), key = { "explore-pair:${it.first().id}" }) { pair ->
            Row(Modifier.padding(horizontal = PipesSpace.Page).padding(bottom = PipesSpace.Page),
                horizontalArrangement = Arrangement.spacedBy(PipesSpace.Gap)) {
                pair.forEach { track -> Box(Modifier.weight(1f)) { MusicArtworkCard(track, onPlay, onLike, onRadio, onSave) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (tracks.isNotEmpty()) item(key = "more") {
            TextButton(onClick = { onSearch(choice.query) }, modifier = Modifier.fillMaxWidth().padding(horizontal = PipesSpace.Page)) {
                Text("More ${if (selection == "For you") "like this" else choice.title.lowercase()} ↗")
            }
        }
    }
}
