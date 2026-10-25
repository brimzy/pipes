package com.nazcarbon.music

import android.content.ComponentName
import android.content.Intent
import android.app.DownloadManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FolderOpen
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Delete
import androidx.media3.session.SessionCommand
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Explore
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nazcarbon.music.data.SongSearch
import com.nazcarbon.music.data.SpotifyLinks
import com.nazcarbon.music.data.SpotifyPlaylists
import com.nazcarbon.music.data.AudioDownload
import com.nazcarbon.music.data.AudioDownloadStatus
import com.nazcarbon.music.data.AudioDownloads
import com.nazcarbon.music.data.ArtistDiscography
import com.nazcarbon.music.data.ArtistRelease
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.data.MusicArtist
import com.nazcarbon.music.data.MusicPlaylist
import com.nazcarbon.music.data.NewPipeMusicSource
import com.nazcarbon.music.data.PlaylistStore
import com.nazcarbon.music.data.PlaybackQueueRequests
import com.nazcarbon.music.ui.theme.PipesTheme
import com.nazcarbon.music.ui.theme.PipesPalette
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

private val Carbon = PipesPalette.Background
private val Panel = PipesPalette.Card
private val Raised = PipesPalette.Raised
private val Accent = PipesPalette.Accent
private val Ink = PipesPalette.Text
private val Muted = PipesPalette.TextSoft

internal val LocalRemoveFromQueue = staticCompositionLocalOf<(MusicTrack) -> Unit> { {} }

private val LocalAddToQueue = staticCompositionLocalOf<(MusicTrack) -> Unit> { {} }

private const val RADIO_METADATA_KEY = "pipes_radio"
private const val AUTO_QUEUE_METADATA_KEY = "pipes_auto_queue"
private const val AUTO_QUEUE_SESSION_KEY = "pipes_auto_queue_session"

