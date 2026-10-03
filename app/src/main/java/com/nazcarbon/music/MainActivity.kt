package com.nazcarbon.music

import android.content.ComponentName
import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FolderOpen
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Close
import androidx.media3.session.SessionCommand
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
import com.nazcarbon.music.data.SongSearch
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

private val Carbon = Color(0xFF090B0F)
private val Panel = Color(0xFF11161D)
private val Raised = Color(0xFF1B222C)
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
                .setExtras(Bundle().apply { putBoolean(RADIO_METADATA_KEY, radio); putString("audio_quality", track.audioQuality); if (radio) putString("pipes_radio_session", java.util.UUID.randomUUID().toString()) })
                .build(),
        )
        .build()

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var source: NewPipeMusicSource
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        source = NewPipeMusicSource()
        source.initialize()
        controllerFuture = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java)),
        ).buildAsync()
        val prefs = getSharedPreferences("pipes", MODE_PRIVATE)
        var playJob: Job? = null
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
                var searchError by remember { mutableStateOf<String?>(null) }
                var error by remember { mutableStateOf<String?>(null) }
                var queueTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
                var playlists by remember { mutableStateOf(PlaylistStore.load(prefs)) }
                var selectedPlaylist by remember { mutableStateOf<String?>(null) }
                var radioLoading by remember { mutableStateOf(false) }
                var radioSeed by remember { mutableStateOf<MusicTrack?>(null) }
                var notice by remember { mutableStateOf<String?>(null) }

                var position by remember { mutableLongStateOf(0L) }
                var duration by remember { mutableLongStateOf(0L) }
                var buffering by remember { mutableStateOf(false) }
                var shuffle by remember { mutableStateOf(false) }
                var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
                var seekable by remember { mutableStateOf(false) }
                var audioQuality by remember { mutableStateOf("") }

                LaunchedEffect(mediaPlayer) {
                    val player = mediaPlayer ?: return@LaunchedEffect
                    while (true) {
                        position = player.currentPosition.coerceAtLeast(0)
                        duration = player.duration.coerceAtLeast(0)
                        seekable = player.isCurrentMediaItemSeekable
                        radioLoading = player.sessionExtras.getBoolean("radio_loading")
                        buffering = player.playbackState == Player.STATE_BUFFERING
                        shuffle = player.shuffleModeEnabled
                        repeat = player.repeatMode
                        isPlaying = player.isPlaying
                        val item = player.currentMediaItem
                        if (item != null) {
                            audioQuality = item.mediaMetadata.extras?.getString("audio_quality").orEmpty()
                            current = knownTracks[item.mediaId] ?: MusicTrack(item.mediaId,
                                item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty(),
                                0, item.mediaMetadata.artworkUri?.toString().orEmpty(), "", item.mediaId)
                        }
                        queueTracks = (0 until player.mediaItemCount).map { index ->
                            val queued = player.getMediaItemAt(index)
                            knownTracks[queued.mediaId] ?: MusicTrack(queued.mediaId,
                                queued.mediaMetadata.title?.toString().orEmpty(), queued.mediaMetadata.artist?.toString().orEmpty(),
                                0, queued.mediaMetadata.artworkUri?.toString().orEmpty(), "", queued.mediaId)
                        }
                        delay(300)
                    }
                }
                BackHandler(page == "Now playing" || page == "Up next") {
                    page = if (page == "Up next") "Now playing" else "Home"
                }

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

                val searchScope = rememberCoroutineScope()
                val songSearch = remember {
                    SongSearch(searchScope, { text -> withContext(Dispatchers.IO) { source.searchSongs(text) } }) { state ->
                        results = state.tracks
                        loading = state.loading
                        searchError = state.error
                        state.tracks.forEach { knownTracks[it.id] = it }
                    }
                }
                fun search(text: String) {
                    query = text
                    page = "Search"
                    songSearch.submit(text)
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
                    error = null
                    buffering = true
                    playJob?.cancel()
                    playJob = lifecycleScope.launch {
                        runCatching {
                            val resolved = if (track.pageUrl.startsWith("content://")) track.copy(streamUrl = track.pageUrl) else withContext(Dispatchers.IO) { source.resolveAudio(track) }
                            val activePlayer = withContext(Dispatchers.IO) { controllerFuture.get() }
                            val item = musicItem(resolved, resolved.streamUrl, radio)
                            withContext(Dispatchers.Main) {
                                if (request != playbackRequest) return@withContext
                                knownTracks[track.id] = resolved
                                activePlayer.repeatMode = Player.REPEAT_MODE_OFF
                                activePlayer.setMediaItem(item)
                                activePlayer.prepare()
                                activePlayer.play()
                                completedPlaybackRequest = request
                                error = null
                            }
                        }.onFailure {
                            if (it is CancellationException) throw it
                            if (request != playbackRequest) return@onFailure
                            isPlaying = false
                            error = it.message ?: "Couldn't start this stream. Try another result."
                        }
                    }
                    return request
                }

                fun enqueue(seed: MusicTrack, candidates: List<MusicTrack>, radio: Boolean, request: Int) {
                    queueJob?.cancel()
                    queueJob = lifecycleScope.launch {
                        try {
                            val activePlayer = withContext(Dispatchers.IO) { controllerFuture.get() }
                            withTimeout(30_000) {
                                while (completedPlaybackRequest != request) delay(100)
                            }
                            for (candidate in candidates.distinctBy { it.id }.filterNot { it.id == seed.id }.take(12)) {
                                val resolved = try { if (candidate.pageUrl.startsWith("content://")) candidate.copy(streamUrl = candidate.pageUrl) else withContext(Dispatchers.IO) { source.resolveAudio(candidate) } }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { continue }
                                if (request != playbackRequest) return@launch
                                knownTracks[candidate.id] = resolved
                                activePlayer.addMediaItem(musicItem(resolved, resolved.streamUrl, radio))
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { if (request == playbackRequest) error = "Couldn't prepare the queue. Try again." }
                    }
                }

                fun startRadio(track: MusicTrack) {
                    if (track.pageUrl.startsWith("content://")) { notice = "Radio is available for online songs."; return }
                    queueJob?.cancel()
                    radioSeed = track
                    error = null
                    page = "Now playing"
                    playJob?.cancel()
                    playbackRequest += 1
                    val activePlayer = mediaPlayer
                    if (activePlayer?.currentMediaItem?.mediaId == track.id) {
                        // The service enables radio without resetting this item's position.
                        activePlayer.sendCustomCommand(SessionCommand("pipes.start_radio", Bundle.EMPTY), Bundle.EMPTY)
                    } else {
                        play(track, keepQueue = true, radio = true)
                    }
                    notice = "Radio started · songs will appear in Up next"
                }

                fun playSequence(track: MusicTrack, sequence: List<MusicTrack>, radio: Boolean = false) {
                    if (!radio) radioSeed = null
                    val ordered = sequence.distinctBy { it.id }.dropWhile { it.id != track.id }.ifEmpty { listOf(track) }
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
                    mediaPlayer?.let { player ->
                        if (offset < 0) player.seekToPrevious()
                        else if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                    }
                }

                val openAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        val name = runCatching {
                            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            }
                        }.getOrNull() ?: "Local audio"
                        val track = MusicTrack(uri.toString(), name, "On this device", 0, "", uri.toString(), uri.toString(), "Original local audio")
                        knownTracks[track.id] = track
                        play(track)
                        page = "Now playing"
                    }
                }

                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(error, notice) {
                    (error ?: notice)?.let { snackbar.showSnackbar(it) }
                }
                Surface(Modifier.fillMaxSize(), color = Carbon) {
                    Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                        Column(Modifier.weight(1f)) {
                            if (page != "Now playing") Header(query, ::search, page) { openAudio.launch(arrayOf("audio/*")) }
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
                                    position, duration, seekable, buffering, audioQuality,
                                    { mediaPlayer?.seekTo(it) },
                                    shuffle, { mediaPlayer?.shuffleModeEnabled = !shuffle },
                                    repeat, { mediaPlayer?.repeatMode = (repeat + 1) % 3 },
                                    current!!.id in likedIds, { toggleLike(current!!) },
                                    { page = "Home" }, { saveToCommute(current!!) },
                                    mediaPlayer?.hasNextMediaItem() == true,
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
                                "Search" -> SearchResults(query, results, loading, searchError, current, isPlaying, likedIds, { track -> playSequence(track, results) }, ::toggleLike, ::startRadio, ::saveToCommute, { search(query) })
                                "Up next" -> UpNext(queueTracks, current, isPlaying, radioSeed, radioLoading, { track ->
                                    mediaPlayer?.let { player ->
                                        val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == track.id }
                                        if (index != null) { player.seekTo(index, 0L); player.play() }
                                    }
                                }, ::startRadio)
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
                        }
                        if (page !in listOf("Now playing", "Up next")) current?.let { MiniPlayer(it, isPlaying, { mediaPlayer?.let { p -> if (p.isPlaying) p.pause() else p.play() } }, { page = "Now playing" }, likedIds.contains(it.id), { skipTrack(1) }, position, duration) }
                        if (page != "Now playing") BottomNav(page) { page = it }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (page == "Now playing") 8.dp else 84.dp))
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

