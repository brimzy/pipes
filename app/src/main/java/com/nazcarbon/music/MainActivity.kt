package com.nazcarbon.music

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.data.MusicPlaylist
import com.nazcarbon.music.data.NewPipeMusicSource
import com.nazcarbon.music.data.PlaylistStore
import com.nazcarbon.music.ui.theme.PipesTheme
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

private val Carbon = Color(0xFF000000)
private val Panel = Color(0xFF101010)
private val Raised = Color(0xFF161616)
private val Cyan = Color(0xFF04D1F9)
private val Ink = Color(0xFFF2F4F8)
private val Muted = Color(0xFF9BA2A8)

private const val RADIO_METADATA_KEY = "pipes_radio"

private fun musicItem(track: MusicTrack, streamUrl: String, radio: Boolean = false) =
    MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(Uri.parse(streamUrl))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setArtworkUri(Uri.parse(track.thumbnailUrl))
                .setExtras(Bundle().apply { putBoolean(RADIO_METADATA_KEY, radio) })
                .build(),
        )
        .build()

class MainActivity : ComponentActivity() {
    private lateinit var source: NewPipeMusicSource
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = NewPipeMusicSource()
        source.initialize()
        controllerFuture = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java)),
        ).buildAsync()
        val prefs = getSharedPreferences("pipes", MODE_PRIVATE)
        var searchJob: Job? = null
        var queueJob: Job? = null
        var playbackRequest = 0
        var completedPlaybackRequest = 0
        setContent {
            PipesTheme {
                var mediaPlayer by remember { mutableStateOf<MediaController?>(null) }
                var page by remember { mutableStateOf("Home") }
                var query by remember { mutableStateOf("") }
                var results by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
                var likedIds by remember { mutableStateOf(prefs.getStringSet("liked", emptySet())?.toSet().orEmpty()) }
                var likedArtists by remember {
                    mutableStateOf(prefs.getStringSet("liked_artists", emptySet())?.map { it.lowercase() }?.toSet().orEmpty())
                }
                val knownTracks = remember { mutableStateMapOf<String, MusicTrack>() }
                var current by remember { mutableStateOf<MusicTrack?>(null) }
                var isPlaying by remember { mutableStateOf(false) }
                var loading by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                var queueTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
                var playlists by remember { mutableStateOf(PlaylistStore.load(prefs)) }
                var selectedPlaylist by remember { mutableStateOf<String?>(null) }
                var radioLoading by remember { mutableStateOf(false) }
                var radioSeed by remember { mutableStateOf<MusicTrack?>(null) }
                var notice by remember { mutableStateOf<String?>(null) }

                DisposableEffect(mediaPlayer) {
                    val listener = object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                        }

                        override fun onPlayerError(playbackError: androidx.media3.common.PlaybackException) {
                            isPlaying = false
                            error = playbackError.message ?: "Playback failed. Try another song or search again."
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) error = null
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            mediaItem ?: return
                            val id = mediaItem.mediaId
                            current = knownTracks[id] ?: MusicTrack(
                                id = id,
                                title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
                                artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
                                durationSeconds = 0,
                                thumbnailUrl = mediaItem.mediaMetadata.artworkUri?.toString().orEmpty(),
                                streamUrl = mediaItem.localConfiguration?.uri?.toString().orEmpty(),
                                pageUrl = id,
                            )
                        }
                    }
                    mediaPlayer?.addListener(listener)
                    onDispose { mediaPlayer?.removeListener(listener) }
                }

                LaunchedEffect(Unit) {
                    runCatching { withContext(Dispatchers.IO) { controllerFuture.get() } }
                        .onSuccess { mediaPlayer = it }
                        .onFailure { error = "The playback service could not start: ${it.message.orEmpty()}" }
                }

                LaunchedEffect(notice) {
                    if (notice != null) {
                        delay(2200)
                        notice = null
                    }
                }

                fun search(text: String) {
                    query = text
                    searchJob?.cancel()
                    if (text.isBlank()) { results = emptyList(); error = null; loading = false; return }
                    loading = true
                    error = null
                    searchJob = lifecycleScope.launch {
                        delay(400)
                        runCatching { withContext(Dispatchers.IO) { source.searchSongs(text) } }
                            .onSuccess { found -> results = found; found.forEach { knownTracks[it.id] = it }; page = "Search" }
                            .onFailure { error = it.message ?: "Search could not load. Try again." }
                        loading = false
                    }
                }

                fun play(track: MusicTrack, keepQueue: Boolean = false, radio: Boolean = false): Int {
                    if (!keepQueue) {
                        queueJob?.cancel()
                        queueTracks = results.dropWhile { it.id != track.id }.ifEmpty { listOf(track) }
                        radioSeed = null
                    }
                    playbackRequest += 1
                    val request = playbackRequest
                    current = track
                    isPlaying = false
                    error = "Loading ${track.title}…"
                    lifecycleScope.launch {
                        runCatching {
                            val resolved = withContext(Dispatchers.IO) { source.resolveAudio(track) }
                            val activePlayer = withContext(Dispatchers.IO) { controllerFuture.get() }
                            val item = musicItem(track, resolved.streamUrl, radio)
                            withContext(Dispatchers.Main) {
                                if (request != playbackRequest) return@withContext
                                activePlayer.setMediaItem(item)
                                activePlayer.prepare()
                                activePlayer.play()
                                completedPlaybackRequest = request
                                error = null
                            }
                        }.onFailure {
                            isPlaying = false
                            error = it.message ?: "Couldn't start this stream. Try another result."
                        }
                    }
                    return request
                }

                fun enqueue(seed: MusicTrack, candidates: List<MusicTrack>, radio: Boolean, request: Int) {
                    queueJob?.cancel()
                    queueTracks = listOf(seed) + candidates
                    queueJob = lifecycleScope.launch {
                        try {
                            val ready = mutableListOf<Pair<MusicTrack, String>>()
                            candidates.take(12).forEach { candidate ->
                                val resolved = runCatching {
                                    withContext(Dispatchers.IO) { source.resolveAudio(candidate) }
                                }.getOrNull()
                                if (resolved != null) ready += candidate to resolved.streamUrl
                            }
                            val activePlayer = withContext(Dispatchers.IO) { controllerFuture.get() }
                            withTimeout(20_000) {
                                while (completedPlaybackRequest != request || activePlayer.currentMediaItem?.mediaId != seed.id) delay(100)
                            }
                            if (request == playbackRequest && activePlayer.currentMediaItem?.mediaId == seed.id && ready.isNotEmpty()) {
                                ready.forEach { (track, _) -> knownTracks[track.id] = track }
                                activePlayer.addMediaItems(ready.map { (track, url) -> musicItem(track, url, radio) })
                                queueTracks = listOf(seed) + ready.map { it.first }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            error = failure.message ?: "Couldn't prepare songs for this queue."
                        } finally {
                            radioLoading = false
                        }
                    }
                }

                fun startRadio(track: MusicTrack) {
                    radioSeed = track
                    radioLoading = true
                    error = null
                    page = "Now playing"
                    queueTracks = listOf(track)
                    val request = play(track, keepQueue = true, radio = true)
                    queueJob?.cancel()
                    queueJob = lifecycleScope.launch {
                        runCatching {
                            val related = withContext(Dispatchers.IO) { source.relatedSongs(track) }
                            val ranked = related
                                .filterNot { it.id == track.id }
                                .distinctBy { it.id }
                                .sortedWith(
                                    compareByDescending<MusicTrack> { candidate ->
                                        (if (candidate.artist.lowercase() in likedArtists) 5 else 0) +
                                            (if (candidate.artist.equals(track.artist, ignoreCase = true)) 1 else 0)
                                    },
                                )
                            enqueue(track, ranked, radio = true, request)
                        }.onFailure {
                            radioLoading = false
                            error = it.message ?: "Couldn't find songs related to this track."
                        }
                    }
                }

                fun playSequence(track: MusicTrack, sequence: List<MusicTrack>, radio: Boolean = false) {
                    val ordered = sequence.dropWhile { it.id != track.id }.ifEmpty { listOf(track) }
                    queueTracks = ordered
                    val request = play(track, keepQueue = true, radio = radio)
                    enqueue(track, ordered.drop(1), radio = radio, request)
                }

                fun saveToCommute(track: MusicTrack) {
                    val name = "Commute Mix"
                    val currentPlaylist = playlists.firstOrNull { it.name == name }
                        ?: MusicPlaylist(name, emptyList())
                    if (currentPlaylist.tracks.any { it.id == track.id }) {
                        notice = "Already in Commute Mix"
                        return
                    }
                    val updated = currentPlaylist.copy(tracks = currentPlaylist.tracks + track.copy(streamUrl = ""))
                    playlists = (playlists.filterNot { it.name == name } + updated)
                    PlaylistStore.save(prefs, playlists)
                    notice = "Added to Commute Mix"
                }

                fun toggleLike(track: MusicTrack) {
                    val removing = track.id in likedIds
                    likedIds = if (removing) likedIds - track.id else likedIds + track.id
                    prefs.edit().putStringSet("liked", likedIds).apply()
                    val artist = track.artist.lowercase()
                    val stillLiked = likedIds.any { knownTracks[it]?.artist?.equals(track.artist, ignoreCase = true) == true }
                    likedArtists = if (removing && !stillLiked) likedArtists - artist else likedArtists + artist
                    prefs.edit().putStringSet("liked_artists", likedArtists).apply()
                }

                fun skipTrack(offset: Int) {
                    val index = queueTracks.indexOfFirst { it.id == current?.id }
                    val next = queueTracks.getOrNull(index + offset) ?: return
                    if (offset > 0 && mediaPlayer?.hasNextMediaItem() == true) mediaPlayer?.seekToNextMediaItem()
                    else if (offset < 0 && mediaPlayer?.hasPreviousMediaItem() == true) mediaPlayer?.seekToPreviousMediaItem()
                    else play(next, keepQueue = true)
                }

                Surface(Modifier.fillMaxSize(), color = Carbon) {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f)) {
                            Header(query, ::search, page)
                            if (page == "Now playing" && current != null) {
                                NowPlaying(
                                    current!!,
                                    isPlaying,
                                    { mediaPlayer?.let { p -> if (p.isPlaying) p.pause() else p.play() } },
                                    { skipTrack(-1) },
                                    { skipTrack(1) },
                                    { startRadio(current!!) },
                                    { page = "Up next" },
                                    radioLoading,
                                    queueTracks.size,
                                )
                            } else when (page) {
                                "Library" -> Library(
                                    likedIds.mapNotNull { knownTracks[it] },
                                    likedIds,
                                    playlists,
                                    current,
                                    isPlaying,
                                    { playlist -> selectedPlaylist = playlist.name; page = "Playlist" },
                                    { track -> playSequence(track, likedIds.mapNotNull { knownTracks[it] }) },
                                    ::toggleLike,
                                    ::saveToCommute,
                                )
                                "Search" -> SearchResults(query, results, loading, error, current, isPlaying, likedIds, { track -> playSequence(track, results) }, ::toggleLike, ::startRadio, ::saveToCommute)
                                "Up next" -> UpNext(queueTracks, current, isPlaying, radioSeed, radioLoading, { track -> playSequence(track, queueTracks.dropWhile { it.id != track.id }, radio = radioSeed != null) }, ::startRadio)
                                "Playlist" -> {
                                    val playlist = playlists.firstOrNull { it.name == selectedPlaylist }
                                    if (playlist != null) PlaylistScreen(
                                        playlist,
                                        current,
                                        isPlaying,
                                        { track -> playSequence(track, playlist.tracks) },
                                        ::startRadio,
                                        ::saveToCommute,
                                    ) else Library(likedIds.mapNotNull { knownTracks[it] }, likedIds, playlists, current, isPlaying, { selectedPlaylist = it.name; page = "Playlist" }, ::play, { }, ::saveToCommute)
                                }
                                "Explore" -> Explore(::search)
                                else -> Home(::search)
                            }
                            if (error != null && page != "Search") Text(error!!, color = Color(0xFFFF9D91), modifier = Modifier.padding(horizontal = 20.dp))
                            if (notice != null) Text(notice!!, color = Cyan, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                        }
                        if (page !in listOf("Now playing", "Up next")) current?.let { MiniPlayer(it, isPlaying, { mediaPlayer?.let { p -> if (p.isPlaying) p.pause() else p.play() } }, { page = "Now playing" }, likedIds.contains(it.id), { skipTrack(1) }) }
                        BottomNav(page) { page = it }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::controllerFuture.isInitialized) MediaController.releaseFuture(controllerFuture)
        super.onDestroy()
    }
}