private fun musicItem(track: MusicTrack, streamUrl: String, radio: Boolean = false, autoplay: Boolean = true) =
    MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(Uri.parse(streamUrl))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setArtworkUri(Uri.parse(track.thumbnailUrl))
                .setExtras(Bundle().apply {
                    val queueSession = java.util.UUID.randomUUID().toString()
                    val canAutoplay = autoplay && !track.pageUrl.startsWith("content://")
                    putBoolean(RADIO_METADATA_KEY, radio)
                    putBoolean(AUTO_QUEUE_METADATA_KEY, canAutoplay)
                    putString(AUTO_QUEUE_SESSION_KEY, queueSession)
                    putString("pipes_page_url", track.pageUrl)
                    putLong("pipes_duration", track.durationSeconds)
                    if (radio) putString("pipes_radio_session", queueSession)
                    putString("audio_quality", track.audioQuality)
                })
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
        var importJob: Job? = null
        var importGeneration = 0
        var playbackRequest = 0
        var completedPlaybackRequest = 0
        setContent {
            PipesTheme {
                val updates = rememberAppUpdates(this@MainActivity)
                AppUpdateDialog(this@MainActivity, updates)
                var mediaPlayer by remember { mutableStateOf<MediaController?>(null) }
                val discoveryFeed = remember { com.nazcarbon.music.data.DiscoveryFeed(source) }
                val discoveryScroll = androidx.compose.foundation.lazy.rememberLazyListState()
                var page by remember { mutableStateOf("Home") }
                var query by remember { mutableStateOf("") }
                var importProgress by remember { mutableStateOf<String?>(null) }
                var results by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
                var likedIds by remember { mutableStateOf(prefs.getStringSet("liked", emptySet())?.toSet().orEmpty()) }
                var likedArtists by remember {
                    mutableStateOf(prefs.getStringSet("liked_artists", emptySet())?.map { it.lowercase() }?.toSet().orEmpty())
                }
                val knownTracks = remember {
                    mutableStateMapOf<String, MusicTrack>().apply {
                        (PlaylistStore.load(prefs) + PlaylistStore.load(prefs, "saved_tracks"))
                            .flatMap { it.tracks }.forEach { put(it.id, it) }
                        likedIds.filterNot { containsKey(it) }.forEach { id ->
                            put(id, MusicTrack(id, "Saved song", "Saved in an earlier version", 0, "", "", id))
                        }
                    }
                }
                var searchHistory by remember { mutableStateOf(com.nazcarbon.music.data.SearchHistory.decode(prefs.getString("search_history", "[]").orEmpty())) }
                fun saveHistory(history: List<String>) {
                    searchHistory = history
                    prefs.edit().putString("search_history", com.nazcarbon.music.data.SearchHistory.encode(history)).apply()
                }
                LaunchedEffect(knownTracks.toMap(), likedIds) {
                    PlaylistStore.save(prefs, listOf(MusicPlaylist("Liked songs", likedIds.mapNotNull { knownTracks[it] })), "saved_tracks")
                }
                var current by remember { mutableStateOf<MusicTrack?>(null) }
                var isPlaying by remember { mutableStateOf(false) }
                var loading by remember { mutableStateOf(false) }
                var searchError by remember { mutableStateOf<String?>(null) }
                var error by remember { mutableStateOf<String?>(null) }
                var queueIndex by remember { mutableStateOf(0) }
                var queueTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
                var playlists by remember { mutableStateOf(PlaylistStore.load(prefs)) }
                var downloads by remember { mutableStateOf(AudioDownloads.load(prefs)) }
                var downloadCategories by remember { mutableStateOf(AudioDownloads.loadCategories(prefs, downloads)) }
                var downloadStatuses by remember { mutableStateOf<Map<Long, AudioDownloadStatus?>>(emptyMap()) }
                var showDownloadDialog by remember { mutableStateOf(false) }
                var showCategoryDialog by remember { mutableStateOf(false) }
                var selectedPlaylist by remember { mutableStateOf<String?>(null) }
                var showLinkDialog by remember { mutableStateOf(false) }
                var linkText by remember { mutableStateOf("") }
                var showNewPlaylistDialog by remember { mutableStateOf(false) }
                var playlistName by remember { mutableStateOf("") }
                var tracksToSave by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
                var settingsOrigin by remember { mutableStateOf("Home") }
                var previousPage by remember { mutableStateOf("Home") }
                var artistSearchOrigin by remember { mutableStateOf("Search") }
                var artistQuery by remember { mutableStateOf("") }
                var artistResults by remember { mutableStateOf<List<MusicArtist>>(emptyList()) }
                var selectedArtist by remember { mutableStateOf<ArtistDiscography?>(null) }
                var selectedRelease by remember { mutableStateOf<ArtistRelease?>(null) }
                var artistLoading by remember { mutableStateOf(false) }
                var artistError by remember { mutableStateOf<String?>(null) }
                var releaseTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
                var releaseLoading by remember { mutableStateOf(false) }
                var releaseError by remember { mutableStateOf<String?>(null) }
                var radioLoading by remember { mutableStateOf(false) }
                var radioSeed by remember { mutableStateOf<MusicTrack?>(null) }
                var notice by remember { mutableStateOf<String?>(null) }
                var downloadDialogError by remember { mutableStateOf<String?>(null) }
                var downloadResolving by remember { mutableStateOf(false) }

                var position by remember { mutableLongStateOf(0L) }
                var duration by remember { mutableLongStateOf(0L) }
                var buffering by remember { mutableStateOf(false) }
                var shuffle by remember { mutableStateOf(false) }
                var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
                var seekable by remember { mutableStateOf(false) }
                var audioQuality by remember { mutableStateOf("") }
                val downloadManager = remember { getSystemService(DownloadManager::class.java) }

                LaunchedEffect(downloads) {
                    while (true) {
                        downloadStatuses = withContext(Dispatchers.IO) {
                            downloads.associate { download -> download.id to AudioDownloads.status(downloadManager, download.id) }
                        }
                        delay(900)
                    }
                }

                LaunchedEffect(mediaPlayer) {
                    val player = mediaPlayer ?: return@LaunchedEffect
                    var lastQueueError: String? = null
                    while (true) {
                        val queueError = player.sessionExtras.getString("queue_error")
                        if (queueError != null && queueError != lastQueueError) notice = queueError
                        lastQueueError = queueError
                        position = player.currentPosition.coerceAtLeast(0)
                        duration = player.duration.coerceAtLeast(0)
                        seekable = player.isCurrentMediaItemSeekable
                        radioLoading = player.sessionExtras.getBoolean("radio_loading") || player.sessionExtras.getBoolean("autoplay_loading")
                        buffering = playJob?.isActive == true || player.playbackState == Player.STATE_BUFFERING
                        shuffle = player.shuffleModeEnabled
                        repeat = player.repeatMode
                        isPlaying = player.isPlaying
                        val item = player.currentMediaItem
                        if (item != null) {
                            audioQuality = item.mediaMetadata.extras?.getString("audio_quality").orEmpty()
                            current = knownTracks[item.mediaId] ?: MusicTrack(item.mediaId,
                                item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty(),
                                item.mediaMetadata.extras?.getLong("pipes_duration") ?: 0, item.mediaMetadata.artworkUri?.toString().orEmpty(), "", item.mediaMetadata.extras?.getString("pipes_page_url") ?: item.mediaId)
                        }
                        queueIndex = player.currentMediaItemIndex
                        queueTracks = (0 until player.mediaItemCount).map { index ->
                            val queued = player.getMediaItemAt(index)
                            knownTracks[queued.mediaId] ?: MusicTrack(queued.mediaId,
                                queued.mediaMetadata.title?.toString().orEmpty(), queued.mediaMetadata.artist?.toString().orEmpty(),
                                queued.mediaMetadata.extras?.getLong("pipes_duration") ?: 0, queued.mediaMetadata.artworkUri?.toString().orEmpty(), "", queued.mediaMetadata.extras?.getString("pipes_page_url") ?: queued.mediaId)
                        }
                        delay(300)
                    }
                }
                BackHandler(page != "Home") {
                    page = when (page) {
                        "Settings" -> settingsOrigin
                        "Now playing" -> previousPage
                        "Up next" -> "Now playing"
                        "Playlist" -> "Library"
                        "Release" -> "Artist"
                        "Artist" -> "Artist Search"
                        "Artist Search" -> artistSearchOrigin
                        "Search", "Explore", "Library" -> "Home"
                        else -> "Home"
                    }
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
                    SongSearch(searchScope, { text ->
                        val terms = withContext(Dispatchers.IO) {
                            if (SpotifyLinks.isSpotifyLink(text)) SpotifyLinks.searchQuery(text) else text
                        }
                        saveHistory(com.nazcarbon.music.data.SearchHistory.add(searchHistory, terms))
                        withContext(Dispatchers.IO) { source.searchSongs(terms) }
                    }) { state ->
                        results = state.tracks
                        loading = state.loading
                        searchError = state.error
                        state.tracks.forEach { knownTracks[it.id] = it }
                    }
                }
                fun importSpotifyPlaylist(text: String) {
                    importJob?.cancel()
                    val generation = ++importGeneration
                    songSearch.submit("")
                    importProgress = "Opening public playlist…"
                    query = text
                    page = "Search"
                    loading = true
                    searchError = null
                    importJob = lifecycleScope.launch {
                        runCatching {
                            val spotifyPlaylist = SpotifyPlaylists.load(text) { completed, total ->
                                runOnUiThread {
                                    if (generation == importGeneration && importJob?.isActive == true) {
                                        importProgress = "Reading playlist · $completed / $total items"
                                    }
                                }
                            }
                            val imported = spotifyPlaylist.tracks.mapIndexed { position, spotifyTrack ->
                                MusicTrack(
                                    id = "spotify:${spotifyTrack.id}:${spotifyTrack.position.takeIf { it >= 0 } ?: position}",
                                    title = spotifyTrack.title,
                                    artist = spotifyTrack.artist,
                                    durationSeconds = spotifyTrack.durationSeconds,
                                    thumbnailUrl = spotifyTrack.thumbnailUrl,
                                    streamUrl = "",
                                    pageUrl = "spotify-search:${spotifyTrack.id}",
                                )
                            }
                            require(imported.isNotEmpty()) { "This Spotify playlist has no playable music tracks." }
                            val playlist = MusicPlaylist(spotifyPlaylist.name, imported, spotifyPlaylist.totalTracks, spotifyPlaylist.skippedItems)
                            val updatedPlaylists = if (playlists.any { it.name.equals(playlist.name, ignoreCase = true) }) {
                                playlists.map { if (it.name.equals(playlist.name, ignoreCase = true)) playlist else it }
                            } else playlists + playlist
                            withContext(Dispatchers.IO) { PlaylistStore.save(prefs, updatedPlaylists) }
                            Triple(spotifyPlaylist, imported, playlist) to updatedPlaylists
                        }.onSuccess { (result, updatedPlaylists) ->
                            val (spotifyPlaylist, imported, playlist) = result
                            imported.forEach { knownTracks[it.id] = it }
                            playlists = updatedPlaylists
                            selectedPlaylist = playlist.name
                            page = "Playlist"
                            notice = if (spotifyPlaylist.skippedItems > 0) {
                                "Imported ${imported.size} songs · ${spotifyPlaylist.skippedItems} unavailable or non-music items skipped"
                            } else "Imported ${imported.size} Spotify songs"
                        }.onFailure { failure ->
                            if (failure is CancellationException) throw failure
                            searchError = failure.message ?: "Couldn't import this Spotify playlist."
                        }
                        loading = false
                    }
                }
                fun search(text: String) {
                    if (SpotifyPlaylists.isPlaylistLink(text)) {
                        importSpotifyPlaylist(text)
                        return
                    }
                    importJob?.cancel()
                    importGeneration++
                    importProgress = null
                    query = text
                    page = "Search"
                    songSearch.submit(text)
                }

                fun findArtists(text: String) {
                    val clean = text.trim()
                    if (clean.isBlank()) return
                    artistSearchOrigin = page
                    artistQuery = clean
                    artistResults = emptyList()
                    artistError = null
                    artistLoading = true
                    page = "Artist Search"
                    lifecycleScope.launch {
                        runCatching { withContext(Dispatchers.IO) { source.searchArtists(clean) } }
                            .onSuccess { artistResults = it }
                            .onFailure { artistError = "Artist search couldn't load. Check your connection and retry." }
                        artistLoading = false
                    }
                }

                fun openArtist(artist: MusicArtist) {
                    selectedArtist = ArtistDiscography(artist, emptyList())
                    artistError = null
                    artistLoading = true
                    page = "Artist"
                    lifecycleScope.launch {
                        runCatching { withContext(Dispatchers.IO) { source.loadArtistDiscography(artist) } }
                            .onSuccess { selectedArtist = it }
                            .onFailure { artistError = "Couldn't load this artist's discography. Check your connection and retry." }
                        artistLoading = false
                    }
                }

                fun openRelease(release: ArtistRelease) {
                    val artist = selectedArtist?.artist ?: return
                    selectedRelease = release
                    releaseTracks = emptyList()
                    releaseError = null
                    releaseLoading = true
                    page = "Release"
                    lifecycleScope.launch {
                        runCatching { withContext(Dispatchers.IO) { source.loadReleaseTracks(release, artist.name) } }
                            .onSuccess { tracks ->
                                releaseTracks = tracks
                                tracks.forEach { knownTracks[it.id] = it }
                            }
                            .onFailure { releaseError = "Couldn't load these tracks. Check your connection and retry." }
                        releaseLoading = false
                    }
                }

                fun createPlaylist(name: String) {
                    val clean = name.trim()
                    if (clean.isBlank() || playlists.any { it.name.equals(clean, true) }) { notice = "Enter a unique playlist name"; return }
                    playlists = playlists + MusicPlaylist(clean, emptyList())
                    PlaylistStore.save(prefs, playlists)
                    showNewPlaylistDialog = false
                    playlistName = ""
                    notice = "Playlist created"
                }

                fun removePlaylist(playlist: MusicPlaylist) {
                    playlists = playlists.filterNot { it.name == playlist.name }
                    PlaylistStore.save(prefs, playlists)
                    selectedPlaylist = null
                    page = "Library"
                    notice = "Playlist deleted"
                }

                suspend fun resolveForPlayback(track: MusicTrack): MusicTrack = withContext(Dispatchers.IO) {
                    source.resolveForPlayback(track)
                }

                fun play(track: MusicTrack, keepQueue: Boolean = false, radio: Boolean = false): Int {
                    if (!keepQueue) {
                        queueJob?.cancel()
                        queueTracks = listOf(track)
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
                            val resolved = resolveForPlayback(track)
                            val activePlayer = withContext(Dispatchers.IO) { controllerFuture.get() }
                            val item = musicItem(resolved, resolved.streamUrl, radio, autoplay = !keepQueue || radio)
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

                fun openLink(link: String) {
                    val value = link.trim()
                    if (value.isBlank()) return
                    val youtube = Regex("(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|live/|embed/))([A-Za-z0-9_-]{11})")
                    val match = youtube.find(value)
                    if (match != null) {
                        val url = "https://www.youtube.com/watch?v=${match.groupValues[1]}"
                        val track = MusicTrack(url, match.groupValues[1], "YouTube link", 0, "", "", url)
                        knownTracks[track.id] = track
                        showLinkDialog = false
                        previousPage = page
                        play(track)
                        page = "Now playing"
                    } else if (SpotifyPlaylists.isPlaylistLink(value)) {
                        showLinkDialog = false
                        importSpotifyPlaylist(value)
                    } else if (SpotifyLinks.isSpotifyLink(value)) {
                        showLinkDialog = false
                        search(value)
                    } else {
                        showLinkDialog = false
                        notice = "Paste a YouTube video or Spotify song or playlist link."
                    }
                }

                fun enqueue(candidates: List<MusicTrack>, request: Int) {
                    queueJob?.cancel()
                    queueJob = lifecycleScope.launch {
                        try {
                            val activePlayer = withContext(Dispatchers.IO) { controllerFuture.get() }
                            withTimeout(30_000) {
                                while (completedPlaybackRequest != request) delay(100)
                            }
                            if (request != playbackRequest) return@launch
                            val session = activePlayer.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY)
                                ?: error("The playlist playback session couldn't start.")
                            val queueId = PlaybackQueueRequests.register(session, candidates)
                            activePlayer.sendCustomCommand(SessionCommand("pipes.set_queue", Bundle.EMPTY), Bundle().apply {
                                putString("queue_id", queueId)
                                putString("session", session)
                            })
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { if (request == playbackRequest) error = "Couldn't prepare the queue. Try again." }
                    }
                }

                fun addToQueue(track: MusicTrack) {
                    if (mediaPlayer?.currentMediaItem == null) { play(track); return }
                    lifecycleScope.launch {
                        try {
                            val player = mediaPlayer ?: return@launch
                            val session = player.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY)
                            val resolved = resolveForPlayback(track)
                            if (player.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY) != session) {
                                notice = "Playback changed. Add the song again."
                                return@launch
                            }
                            knownTracks[resolved.id] = resolved
                            val template = player.currentMediaItem ?: return@launch
                            val item = musicItem(resolved, resolved.streamUrl)
                            val extras = Bundle(item.mediaMetadata.extras).apply {
                                putString(AUTO_QUEUE_SESSION_KEY, session)
                                putBoolean(RADIO_METADATA_KEY, template.mediaMetadata.extras?.getBoolean(RADIO_METADATA_KEY) == true)
                                putBoolean(AUTO_QUEUE_METADATA_KEY, template.mediaMetadata.extras?.getBoolean(AUTO_QUEUE_METADATA_KEY) == true)
                            }
                            player.addMediaItem(item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build())
                            notice = "Added to queue · ${track.title}"
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { notice = "Couldn't add this song to the queue. Try again." }
                    }
                }

                fun startRadio(track: MusicTrack) {
                    if (track.pageUrl.startsWith("content://")) { notice = "Radio is available for online songs."; return }
                    queueJob?.cancel()
                    radioSeed = track
                    error = null
                    if (page != "Now playing") previousPage = page
                    page = "Now playing"
                    playJob?.cancel()
                    playbackRequest += 1
                    val activePlayer = mediaPlayer
                    if (activePlayer?.currentMediaItem?.mediaId == track.id) {
                        activePlayer.sendCustomCommand(SessionCommand("pipes.start_radio", Bundle.EMPTY), Bundle.EMPTY)
                    } else {
                        play(track, keepQueue = true, radio = true)
                    }
                    notice = "Radio started · songs will appear in Up next"
                }

                fun startVibeRadio(vibe: String) {
                    radioLoading = true
                    lifecycleScope.launch {
                        runCatching { withContext(Dispatchers.IO) { source.searchSongs(vibe).firstOrNull() ?: error("No songs found for that vibe") } }
                            .onSuccess { track -> knownTracks[track.id] = track; startRadio(track) }
                            .onFailure { notice = it.message ?: "Couldn't find a radio for that vibe." }
                        radioLoading = false
                    }
                }

                fun playSequence(track: MusicTrack, sequence: List<MusicTrack>, radio: Boolean = false) {
                    if (!radio) radioSeed = null
                    val ordered = sequence.distinctBy { it.id }.dropWhile { it.id != track.id }.ifEmpty { listOf(track) }
                    queueTracks = ordered
                    val request = play(track, keepQueue = true, radio = radio)
                    enqueue(ordered.drop(1), request)
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

                fun choosePlaylist(track: MusicTrack) { tracksToSave = listOf(track) }
                fun chooseTracksForPlaylist(tracks: List<MusicTrack>) { tracksToSave = tracks }
                fun addTracksToPlaylist(name: String) {
                    val tracks = tracksToSave
                    val playlist = playlists.firstOrNull { it.name == name } ?: return
                    val existingIds = playlist.tracks.mapTo(mutableSetOf()) { it.id }
                    val additions = tracks.filter { existingIds.add(it.id) }
                    if (additions.isEmpty()) { notice = "Already in $name" }
                    else {
                        playlists = playlists.map { if (it.name == name) it.copy(tracks = it.tracks + additions.map { track -> track.copy(streamUrl = "") }) else it }
                        PlaylistStore.save(prefs, playlists)
                        notice = "Added ${additions.size} ${if (additions.size == 1) "song" else "songs"} to $name"
                    }
                    tracksToSave = emptyList()
                }

                fun toggleLike(track: MusicTrack) {
                    val removing = track.id in likedIds
                    likedIds = if (removing) likedIds - track.id else likedIds + track.id
                    knownTracks[track.id] = track
                    prefs.edit().putStringSet("liked", likedIds).apply()
                    PlaylistStore.save(prefs, listOf(MusicPlaylist("Liked songs", likedIds.mapNotNull { knownTracks[it] })), "saved_tracks")
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

                fun addAudioDownload(url: String, title: String, artist: String, category: String) {
                    if (downloadResolving) return
                    if (category.trim().length > 32) {
                        downloadDialogError = "Category names can be up to 32 characters."
                        return
                    }
                    downloadResolving = true
                    downloadDialogError = null
                    searchScope.launch {
                        try {
                            val download = withContext(Dispatchers.IO) {
                                val instagram = com.nazcarbon.music.data.InstagramDownloads.isInstagram(url)
                                val resolved = if (instagram) com.nazcarbon.music.data.InstagramDownloads.resolve(url) else url
                                AudioDownloads.enqueue(this@MainActivity, downloadManager, resolved, title, artist, category, instagram)
                            }
                            downloads = listOf(download) + downloads
                            AudioDownloads.save(prefs, downloads)
                            if (downloadCategories.none { it.equals(download.category, ignoreCase = true) }) {
                                downloadCategories = AudioDownloads.addCategory(downloadCategories, download.category)
                                AudioDownloads.saveCategories(prefs, downloadCategories)
                            }
                            showDownloadDialog = false
                            downloadDialogError = null
                            notice = "Download added · ${download.category}"
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (failure: IllegalArgumentException) {
                            downloadDialogError = failure.message ?: "This media link is unsupported."
                        } catch (_: Exception) {
                            downloadDialogError = "Couldn't download this link. Use a direct HTTPS audio file or an accessible public Instagram video."
                        } finally {
                            downloadResolving = false
                        }
                    }
                }

                fun addDownloadCategory(name: String): String? = runCatching {
                    AudioDownloads.addCategory(downloadCategories, name).also { updated ->
                        downloadCategories = updated
                        AudioDownloads.saveCategories(prefs, updated)
                    }
                }.exceptionOrNull()?.message

                fun removeDownloadCategory(name: String) {
                    val movedCount = downloads.count { it.category.equals(name, ignoreCase = true) }
                    val (updatedCategories, updatedDownloads) = AudioDownloads.removeCategory(downloadCategories, downloads, name)
                    downloadCategories = updatedCategories
                    downloads = updatedDownloads
                    AudioDownloads.saveCategories(prefs, updatedCategories)
                    AudioDownloads.save(prefs, updatedDownloads)
                    notice = if (movedCount > 0) {
                        "Category removed · downloads moved to Unsorted"
                    } else "Category removed"
                }

                fun openOnSpotify(track: MusicTrack) {
                    val search = Uri.encode("${track.title} ${track.artist}")
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$search")))
                    }.onFailure { notice = "Couldn't open Spotify." }
                }

                fun playDownloaded(download: AudioDownload) {
                    val uri = downloadManager.getUriForDownloadedFile(download.id)
                    if (uri == null) {
                        notice = "This file is not ready yet."
                        return
                    }
                    val track = MusicTrack(
                        id = "download:${download.id}",
                        title = download.title,
                        artist = download.artist,
                        durationSeconds = 0,
                        thumbnailUrl = "",
                        streamUrl = uri.toString(),
                        pageUrl = uri.toString(),
                        audioQuality = "Offline · ${download.category}",
                    )
                    knownTracks[track.id] = track
                    play(track)
                    page = "Now playing"
                }

                fun removeAudioDownload(download: AudioDownload) {
                    if (current?.id == "download:${download.id}") {
                        mediaPlayer?.stop()
                        mediaPlayer?.clearMediaItems()
                        current = null
                        isPlaying = false
                    }
                    downloadManager.remove(download.id)
                    downloads = downloads.filterNot { it.id == download.id }
                    AudioDownloads.save(prefs, downloads)
                    notice = "Download removed"
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
                CompositionLocalProvider(LocalAddToQueue provides ::addToQueue, LocalRemoveFromQueue provides { track ->
                    mediaPlayer?.let { player ->
                        val index = ((player.currentMediaItemIndex + 1) until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == track.id }
                        if (index != null) { player.removeMediaItem(index); notice = "Removed from queue · ${track.title}" }
                        else notice = "This song is not in the upcoming queue"
                    }
                }) {
                Surface(Modifier.fillMaxSize(), color = Carbon) {
                    Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                        Column(Modifier.weight(1f)) {
                            if (page !in listOf("Now playing", "Settings", "Library")) Header(query, ::search, page, { openAudio.launch(arrayOf("audio/*")) }, { showLinkDialog = true }, { settingsOrigin = page; page = "Settings" })
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
                                    { page = previousPage }, { saveToCommute(current!!) },
                                    { findArtists(current!!.artist) },
                                    mediaPlayer?.hasNextMediaItem() == true,
                                )
                            } else when (page) {
                                "Settings" -> AppSettingsScreen(this@MainActivity, updates, { page = settingsOrigin }) { RecommendationRegionPicker() }
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
                                downloads,
                                downloadCategories,
                                downloadStatuses,
                                    { downloadDialogError = null; showDownloadDialog = true },
                                    { showCategoryDialog = true },
                                ::playDownloaded,
                                ::removeAudioDownload,
                                { showNewPlaylistDialog = true },
                                ::startRadio,
                                )
                                "Search" -> if (query.isBlank()) SearchHistoryPanel(searchHistory, ::search,
                                    { value -> saveHistory(searchHistory.filterNot { it == value }) }, { saveHistory(emptyList()) })
                                else SearchResults(query, results, loading, searchError, current, isPlaying, likedIds, { track -> playSequence(track, results) }, ::toggleLike, ::startRadio, ::choosePlaylist, ::openOnSpotify, ::findArtists, { search(query) }, loadingMessage = importProgress)
                                "Artist Search" -> ArtistSearchResults(artistQuery, artistResults, artistLoading, artistError, { findArtists(artistQuery) }, ::openArtist)
                                "Artist" -> ArtistDiscographyScreen(
                                    selectedArtist,
                                    artistLoading,
                                    artistError,
                                    { selectedArtist?.artist?.let { startVibeRadio("${it.name} songs") } },
                                    ::openRelease,
                                )
                                "Release" -> selectedRelease?.let { release ->
                                    ReleaseTracksScreen(
                                        release,
                                        selectedArtist?.artist?.name.orEmpty(),
                                        releaseTracks,
                                        releaseLoading,
                                        releaseError,
                                        { if (releaseTracks.isNotEmpty()) playSequence(releaseTracks.first(), releaseTracks) },
                                        { track -> playSequence(track, releaseTracks) },
                                        { chooseTracksForPlaylist(releaseTracks) },
                                        ::choosePlaylist,
                                        { startVibeRadio("${selectedArtist?.artist?.name.orEmpty()} songs") },
                                    )
                                }
                                "Up next" -> UpNext(queueTracks, queueIndex, current, isPlaying, radioSeed, radioLoading, { index ->
                                    mediaPlayer?.let { player ->
                                        if (index in 0 until player.mediaItemCount) { player.seekTo(index, 0L); player.play() }
                                    }
                                }, ::startRadio, { index -> mediaPlayer?.let { if (index > it.currentMediaItemIndex && index < it.mediaItemCount) it.removeMediaItem(index) } }, ::toggleLike, ::choosePlaylist, likedIds)
                                "Playlist" -> {
                                    val playlist = playlists.firstOrNull { it.name == selectedPlaylist }
                                    if (playlist != null) PlaylistScreen(
                                        playlist,
                                        current,
                                        isPlaying,
                                        { track -> playSequence(track, playlist.tracks) },
                                        ::startRadio,
                                        ::choosePlaylist,
                                        { removePlaylist(playlist) },
                                    ) else Library(likedIds.mapNotNull { knownTracks[it] }, likedIds, playlists, current, isPlaying, { selectedPlaylist = it.name; page = "Playlist" }, ::play, { }, ::saveToCommute, downloads, downloadCategories, downloadStatuses, { downloadDialogError = null; showDownloadDialog = true }, { showCategoryDialog = true }, ::playDownloaded, ::removeAudioDownload, { showNewPlaylistDialog = true }, ::startRadio)
                                }
                                "Explore" -> Explore(::search)
                                else -> DiscoveryHome(discoveryFeed, discoveryScroll, likedArtists.toList(), current, likedIds, ::play, ::toggleLike, ::startRadio, ::choosePlaylist)
                            }
                        }
                        if (page != "Now playing") BottomNav(page) { if (it == "Search") search("") else page = it }
                    }
                    if (page !in listOf("Now playing", "Up next")) current?.let { FloatingPlayer(it, isPlaying,
                        { mediaPlayer?.let { p -> if (p.isPlaying) p.pause() else p.play() } },
                        { previousPage = page; page = "Now playing" }, { skipTrack(1) }, { skipTrack(-1) }, position, duration) }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (page == "Now playing") 8.dp else 84.dp))
                    }
                }
                }
                if (showDownloadDialog) {
                    DownloadAudioDialog(
                        error = downloadDialogError,
                        busy = downloadResolving,
                        onDismiss = { if (!downloadResolving) { showDownloadDialog = false; downloadDialogError = null } },
                        onDownload = ::addAudioDownload,
                    )
                }
                if (showLinkDialog) AlertDialog(
                    onDismissRequest = { showLinkDialog = false },
                    title = { Text("Open a link") },
                    text = { OutlinedTextField(linkText, { linkText = it }, label = { Text("YouTube video or Spotify song/playlist") }, singleLine = true) },
                    confirmButton = { TextButton(onClick = { openLink(linkText) }) { Text("Open") } },
                    dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") } },
                )
                if (showNewPlaylistDialog) AlertDialog(
                    onDismissRequest = { showNewPlaylistDialog = false },
                    title = { Text("New playlist") },
                    text = { OutlinedTextField(playlistName, { playlistName = it }, label = { Text("Playlist name") }, singleLine = true) },
                    confirmButton = { TextButton(onClick = { createPlaylist(playlistName) }) { Text("Create") } },
                    dismissButton = { TextButton(onClick = { showNewPlaylistDialog = false }) { Text("Cancel") } },
                )
                if (tracksToSave.isNotEmpty()) AlertDialog(
                    onDismissRequest = { tracksToSave = emptyList() },
                    title = { Text("Add ${tracksToSave.size} ${if (tracksToSave.size == 1) "song" else "songs"} to playlist") },
                    text = {
                        Column {
                            if (playlists.isEmpty()) Text("Create a playlist in Your library first.", color = Muted)
                            playlists.forEach { playlist -> TextButton(onClick = { addTracksToPlaylist(playlist.name) }) { Text(playlist.name) } }
                            TextButton(onClick = { tracksToSave = emptyList(); showNewPlaylistDialog = true }) { Text("Create a playlist") }
                        }
                    },
                    confirmButton = { TextButton(onClick = { tracksToSave = emptyList() }) { Text("Done") } },
                )
                if (showCategoryDialog) {
                    CategoryManagerDialog(
                        categories = downloadCategories,
                        onDismiss = { showCategoryDialog = false },
                        onAdd = ::addDownloadCategory,
                        onRemove = ::removeDownloadCategory,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (::controllerFuture.isInitialized) MediaController.releaseFuture(controllerFuture)
        super.onDestroy()
    }
}