@Composable private fun Header(query: String, onSearch: (String) -> Unit, page: String, onOpenAudio: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth().background(Carbon).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("PIPES", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Spacer(Modifier.width(8.dp))
            Text("MUSIC", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenAudio) { Icon(Icons.Default.FolderOpen, "Open local audio, including lossless FLAC and WAV", tint = Muted) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch(query) }),
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Close, "Clear search") } },
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
            Box(Modifier.fillMaxWidth().heightIn(min = 184.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF12353C), Panel))).clickable { onSearch("indie pop") }) {
                Column(Modifier.padding(24.dp).padding(end = 40.dp)) {
                    Text("PIPES RADIO · 001", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Your soft\nlanding.", color = Ink, fontSize = 29.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Dreamy indie for wherever you're headed.", color = Muted, fontSize = 11.sp)
                }
                Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = Cyan, modifier = Modifier.size(30.dp))
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

@Composable private fun SearchResults(query: String, tracks: List<MusicTrack>, loading: Boolean, error: String?, current: MusicTrack?, playing: Boolean, liked: Set<String>, onPlay: (MusicTrack) -> Unit, onLike: (MusicTrack) -> Unit, onRadio: (MusicTrack) -> Unit, onSave: (MusicTrack) -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(if (query.isBlank()) "Find a sound" else "Search results", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        when {
            loading -> CircularProgressIndicator(color = Cyan, modifier = Modifier.align(Alignment.CenterHorizontally).padding(35.dp))
            error != null -> Column(Modifier.padding(20.dp)) {
                Text(error, color = Muted)
                TextButton(onClick = onRetry) { Text("Retry search") }
            }
            tracks.isEmpty() -> Text(if (query.isBlank()) "Search for your favorite songs and artists." else "No songs found. Try another title or artist.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
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
        IconButton(onClick = { onLike(track) }) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (liked) "Unlike song" else "Like song", tint = if (liked) Cyan else Muted) }
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
            else Text("No songs queued yet. Start radio or retry it from the player.", color = Muted, modifier = Modifier.padding(20.dp))
        } else LazyColumn {
            items(following, key = { it.id }) { track ->
                TrackRow(track, current?.id == track.id && playing, false, onPlay, {}, onRadio, {})
            }
        }
    }
}