@Composable private fun Header(query: String, onSearch: (String) -> Unit, page: String) {
    Column(Modifier.fillMaxWidth().background(Carbon).padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("PIPES", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Spacer(Modifier.width(8.dp))
            Text("MUSIC", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(5.dp).clip(CircleShape).background(Cyan))
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            singleLine = true,
            placeholder = { Text("Songs, artists, albums", color = Muted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink, focusedBorderColor = Cyan, unfocusedBorderColor = Raised, cursorColor = Cyan, focusedContainerColor = Panel, unfocusedContainerColor = Panel),
        )
        if (page == "Home") Spacer(Modifier.height(2.dp))
    }
}

@Composable private fun Home(onSearch: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Spacer(Modifier.height(6.dp))
            Text("Your music, in motion.", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Your music. Your mood. Your rules.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(184.dp).clip(RoundedCornerShape(18.dp)).background(Raised).clickable { onSearch("indie pop") }) {
                Column(Modifier.align(Alignment.CenterStart).padding(20.dp)) {
                    Text("PIPES RADIO · 001", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Your soft\nlanding.", color = Ink, fontSize = 29.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Dreamy indie for wherever you're headed.", color = Muted, fontSize = 11.sp)
                }
                Box(Modifier.align(Alignment.CenterEnd).size(150.dp).padding(15.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF101010)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = Cyan, modifier = Modifier.size(70.dp))
                }
            }
        }
        item { Text("Find your frequency", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        items(listOf("indie pop", "late night r&b", "dream pop", "ambient focus", "new music")) { mood ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable { onSearch(mood) }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Raised), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Cyan) }
                Spacer(Modifier.width(13.dp)); Text(mood.replaceFirstChar { it.uppercase() }, color = Ink, fontWeight = FontWeight.Medium); Spacer(Modifier.weight(1f)); Text("↗", color = Muted)
            }
        }
    }
}