@Composable private fun Header(query: String, onSearch: (String) -> Unit, page: String, onOpenAudio: () -> Unit, onLink: () -> Unit, onSettings: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth().background(Carbon).padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            PipesWordmark()
            Spacer(Modifier.weight(1f))
            if (page == "Home") IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Search, "Search music", tint = Ink) }
            IconButton(onClick = { keyboard?.hide(); onSettings() }) { Icon(Icons.Default.Settings, "App settings", tint = Ink) }
            IconButton(onClick = onOpenAudio) { Icon(Icons.Default.FolderOpen, "Open local audio, including lossless FLAC and WAV", tint = Ink) }
        }
        if (page != "Library" && page != "Home") {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch(query) }),
            trailingIcon = { Row {
                IconButton(onClick = onLink) { Icon(Icons.Default.Link, "Search by link", tint = Ink) }
                if (query.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Close, "Clear search") }
            } },
            placeholder = { Text("Songs, artists, albums", color = Muted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Ink) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink, focusedBorderColor = Accent, unfocusedBorderColor = Raised, cursorColor = Accent, focusedContainerColor = Panel, unfocusedContainerColor = Panel),
        )
        }
        if (page == "Home") Spacer(Modifier.height(2.dp))
    }
}

@Composable private fun Home(onSearch: (String) -> Unit, likedArtists: List<String>, playlists: List<MusicPlaylist>, current: MusicTrack?, onRadio: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Spacer(Modifier.height(6.dp))
            Text("Room for your music.", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("A little quieter. A little more you.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().heightIn(min = 184.dp).clip(RoundedCornerShape(24.dp)).background(Panel).border(1.dp, Raised, RoundedCornerShape(24.dp)).clickable { onSearch("indie pop") }) {
                Column(Modifier.padding(24.dp).padding(end = 40.dp)) {
                    Text("PIPES RADIO · 001", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Your soft\nlanding.", color = Ink, fontSize = 29.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Dreamy indie for wherever you're headed.", color = Muted, fontSize = 11.sp)
                }
                Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = Ink, modifier = Modifier.size(30.dp))
                }
            }
        }
        if (current != null) item {
            Text("Keep the vibe going", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                items((listOf(current) + playlists.flatMap { it.tracks }).distinctBy { it.id }.take(12), key = { "home:${it.id}" }) { track ->
                    Column(Modifier.width(140.dp).clickable { onSearch("${track.artist} ${track.title}") }) {
                        ArtworkImage(track.thumbnailUrl, null, Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)).background(Raised), contentScale = ContentScale.Crop)
                        Text(track.title, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            TextButton(onClick = { onRadio(if (likedArtists.isNotEmpty()) "${likedArtists.take(3).joinToString(" ")} similar music" else "indie pop radio") }) {
                Icon(Icons.Default.Radio, null, tint = Ink); Spacer(Modifier.width(8.dp)); Text("Start a radio for your taste", color = Ink)
            }
        }
        item { Text("Find your frequency", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        items(listOf("indie pop", "late night r&b", "dream pop", "ambient focus", "new music")) { mood ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable { onSearch(mood) }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Raised), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Ink) }
                Spacer(Modifier.width(16.dp)); Text(mood.replaceFirstChar { it.uppercase() }, color = Ink, fontWeight = FontWeight.Medium); Spacer(Modifier.weight(1f)); Text("↗", color = Muted)
            }
        }
    }
}