@Composable private fun Explore(onSearch: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Explore", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Go where the music takes you.", color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 20.dp))
        listOf("Indie essentials", "R&B after dark", "Electronic", "Quiet focus", "New & trending").forEachIndexed { index, label ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(Raised).clickable { onSearch(listOf("indie pop", "r&b", "electronic music", "ambient focus", "new music")[index]) }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(listOf("♫", "◉", "▣", "☷", "✳")[index], color = Cyan, fontSize = 20.sp); Spacer(Modifier.width(14.dp)); Text(label, color = Ink, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text("→", color = Muted)
            }
        }
    }
}

@Composable private fun MiniPlayer(track: MusicTrack, playing: Boolean, toggle: () -> Unit, expand: () -> Unit, liked: Boolean, next: () -> Unit, position: Long, duration: Long) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(Raised).clickable(onClick = expand)) {
        AsyncImage(track.thumbnailUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = 0.15f)
        Column {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(track.thumbnailUrl, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Panel), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(track.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (liked) Icon(Icons.Default.Favorite, "Liked", tint = Cyan, modifier = Modifier.size(16.dp))
                IconButton(onClick = toggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", tint = Ink) }
                IconButton(onClick = next) { Icon(Icons.Default.SkipNext, "Next song", tint = Ink) }
            }
            LinearProgressIndicator(progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp), color = Cyan, trackColor = Ink.copy(alpha = 0.08f))
        }
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
    position: Long,
    duration: Long,
    seekable: Boolean,
    buffering: Boolean,
    audioQuality: String,
    onSeek: (Long) -> Unit,
    shuffle: Boolean,
    onShuffle: () -> Unit,
    repeat: Int,
    onRepeat: () -> Unit,
    liked: Boolean,
    onLike: () -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    hasNext: Boolean,
) {
    var dragPosition by remember(track.id) { mutableStateOf<Float?>(null) }
    val progress = dragPosition ?: position.toFloat()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 700.dp
        val artworkSize = minOf(maxWidth - 64.dp, (maxHeight - 400.dp).coerceAtLeast(96.dp), 360.dp)
        AsyncImage(track.thumbnailUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = 0.28f)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Carbon.copy(alpha = 0.35f), Carbon.copy(alpha = 0.80f), Carbon))))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close player", tint = Ink) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Your music, in motion", color = Muted, fontSize = 11.sp)
                }
                IconButton(onClick = onSave) { Icon(Icons.Default.PlaylistAdd, "Save to Commute Mix", tint = Ink) }
            }
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            AsyncImage(track.thumbnailUrl, "Artwork for ${track.title}",
                Modifier.size(artworkSize).clip(RoundedCornerShape(24.dp)).background(Raised), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Ink, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
                IconButton(onClick = onLike) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (liked) "Unlike song" else "Like song", tint = if (liked) Cyan else Ink) }
            }
            Spacer(Modifier.height(10.dp))
            Text(if (audioQuality.isBlank()) "Best available audio" else audioQuality,
                color = Cyan, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
            Slider(
                value = progress.coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                onValueChange = { dragPosition = it },
                onValueChangeFinished = { dragPosition?.let { onSeek(it.toLong()) }; dragPosition = null },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                enabled = seekable && duration > 0,
                colors = SliderDefaults.colors(thumbColor = Ink, activeTrackColor = Cyan, inactiveTrackColor = Ink.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(playbackTime(progress.toLong()), color = Muted, fontSize = 12.sp)
                Text(if (duration > 0) playbackTime(duration) else "--:--", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, if (shuffle) "Disable shuffle" else "Enable shuffle", tint = if (shuffle) Cyan else Muted) }
                IconButton(onClick = previous) { Icon(Icons.Default.SkipPrevious, "Previous song", tint = Ink, modifier = Modifier.size(32.dp)) }
                FilledIconButton(onClick = toggle, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Ink, contentColor = Carbon), modifier = Modifier.size(68.dp)) {
                    if (buffering) CircularProgressIndicator(Modifier.size(28.dp), color = Carbon, strokeWidth = 2.dp)
                    else Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = next, enabled = hasNext) { Icon(Icons.Default.SkipNext, "Next song", tint = if (hasNext) Ink else Muted.copy(alpha = 0.35f), modifier = Modifier.size(32.dp)) }
                IconButton(onClick = onRepeat) { Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat: ${when (repeat) { Player.REPEAT_MODE_ONE -> "one song"; Player.REPEAT_MODE_ALL -> "all songs"; else -> "off" }}",
                    tint = if (repeat != Player.REPEAT_MODE_OFF) Cyan else Muted) }
            }
            Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink.copy(alpha = 0.06f)), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onRadio, enabled = !radioLoading && !track.pageUrl.startsWith("content://")) {
                    Icon(Icons.Default.Radio, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Radio")
                }
                TextButton(onClick = onQueue) {
                    Icon(Icons.Default.QueueMusic, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Queue · $queueCount")
                }
            }
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        }
    }
}

private fun playbackTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable private fun BottomNav(page: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Carbon, contentColor = Ink, windowInsets = WindowInsets(0, 0, 0, 0)) {
        listOf(Triple("Home", Icons.Default.Home, "Home"), Triple("Explore", Icons.Default.Explore, "Explore"), Triple("Library", Icons.Default.LibraryMusic, "Library")).forEach { (label, icon, target) ->
            NavigationBarItem(selected = page == target, onClick = { onSelect(target) }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = Raised, unselectedIconColor = Muted, unselectedTextColor = Muted))
        }
    }
}