@Composable private fun SearchResults(query: String, tracks: List<MusicTrack>, loading: Boolean, error: String?, current: MusicTrack?, playing: Boolean, liked: Set<String>, onPlay: (MusicTrack) -> Unit, onLike: (MusicTrack) -> Unit, onRadio: (MusicTrack) -> Unit, onSave: (MusicTrack) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(if (query.isBlank()) "Find a sound" else "Search results", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        when {
            loading -> CircularProgressIndicator(color = Cyan, modifier = Modifier.align(Alignment.CenterHorizontally).padding(35.dp))
            error != null -> Text(error, color = Color(0xFFFF9D91), modifier = Modifier.padding(20.dp))
            tracks.isEmpty() -> Text("Search YouTube Music songs, artists, or albums.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(tracks, key = { it.id }) { track -> TrackRow(track, current?.id == track.id && playing, liked.contains(track.id), onPlay, onLike, onRadio, onSave) }
            }
        }
    }
}

@Composable private fun TrackRow(track: MusicTrack, active: Boolean, liked: Boolean, onPlay: (MusicTrack) -> Unit, onLike: (MusicTrack) -> Unit, onRadio: (MusicTrack) -> Unit, onSave: (MusicTrack) -> Unit) {
    var menuOpen by remember(track.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { onPlay(track) }.padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = track.thumbnailUrl, contentDescription = null, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(9.dp)).background(Raised), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = if (active) Cyan else Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp)); Text(track.artist, color = Muted, maxLines = 1, fontSize = 12.sp)
        }
        IconButton(onClick = { onLike(track) }) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (liked) Cyan else Muted) }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More options", tint = Muted) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Start radio from song") },
                    leadingIcon = { Icon(Icons.Default.Radio, null) },
                    onClick = { menuOpen = false; onRadio(track) },
                )
                DropdownMenuItem(
                    text = { Text("Add to Commute Mix") },
                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) },
                    onClick = { menuOpen = false; onSave(track) },
                )
            }
        }
        Icon(if (active) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Ink, modifier = Modifier.size(21.dp))
    }
}