@Composable private fun SearchHistoryPanel(history: List<String>, onSearch: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent searches", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear all") }
        }
        if (history.isEmpty()) Text("Find your next favorite. Your searches will appear here.", color = Muted, modifier = Modifier.padding(20.dp))
        LazyColumn {
            items(history, key = { it }) { query ->
                Row(Modifier.fillMaxWidth().clickable { onSearch(query) }.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = Ink, modifier = Modifier.size(18.dp))
                    Text(query, color = Ink, modifier = Modifier.weight(1f).padding(16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onRemove(query) }) { Icon(Icons.Default.Close, "Delete search: $query", tint = Ink) }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear search history?") },
        text = { Text("This removes all recent searches from this device.") },
        confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Clear all") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

@Composable private fun SearchResults(query: String, tracks: List<MusicTrack>, loading: Boolean, error: String?, current: MusicTrack?, playing: Boolean, liked: Set<String>, onPlay: (MusicTrack) -> Unit, onLike: (MusicTrack) -> Unit, onRadio: (MusicTrack) -> Unit, onSave: (MusicTrack) -> Unit, onSpotify: (MusicTrack) -> Unit, onArtist: (String) -> Unit, onRetry: () -> Unit, loadingMessage: String? = null) {
    Column(Modifier.fillMaxSize()) {
        Text(if (query.isBlank()) "Find a sound" else "Search results", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        TextButton(onClick = { onArtist(query) }, enabled = query.isNotBlank(), modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(Icons.Default.MusicNote, null, tint = Ink)
            Spacer(Modifier.width(8.dp))
            Text(if (query.isBlank()) "Browse artist discography" else "Artist discography · $query", color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            loading -> Column(Modifier.align(Alignment.CenterHorizontally).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PipesLoading()
                loadingMessage?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
            }
            error != null -> Column(Modifier.padding(20.dp)) {
                Text(error, color = Muted)
                TextButton(onClick = onRetry) { Text("Retry search") }
            }
            tracks.isEmpty() -> Text(if (query.isBlank()) "Search for your favorite songs and artists." else "No songs found. Try another title or artist.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(tracks, key = { it.id }) { track -> TrackRow(track, current?.id == track.id && playing, liked.contains(track.id), onPlay, onLike, onRadio, onSave, onSpotify, onArtist) }
            }
        }
    }
}

@Composable private fun TrackRow(track: MusicTrack, active: Boolean, liked: Boolean, onPlay: (MusicTrack) -> Unit, onLike: (MusicTrack) -> Unit, onRadio: (MusicTrack) -> Unit, onSave: (MusicTrack) -> Unit, onSpotify: ((MusicTrack) -> Unit)? = null, onArtist: ((String) -> Unit)? = null, onRemove: (() -> Unit)? = null) {
    val add = LocalAddToQueue.current
    val remove = LocalRemoveFromQueue.current
    QueueGesture(track, { onPlay(track) }, { add(track) }, onRemove ?: { remove(track) },
        { onLike(track) }, { onRadio(track) }, { onSave(track) }, onSpotify?.let { { it(track) } }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(track.thumbnailUrl, null, Modifier.size(56.dp).shadow(4.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Raised), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(track.artist, color = Muted, maxLines = 1, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp).clickable(enabled = onArtist != null) { onArtist?.invoke(track.artist) })
            }
            if (active) SignalMark(Modifier.width(40.dp))
            IconButton(onClick = { onLike(track) }) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (liked) "Unlike song" else "Like song", tint = if (liked) Ink else Muted, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable private fun Library(
    tracks: List<MusicTrack>,
    likedIds: Set<String>,
    playlists: List<MusicPlaylist>,
    current: MusicTrack?,
    playing: Boolean,
    onOpenPlaylist: (MusicPlaylist) -> Unit,
    onPlay: (MusicTrack) -> Unit,
    onLike: (MusicTrack) -> Unit,
    onSave: (MusicTrack) -> Unit,
    downloads: List<AudioDownload>,
    downloadCategories: List<String>,
    downloadStatuses: Map<Long, AudioDownloadStatus?>,
    onAddDownload: () -> Unit,
    onManageCategories: () -> Unit,
    onPlayDownload: (AudioDownload) -> Unit,
    onRemoveDownload: (AudioDownload) -> Unit,
    onCreatePlaylist: () -> Unit,
    onRadio: (MusicTrack) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All") + downloadCategories
    LaunchedEffect(categories) {
        if (selectedCategory !in categories) selectedCategory = "All"
    }
    val collection = (tracks + playlists.flatMap { it.tracks }).distinctBy { it.id }
    val visibleDownloads = downloads.filter { selectedCategory == "All" || it.category.equals(selectedCategory, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp)) {
            item(key = "library-records") {
                Column(Modifier.padding(24.dp)) {
                    SignalMark()
                    Text("Your library.", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 16.dp))
                    Text("${collection.size} recordings  /  ${playlists.size} playlists", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("Swipe right to queue · left to remove · hold for actions", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp))
                }
                if (collection.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    collection.take(2).forEach { track ->
                        val add = LocalAddToQueue.current
                        val remove = LocalRemoveFromQueue.current
                        Box(Modifier.weight(1f)) {
                            QueueGesture(track, { onPlay(track) }, { add(track) }, { remove(track) }, { onLike(track) }, { onRadio(track) }, { onSave(track) }) {
                                Column {
                                    ArtworkImage(track.thumbnailUrl, null, Modifier.fillMaxWidth().aspectRatio(1f).shadow(8.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Raised), contentScale = ContentScale.Crop)
                                    Text(track.title, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp), fontSize = 13.sp)
                                    Text(track.artist, color = Muted, maxLines = 1, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                                }
                            }
                        }
                    }
                    if (collection.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item(key = "library-tools") {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("YOUR COLLECTION", color = Ink, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = onCreatePlaylist) { Icon(Icons.Default.PlaylistAdd, "Create playlist", tint = Ink) }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onAddDownload, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)) {
                            Icon(Icons.Default.MusicNote, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Download", maxLines = 1)
                        }
                        OutlinedButton(onClick = onManageCategories, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)) {
                            Icon(Icons.Default.FolderOpen, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Categories", maxLines = 1)
                        }
                    }
                    Text(
                        "Download audio or public Instagram videos you own or have permission to save.",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                    )
                }
            }
            item {
                Text("DOWNLOADS · ${downloads.size}", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp))
            }
            if (categories.size > 1) item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                        )
                    }
                }
            }
            if (visibleDownloads.isEmpty()) item {
                Text(
                    if (downloads.isEmpty()) "Your downloaded audio will appear here, organized by category." else "No downloads in this category yet.",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else items(visibleDownloads, key = { "download:${it.id}" }) { download ->
                DownloadRow(download, downloadStatuses[download.id], onPlayDownload, onRemoveDownload)
            }
            item {
                Text("PLAYLISTS", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp))
            }
            if (playlists.isEmpty()) item {
                Text("Create a playlist to collect your favorites.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            } else items(playlists, key = { "playlist:${it.name}" }) { playlist ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenPlaylist(playlist) }.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(9.dp)).background(Raised), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.QueueMusic, null, tint = Ink)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("${playlist.tracks.size} songs", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.PlayArrow, null, tint = Ink)
                }
            }
            item {
                Text("LIKED SONGS", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp))
            }
            if (tracks.isEmpty()) item {
                Text("Tap ♡ on a song to save it here.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            } else items(tracks, key = { "liked:${it.id}" }) {
                TrackRow(it, current?.id == it.id && playing, likedIds.contains(it.id), onPlay, onLike, onRadio, onSave)
            }
        }
    }
}