@Composable private fun Library(tracks: List<MusicTrack>, likedIds: Set<String>, playlists: List<MusicPlaylist>, current: MusicTrack?, playing: Boolean, onOpenPlaylist: (MusicPlaylist) -> Unit, onPlay: (MusicTrack) -> Unit, onLike: (MusicTrack) -> Unit, onSave: (MusicTrack) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Your library", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        Text("A collection built around you.", color = Muted, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp)) {
            item {
                Text("PLAYLISTS", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp))
            }
            if (playlists.isEmpty()) item {
                Text("Save a song to create your Commute Mix.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            } else items(playlists, key = { "playlist:${it.name}" }) { playlist ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenPlaylist(playlist) }.padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(9.dp)).background(Raised), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.QueueMusic, null, tint = Cyan)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("${playlist.tracks.size} songs", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.PlayArrow, null, tint = Muted)
                }
            }
            item {
                Text("LIKED SONGS", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp))
            }
            if (tracks.isEmpty()) item {
                Text("Tap ♡ on a song to save it here.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            } else items(tracks, key = { "liked:${it.id}" }) {
                TrackRow(it, current?.id == it.id && playing, likedIds.contains(it.id), onPlay, onLike, {}, onSave)
            }
        }
    }
}

@Composable private fun PlaylistScreen(
    playlist: MusicPlaylist,
    current: MusicTrack?,
    playing: Boolean,
    onPlay: (MusicTrack) -> Unit,
    onRadio: (MusicTrack) -> Unit,
    onSave: (MusicTrack) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(playlist.name, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        Text("${playlist.tracks.size} songs · saved on this device", color = Muted, modifier = Modifier.padding(horizontal = 20.dp))
        if (playlist.tracks.isEmpty()) {
            Text("Use a song's menu to add it to this playlist.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(20.dp))
        } else LazyColumn {
            items(playlist.tracks, key = { it.id }) {
                TrackRow(it, current?.id == it.id && playing, false, onPlay, {}, onRadio, onSave)
            }
        }
    }
}

@Composable private fun UpNext(
    tracks: List<MusicTrack>,
    current: MusicTrack?,
    playing: Boolean,
    radioSeed: MusicTrack?,
    radioLoading: Boolean,
    onPlay: (MusicTrack) -> Unit,
    onRadio: (MusicTrack) -> Unit,
) {
    val following = tracks.dropWhile { it.id != current?.id }.drop(1)
    Column(Modifier.fillMaxSize()) {
        Text("Up next", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        Text(
            when {
                radioLoading -> "Finding songs related to ${radioSeed?.title.orEmpty()}…"
                radioSeed != null -> "Radio from ${radioSeed.title}"
                else -> "Your play queue"
            },
            color = Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (following.isEmpty()) {
            if (radioLoading) CircularProgressIndicator(color = Cyan, modifier = Modifier.padding(24.dp))
            else Text("Start a radio station from any song to fill the queue.", color = Muted, modifier = Modifier.padding(20.dp))
        } else LazyColumn {
            items(following, key = { it.id }) { track ->
                TrackRow(track, current?.id == track.id && playing, false, onPlay, {}, onRadio, {})
            }
        }
    }
}

@Composable private fun Explore(onSearch: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Explore", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Go where the music takes you.", color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 20.dp))
        listOf("Songs", "Artists", "Albums", "Playlists", "New & trending").forEachIndexed { index, label ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(Raised).clickable { onSearch(if (index == 4) "new music" else "") }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(listOf("♫", "◉", "▣", "☷", "✳")[index], color = Cyan, fontSize = 20.sp); Spacer(Modifier.width(14.dp)); Text(label, color = Ink, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text("→", color = Muted)
            }
        }
    }
}

@Composable private fun MiniPlayer(track: MusicTrack, playing: Boolean, toggle: () -> Unit, expand: () -> Unit, liked: Boolean, next: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(13.dp)).background(Raised).clickable(onClick = expand).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = track.thumbnailUrl, contentDescription = null, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(track.title, color = Ink, maxLines = 1, fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text(track.artist, color = Muted, fontSize = 10.sp, maxLines = 1) }
        Icon(if (liked) Icons.Default.Favorite else Icons.Default.MusicNote, null, tint = if (liked) Cyan else Muted, modifier = Modifier.padding(horizontal = 10.dp).size(17.dp))
        IconButton(onClick = toggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Ink) }
        IconButton(onClick = next) { Icon(Icons.Default.SkipNext, null, tint = Ink) }
    }
}

@Composable private fun NowPlaying(
    track: MusicTrack,
    playing: Boolean,
    toggle: () -> Unit,
    previous: () -> Unit,
    next: () -> Unit,
    onRadio: () -> Unit,
    onQueue: () -> Unit,
    radioLoading: Boolean,
    queueCount: Int,
) {
    Column(Modifier.fillMaxSize().background(Carbon).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AsyncImage(model = track.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Raised), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(26.dp)); Text(track.title, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(track.artist, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(24.dp)); Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = previous) { Icon(Icons.Default.SkipPrevious, null, tint = Muted, modifier = Modifier.size(34.dp)) }; Spacer(Modifier.width(18.dp)); FilledIconButton(onClick = toggle, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Cyan, contentColor = Carbon), modifier = Modifier.size(60.dp)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(30.dp)) }; Spacer(Modifier.width(18.dp)); IconButton(onClick = next) { Icon(Icons.Default.SkipNext, null, tint = Muted, modifier = Modifier.size(34.dp)) } }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onRadio, enabled = !radioLoading) {
                Icon(Icons.Default.Radio, null, tint = Cyan)
                Spacer(Modifier.width(8.dp))
                Text(if (radioLoading) "Building radio…" else "Start radio")
            }
            OutlinedButton(onClick = onQueue) {
                Icon(Icons.Default.QueueMusic, null, tint = Cyan)
                Spacer(Modifier.width(8.dp))
                Text("Up next · $queueCount")
            }
        }
    }
}

@Composable private fun BottomNav(page: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Panel, contentColor = Ink) {
        listOf(Triple("Home", Icons.Default.Home, "Home"), Triple("Explore", Icons.Default.Explore, "Explore"), Triple("Library", Icons.Default.LibraryMusic, "Library")).forEach { (label, icon, target) ->
            NavigationBarItem(selected = page == target, onClick = { onSelect(target) }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = Raised, unselectedIconColor = Muted, unselectedTextColor = Muted))
        }
    }
}