@Composable private fun CategoryManagerDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> String?,
    onRemove: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download categories") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Create categories or remove them. Downloads in a removed category move to Unsorted; their files stay on your phone.", color = Muted, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it; error = null }, label = { Text("New category") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        error = onAdd(name)
                        if (error == null) name = ""
                    }, enabled = name.isNotBlank()) { Text("Add") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                categories.forEach { category ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(category, color = Ink, modifier = Modifier.weight(1f))
                        if (!category.equals(AudioDownloads.DEFAULT_CATEGORY, ignoreCase = true)) {
                            IconButton(onClick = { onRemove(category) }) { Icon(Icons.Default.Delete, "Delete $category", tint = Ink) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable private fun DownloadRow(
    download: AudioDownload,
    status: AudioDownloadStatus?,
    onPlay: (AudioDownload) -> Unit,
    onRemove: (AudioDownload) -> Unit,
) {
    val ready = status?.status == DownloadManager.STATUS_SUCCESSFUL
    val inProgress = status?.status in setOf(
        DownloadManager.STATUS_PENDING,
        DownloadManager.STATUS_RUNNING,
        DownloadManager.STATUS_PAUSED,
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Raised), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, null, tint = Ink)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(download.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${download.artist} · ${download.category}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        ready -> "Saved for offline listening · ${download.mimeType.substringAfter('/').uppercase()}"
                        inProgress -> status?.progress?.let { "Downloading · ${(it * 100).toInt()}%" } ?: "Downloading…"
                        status?.status == DownloadManager.STATUS_FAILED -> "Download failed · remove and retry"
                        else -> "Checking download…"
                    },
                    color = if (ready) Accent else Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onPlay(download) }, enabled = ready) {
                Icon(Icons.Default.PlayArrow, "Play offline", tint = if (ready) Ink else Muted.copy(alpha = 0.4f))
            }
            IconButton(onClick = { onRemove(download) }) {
                Icon(Icons.Default.Delete, "Remove download", tint = Ink)
            }
        }
        if (inProgress) {
            val progress = status?.progress
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = Ink)
            else LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().padding(top = 6.dp), color = Ink)
        }
    }
}

@Composable private fun DownloadAudioDialog(
    error: String?,
    onDismiss: () -> Unit,
    busy: Boolean,
    onDownload: (String, String, String, String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Unsorted") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download media") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Paste a direct HTTPS audio link or a public Instagram Reel/video link. Instagram videos save as MP4; playback uses their audio.", color = Muted, fontSize = 13.sp)
                OutlinedTextField(url, { url = it }, label = { Text("Audio or Instagram URL") }, singleLine = true)
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
                Text("Downloads are stored on this phone for offline playback. YouTube and YouTube Music links are stream links, not downloadable audio files.", color = Muted, fontSize = 12.sp)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onDownload(url, title, artist, category) }, enabled = url.isNotBlank() && !busy) { Text(if (busy) "Resolving…" else "Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable private fun PlaylistScreen(
    playlist: MusicPlaylist,
    current: MusicTrack?,
    playing: Boolean,
    onPlay: (MusicTrack) -> Unit,
    onRadio: (MusicTrack) -> Unit,
    onSave: (MusicTrack) -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(playlist.name, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete playlist", tint = Ink) }
        }
        Text("${playlist.tracks.size} songs · saved on this device", color = Muted, modifier = Modifier.padding(horizontal = 24.dp))
        if (playlist.sourceTotal > playlist.tracks.size + playlist.sourceSkipped) {
            Text("Spotify exposes ${playlist.tracks.size} of ${playlist.sourceTotal} songs without sign-in. This is a partial import.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
        }
        if (playlist.sourceSkipped > 0) {
            Text("${playlist.sourceSkipped} unavailable or non-music items skipped", color = Muted,
                fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
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
    queueIndex: Int,
    current: MusicTrack?,
    playing: Boolean,
    radioSeed: MusicTrack?,
    radioLoading: Boolean,
    onPlay: (Int) -> Unit,
    onRadio: (MusicTrack) -> Unit,
    onRemove: (Int) -> Unit,
    onLike: (MusicTrack) -> Unit,
    onSave: (MusicTrack) -> Unit,
    liked: Set<String>,
) {
    val following = tracks.drop((queueIndex + 1).coerceAtLeast(0))
    Column(Modifier.fillMaxSize()) {
        Text("Up next", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        Text(
            when {
                radioLoading && radioSeed != null -> "Finding songs related to ${radioSeed.title}…"
                radioLoading -> "Finding fresh songs for the queue…"
                radioSeed != null -> "Radio from ${radioSeed.title}"
                else -> "Autoplay · the queue keeps going"
            },
            color = Muted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        RecommendationRegionPicker()
        if (following.isEmpty()) {
            if (radioLoading) PipesLoading(modifier = Modifier.padding(24.dp))
            else Text("Autoplay will fill this queue with related songs as you listen.", color = Muted, modifier = Modifier.padding(20.dp))
        } else LazyColumn {
            items(following.size) { offset ->
                val track = following[offset]
                TrackRow(track, current?.id == track.id && playing, track.id in liked, { onPlay(queueIndex + 1 + offset) }, onLike, onRadio, onSave, onRemove = { onRemove(queueIndex + 1 + offset) })
            }
        }
    }
}

@Composable private fun ArtistSearchResults(
    query: String,
    artists: List<MusicArtist>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenArtist: (MusicArtist) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text("Artists", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text("Discography search · $query", color = Muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        when {
            loading -> PipesLoading(modifier = Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
            error != null -> Column(Modifier.padding(20.dp)) {
                Text(error, color = Muted)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            artists.isEmpty() -> Text("No artist pages found. Try the artist's full name.", color = Muted, modifier = Modifier.padding(20.dp))
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)) {
                items(artists, key = { it.url }) { artist ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpenArtist(artist) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkImage(artist.thumbnailUrl, null, Modifier.size(58.dp).clip(CircleShape).background(Raised), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(artist.name, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Open albums, singles & radio", color = Muted, fontSize = 12.sp)
                        }
                        Text("›", color = Ink, fontSize = 26.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun ArtistDiscographyScreen(
    discography: ArtistDiscography?,
    loading: Boolean,
    error: String?,
    onRadio: () -> Unit,
    onOpenRelease: (ArtistRelease) -> Unit,
) {
    val releases = remember(discography?.releases) { discography?.releases.orEmpty() }
    val singles = remember(releases) {
        releases.filter(::isSingleRelease)
    }
    val albums = remember(releases) {
        releases.filterNot(::isSingleRelease)
    }
    val selectedReleaseTab = remember(releases) {
        mutableIntStateOf(if (singles.isNotEmpty()) 1 else 0)
    }
    val chronologicalReleases = remember(releases) {
        releases.withIndex()
            .sortedWith(compareByDescending<IndexedValue<ArtistRelease>> { it.value.year ?: Int.MIN_VALUE }.thenBy { it.index })
            .map { it.value }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (discography != null) {
            item(key = "artist-header") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(
                        discography.artist.thumbnailUrl, null,
                        Modifier.size(76.dp).clip(CircleShape).background(Raised),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(discography.artist.name, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Artist discography", color = Muted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    ArtistArtwork(releases.firstOrNull()?.thumbnailUrl.orEmpty(), Modifier.size(78.dp))
                }
            }
            item(key = "artist-radio") {
                TextButton(onClick = onRadio, modifier = Modifier.padding(start = 10.dp)) {
                    Icon(Icons.Default.Radio, null, tint = Ink)
                    Spacer(Modifier.width(8.dp))
                    Text("Artist radio", color = Ink)
                }
            }
            if (discography.artist.description.isNotBlank()) {
                item(key = "artist-description") {
                    Text(discography.artist.description, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                }
            }
        }
        when {
            loading -> item(key = "artist-loading") { PipesLoading(modifier = Modifier.fillMaxWidth().wrapContentWidth().padding(28.dp)) }
            error != null -> item(key = "artist-error") { Text(error, color = Muted, modifier = Modifier.padding(20.dp)) }
            discography == null || releases.isEmpty() -> item(key = "artist-empty") { Text("No albums or singles were found for this artist.", color = Muted, modifier = Modifier.padding(20.dp)) }
            else -> {
                item(key = "release-tabs") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilterChip(
                            selected = selectedReleaseTab.intValue == 0,
                            onClick = { selectedReleaseTab.intValue = 0 },
                            label = { Text("Albums · ${albums.size}") },
                        )
                        FilterChip(
                            selected = selectedReleaseTab.intValue == 1,
                            onClick = { selectedReleaseTab.intValue = 1 },
                            label = { Text("Singles & EPs · ${singles.size}") },
                        )
                    }
                }
                val showingSingles = selectedReleaseTab.intValue == 1
                val selectedReleases = if (showingSingles) singles else albums
                item(key = if (showingSingles) "singles-section-title" else "albums-section-title") {
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(if (showingSingles) "Singles & EPs" else "Albums", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (showingSingles) "STANDALONE TRACKS · SINGLES · EPS" else "SWIPE TO EXPLORE  ·  HOLD A COVER TO FLIP",
                            color = Muted,
                            fontSize = 10.sp,
                            letterSpacing = 1.1.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (selectedReleases.isEmpty()) {
                    item(key = "release-tab-empty") {
                        Text(
                            if (showingSingles) "No singles or EPs were found for this artist." else "No albums were found for this artist.",
                            color = Muted,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    item(key = if (showingSingles) "singles-carousel" else "albums-carousel") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (showingSingles) 14.dp else 16.dp),
                        ) {
                            itemsIndexed(selectedReleases, key = { _, release -> "${if (showingSingles) "single" else "album"}:${release.url}" }) { index, release ->
                                ArtistReleaseCard(release, index, isSingle = showingSingles, onOpen = onOpenRelease)
                            }
                        }
                    }
                }
                item(key = "chronology-title") {
                    Text("Chronological discography · newest first", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp))
                }
                items(chronologicalReleases, key = { "list:${it.url}" }) { release -> ArtistReleaseRow(release, onOpenRelease) }
            }
        }
    }
}

private fun isSingleRelease(release: ArtistRelease): Boolean =
    release.kind.contains("single", true) || release.kind.contains("ep", true) || release.trackCount == 1L

@Composable private fun ArtistArtwork(imageUrl: String, modifier: Modifier = Modifier) {
    ArtworkImage(imageUrl, null, modifier.shadow(8.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Raised), contentScale = ContentScale.Crop)
}

@Composable private fun ArtistReleaseCard(release: ArtistRelease, index: Int, isSingle: Boolean, onOpen: (ArtistRelease) -> Unit) {
    Column(Modifier.width(if (isSingle) 176.dp else 224.dp).clickable { onOpen(release) }.padding(bottom = 16.dp)) {
        ArtworkImage(release.thumbnailUrl, null, Modifier.fillMaxWidth().aspectRatio(1f).shadow(12.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Raised), contentScale = ContentScale.Crop)
        Text("${(index + 1).toString().padStart(2, '0')}  /  ${release.kind.uppercase()}", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 16.dp))
        Text(release.title, color = Ink, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(listOfNotNull(release.year?.toString(), release.trackCount.takeIf { it > 0 }?.let { "$it tracks" }).joinToString(" · "), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable private fun ArtistReleaseRow(release: ArtistRelease, onOpen: (ArtistRelease) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(release) }.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(release.thumbnailUrl, null, Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).background(Raised), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(release.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(release.year?.toString(), release.kind, release.trackCount.takeIf { it > 0 }?.let { "$it tracks" })
            Text(details.joinToString(" · "), color = Muted, fontSize = 12.sp)
        }
        Text("›", color = Ink, fontSize = 26.sp)
    }
}

@Composable private fun ReleaseTracksScreen(
    release: ArtistRelease,
    artistName: String,
    tracks: List<MusicTrack>,
    loading: Boolean,
    error: String?,
    onPlayAll: () -> Unit,
    onPlayTrack: (MusicTrack) -> Unit,
    onAddRelease: () -> Unit,
    onAddTrack: (MusicTrack) -> Unit,
    onRadio: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(release.thumbnailUrl, null, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Raised), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(release.title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$artistName · ${release.kind}", color = Muted, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            TextButton(onClick = onPlayAll, enabled = tracks.isNotEmpty()) { Icon(Icons.Default.PlayArrow, null, tint = Ink); Text("Play release", color = Ink) }
            TextButton(onClick = onAddRelease, enabled = tracks.isNotEmpty()) { Icon(Icons.Default.PlaylistAdd, null, tint = Ink); Text("Add whole release", color = Ink) }
            TextButton(onClick = onRadio) { Icon(Icons.Default.Radio, null, tint = Ink); Text("Artist radio", color = Ink) }
        }
        when {
            loading -> PipesLoading(modifier = Modifier.align(Alignment.CenterHorizontally).padding(28.dp))
            error != null -> Text(error, color = Muted, modifier = Modifier.padding(20.dp))
            tracks.isEmpty() -> Text("No tracks were found on this release.", color = Muted, modifier = Modifier.padding(20.dp))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(track, false, false, onPlayTrack, {}, { onRadio() }, onAddTrack)
                }
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
                Text(listOf("♫", "◉", "▣", "☷", "✳")[index], color = Ink, fontSize = 20.sp); Spacer(Modifier.width(14.dp)); Text(label, color = Ink, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text("→", color = Muted)
            }
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
    onArtist: () -> Unit,
    hasNext: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 700.dp
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(Accent.copy(alpha = .045f), Carbon))))
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
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SwipeArtwork(track, Modifier.size(if (compact) 232.dp else 312.dp).shadow(24.dp, RoundedCornerShape(16.dp)), previous, next, hasNext)
            }
            SignalMark(Modifier.padding(top = 16.dp), playing)
            Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Ink, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).clickable { onArtist() })
                }
                IconButton(onClick = onLike) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (liked) "Unlike song" else "Like song", tint = if (liked) Accent else Ink) }
            }
            Spacer(Modifier.height(10.dp))
            Text(if (audioQuality.isBlank()) "Best available audio" else audioQuality,
                color = Ink, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
            key(track.id) {
                PlayerSeekControls(position, duration, seekable, playing, onSeek)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, if (shuffle) "Disable shuffle" else "Enable shuffle", tint = if (shuffle) Accent else Ink) }
                IconButton(onClick = previous) { Icon(Icons.Default.SkipPrevious, "Previous song", tint = Ink, modifier = Modifier.size(32.dp)) }
                CarbonPlayButton(onClick = toggle) {
                    if (buffering) PipesLoading(Modifier.size(40.dp))
                    else Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = next, enabled = hasNext) { Icon(Icons.Default.SkipNext, "Next song", tint = if (hasNext) Ink else Muted.copy(alpha = 0.35f), modifier = Modifier.size(32.dp)) }
                IconButton(onClick = onRepeat) { Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat: ${when (repeat) { Player.REPEAT_MODE_ONE -> "one song"; Player.REPEAT_MODE_ALL -> "all songs"; else -> "off" }}",
                    tint = if (repeat != Player.REPEAT_MODE_OFF) Accent else Ink) }
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
    Row(Modifier.fillMaxWidth().background(Carbon).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("Home", "Search", "Explore", "Library").forEach { target ->
            val selected = page == target || (target == "Library" && page == "Playlist")
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(target) }
                .padding(vertical = 12.dp).semantics { this.selected = selected }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(if (selected) 24.dp else 4.dp).height(4.dp).background(if (selected) Ink else Muted.copy(alpha = .4f), CircleShape))
                Text(target, color = if (selected) Ink else Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun DiscoveryHome(
    feed: com.nazcarbon.music.data.DiscoveryFeed, list: androidx.compose.foundation.lazy.LazyListState,
    artists: List<String>, current: MusicTrack?, liked: Set<String>,
    onPlay: (MusicTrack) -> Unit, onLike: (MusicTrack) -> Unit,
    onRadio: (MusicTrack) -> Unit, onSave: (MusicTrack) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf(feed.snapshot()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var exhausted by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    val latestCurrent by rememberUpdatedState(current)
    val latestArtists by rememberUpdatedState(artists)
    fun loadMore() {
        if (loading || exhausted) return
        loading = true
        error = null
        scope.launch {
            try {
                val batch = withContext(Dispatchers.IO) { feed.next(latestCurrent, latestArtists) }
                tracks = tracks + batch
                exhausted = batch.isEmpty()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Couldn't load more music. Tap to try again." }
            finally { loading = false }
        }
    }
    LaunchedEffect(current?.id, refreshToken) {
        while (loading) delay(100)
        loading = true
        exhausted = false
        error = null
        try {
            val batch = withContext(Dispatchers.IO) { feed.refresh(latestCurrent, latestArtists) }
            tracks = (batch + tracks).distinctBy { it.id }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Couldn't refresh discovery. Tap to try again." }
        finally { loading = false }
    }
    LaunchedEffect(list, tracks.size, loading, error, exhausted) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (tracks.isNotEmpty() && last >= 2 + (tracks.size - 1) / 2 - 3 && error == null) loadMore()
        }
    }
    LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "discovery-list-title") {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                SignalMark()
                Text("Find your\nnext feeling.", color = Ink, fontSize = 40.sp, lineHeight = 44.sp,
                    fontWeight = FontWeight.Light, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 16.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(current?.let { "TUNED TO ${it.artist.uppercase()}" } ?: "A NEW FREQUENCY", color = Muted, fontSize = 10.sp,
                        letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = { refreshToken++ }, enabled = !loading) { Text("Refresh ↗", color = Ink) }
                }
            }
        }
        if (tracks.isNotEmpty()) item(key = "discovery-feature") {
            val track = tracks.first()
            val add = LocalAddToQueue.current
            val remove = LocalRemoveFromQueue.current
            QueueGesture(track, { onPlay(track) }, { add(track) }, { remove(track) }, { onLike(track) }, { onRadio(track) }, { onSave(track) }) {
                Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).fillMaxWidth().aspectRatio(1.05f)
                    .shadow(16.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Raised)) {
                    ArtworkImage(track.thumbnailUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                        Text("01  /  IN YOUR ORBIT", color = Ink, fontSize = 10.sp, letterSpacing = 2.sp)
                        Text(track.title, color = Ink, fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                        Text(track.artist, color = Ink.copy(alpha = .7f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        items(tracks.drop(1).chunked(2), key = { "discovery-pair:${it.first().id}" }) { pair ->
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { track ->
                    val add = LocalAddToQueue.current
                    val remove = LocalRemoveFromQueue.current
                    Box(Modifier.weight(1f)) {
                        QueueGesture(track, { onPlay(track) }, { add(track) }, { remove(track) }, { onLike(track) }, { onRadio(track) }, { onSave(track) }) {
                            Column {
                                ArtworkImage(track.thumbnailUrl, null, Modifier.fillMaxWidth().aspectRatio(1f).shadow(6.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Raised), contentScale = ContentScale.Crop)
                                Text(track.title, color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                                Text(track.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item(key = "discovery-footer") {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                when {
                    loading -> PipesLoading(Modifier.size(36.dp))
                    error != null -> TextButton(onClick = { loadMore() }) { Text(error!!, color = Accent) }
                    exhausted -> TextButton(onClick = { exhausted = false; loadMore() }) { Text("Explore another wave", color = Accent) }
                }
            }
        }
    }
}

@Composable private fun RecommendationRegionPicker() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("pipes", android.content.Context.MODE_PRIVATE) }
    var region by remember { mutableStateOf(prefs.getString("recommendation_region", "AUTO") ?: "AUTO") }
    var expanded by remember { mutableStateOf(false) }
    val countries = remember { java.util.Locale.getISOCountries().map { it to java.util.Locale("", it).displayCountry }.sortedBy { it.second } }
    Box(Modifier.padding(horizontal = 24.dp)) {
        TextButton(onClick = { expanded = true }) {
            Text("Region · " + (countries.firstOrNull { it.first == region }?.second ?: "Auto · current song"))
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            DropdownMenuItem(text = { Text("Auto · current song") }, onClick = {
                region = "AUTO"; prefs.edit().putString("recommendation_region", region).apply(); expanded = false
            })
            countries.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    region = code; prefs.edit().putString("recommendation_region", code).apply(); expanded = false
                })
            }
        }
    }
}
