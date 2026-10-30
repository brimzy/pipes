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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import com.nazcarbon.music.data.YouTubeLinks
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
import com.nazcarbon.music.data.PlaylistEdits
import com.nazcarbon.music.data.PlaylistArtworkFiles
import com.nazcarbon.music.data.PlaybackQueueRequests
import com.nazcarbon.music.ui.theme.PipesTheme
import com.nazcarbon.music.ui.theme.PipesPalette
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

private val Carbon = PipesPalette.Background
private val Panel = PipesPalette.Card
private val Raised = PipesPalette.Raised
private val Accent = PipesPalette.Accent
private val Ink = PipesPalette.Text
private val Muted = PipesPalette.TextSoft

internal val LocalRemoveFromQueue = staticCompositionLocalOf<(MusicTrack) -> Unit> { {} }

internal val LocalPlayNext = staticCompositionLocalOf<(MusicTrack) -> Unit> { {} }

internal val LocalAddToQueue = staticCompositionLocalOf<(MusicTrack) -> Unit> { {} }

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
                val tutorial = remember { AppTutorialState(prefs) }
                val updates = rememberAppUpdates(this@MainActivity)
                if (!tutorial.showingDialog) AppUpdateDialog(this@MainActivity, updates)
                var mediaPlayer by remember { mutableStateOf<MediaController?>(null) }
                val discoveryFeed = remember { com.nazcarbon.music.data.DiscoveryFeed(source) }
                val exploreFeed = remember { com.nazcarbon.music.data.ExploreFeed(source::searchSongs) }
                var exploreTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
                var exploreSelection by remember { mutableStateOf("After hours") }
                val exploreScroll = androidx.compose.foundation.lazy.rememberLazyListState()
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
                var showNewPlaylistDialog by remember { mutableStateOf(false) }
                var artworkTarget by rememberSaveable { mutableStateOf<String?>(null) }
                var savingArtwork by remember { mutableStateOf(false) }
                var selectedTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
                var collectionAdded by remember { mutableStateOf<CollectionAdded?>(null) }
                var downloadTrack by remember { mutableStateOf<MusicTrack?>(null) }
                LaunchedEffect(page) { selectedTracks = emptyList() }
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

                BackHandler(selectedTracks.isNotEmpty()) { selectedTracks = emptyList() }

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
                        val youtube = YouTubeLinks.parse(text)
                        if (youtube != null) {
                            withContext(Dispatchers.IO) { source.searchYouTubeLink(youtube) }
                        } else {
                            val terms = withContext(Dispatchers.IO) {
                                if (SpotifyLinks.isSpotifyLink(text)) SpotifyLinks.searchQuery(text) else text
                            }
                            saveHistory(com.nazcarbon.music.data.SearchHistory.add(searchHistory, terms))
                            source.searchMusic(terms)
                        }
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
                                playlists.map { if (it.name.equals(playlist.name, ignoreCase = true)) playlist.copy(artworkUri = it.artworkUri) else it }
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

                fun createCollection(name: String, album: Boolean, tracks: List<MusicTrack> = emptyList()): String? = runCatching {
                    val clean = PlaylistEdits.name(name, playlists)
                    val collection = PlaylistEdits.add(MusicPlaylist(clean, emptyList(), isAlbum = album), tracks)
                    playlists = playlists + collection
                    PlaylistStore.save(prefs, playlists)
                    showNewPlaylistDialog = false
                    tracksToSave = emptyList()
                    selectedTracks = emptyList()
                    if (collection.tracks.isNotEmpty()) collectionAdded = CollectionAdded(collection.tracks, clean)
                    notice = "${if (album) "Album" else "Playlist"} created · $clean"
                }.exceptionOrNull()?.message

                fun renameCollection(collection: MusicPlaylist, name: String, album: Boolean): String? = runCatching {
                    val clean = PlaylistEdits.name(name, playlists, replacing = collection.name)
                    playlists = playlists.map { if (it.name == collection.name) it.copy(name = clean, isAlbum = album) else it }
                    PlaylistStore.save(prefs, playlists)
                    selectedPlaylist = clean
                }.exceptionOrNull()?.message

                fun removeCollectionTracks(ids: Set<String>) {
                    playlists = playlists.map { if (it.name == selectedPlaylist) PlaylistEdits.remove(it, ids) else it }
                    PlaylistStore.save(prefs, playlists)
                    selectedTracks = emptyList()
                    notice = "Removed ${ids.size} ${if (ids.size == 1) "song" else "songs"}"
                }

                fun discardUnusedArtwork(uri: String) {
                    if (uri.isNotBlank() && playlists.none { it.artworkUri == uri }) lifecycleScope.launch(Dispatchers.IO) {
                        PlaylistArtworkFiles.remove(playlistArtworkDirectory(this@MainActivity), uri)
                    }
                }

                fun setCollectionArtwork(name: String, uri: String): Boolean {
                    val previous = playlists.firstOrNull { it.name == name } ?: return false
                    val updated = playlists.map { if (it.name == name) it.copy(artworkUri = uri) else it }
                    PlaylistStore.save(prefs, updated)
                    playlists = updated
                    discardUnusedArtwork(previous.artworkUri)
                    notice = if (uri.isBlank()) "Default artwork restored" else "Collection artwork updated"
                    return true
                }

                fun removePlaylist(playlist: MusicPlaylist) {
                    playlists = playlists.filterNot { it.name == playlist.name }
                    PlaylistStore.save(prefs, playlists)
                    discardUnusedArtwork(playlist.artworkUri)
                    selectedPlaylist = null
                    page = "Library"
                    notice = "${if (playlist.isAlbum) "Album" else "Playlist"} deleted"
                }

                suspend fun resolveForPlayback(track: MusicTrack): MusicTrack = withContext(Dispatchers.IO) {
                    source.resolveForPlayback(track)
                }

                fun openPlayer() {
                    previousPage = com.nazcarbon.music.data.PlayerNavigation.origin(page, previousPage)
                    page = "Now playing"
                }

                fun play(track: MusicTrack, keepQueue: Boolean = false, radio: Boolean = false): Int {
                    openPlayer()
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

                fun closePlayback() {
                    // Invalidate in-flight stream and queue requests before clearing the session.
                    playbackRequest += 1
                    playJob?.cancel()
                    playJob = null
                    queueJob?.cancel()
                    queueJob = null
                    mediaPlayer?.stop()
                    mediaPlayer?.clearMediaItems()
                    current = null
                    queueTracks = emptyList()
                    queueIndex = 0
                    radioSeed = null
                    radioLoading = false
                    isPlaying = false
                    buffering = false
                    position = 0L
                    duration = 0L
                    seekable = false
                    audioQuality = ""
                    error = null
                    if (page == "Now playing" || page == "Up next") page = previousPage
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

                fun playNext(track: MusicTrack) {
                    if (mediaPlayer?.currentMediaItem == null) { play(track); return }
                    lifecycleScope.launch {
                        try {
                            val player = mediaPlayer ?: return@launch
                            val session = player.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY)
                                ?: return@launch
                            val existing = com.nazcarbon.music.data.PlayNextQueue.existingUpcomingIndex(
                                (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
                                player.currentMediaItemIndex, track.id)
                            val resolved = if (existing != null) track else resolveForPlayback(track)
                            if (player.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY) != session) {
                                notice = "Playback changed. Choose Play next again."
                                return@launch
                            }
                            if (existing == null) knownTracks[resolved.id] = resolved
                            val requestId = PlaybackQueueRequests.register(session, listOf(resolved))
                            val result = player.sendCustomCommand(SessionCommand("pipes.play_next", Bundle.EMPTY), Bundle().apply {
                                putString("queue_id", requestId)
                                putString("session", session)
                            })
                            val success = withContext(Dispatchers.IO) { result.get().resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS }
                            notice = if (success) "Playing next · ${track.title}" else "Playback changed. Choose Play next again."
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { notice = "Couldn't play this song next. Try again." }
                    }
                }

                fun startRadio(track: MusicTrack) {
                    if (track.pageUrl.startsWith("content://")) { notice = "Radio is available for online songs."; return }
                    queueJob?.cancel()
                    radioSeed = track
                    error = null
                    openPlayer()
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

                fun choosePlaylist(track: MusicTrack) { tracksToSave = listOf(track) }
                fun chooseTracksForPlaylist(tracks: List<MusicTrack>) { tracksToSave = tracks.distinctBy { it.id } }
                fun addTracksToPlaylist(name: String) {
                    val playlist = playlists.firstOrNull { it.name == name } ?: return
                    val updated = PlaylistEdits.add(playlist, tracksToSave)
                    val additions = updated.tracks.drop(playlist.tracks.size)
                    if (additions.isEmpty()) notice = "Already in $name"
                    else {
                        playlists = playlists.map { if (it.name == name) updated else it }
                        PlaylistStore.save(prefs, playlists)
                        collectionAdded = CollectionAdded(additions, name)
                        notice = "Added ${additions.size} ${if (additions.size == 1) "song" else "songs"} to $name"
                    }
                    tracksToSave = emptyList()
                    selectedTracks = emptyList()
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
                        if (offset < 0) {
                            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0)
                        }
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

                fun chooseDownload(track: MusicTrack) {
                    if (track.pageUrl.startsWith("content://") || track.pageUrl.startsWith("file://") || track.id.startsWith("download:")) {
                        notice = "This song is already on your device"
                        return
                    }
                    if (downloadResolving) return
                    downloadDialogError = null
                    downloadTrack = track
                }

                fun downloadSong(category: String) {
                    val track = downloadTrack ?: return
                    if (downloadResolving) return
                    downloadResolving = true
                    downloadDialogError = null
                    searchScope.launch {
                        try {
                            val existing = withContext(Dispatchers.IO) {
                                downloads.firstOrNull { it.sourceTrackId == track.id &&
                                    AudioDownloads.status(downloadManager, it.id)?.status in setOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_SUCCESSFUL) }
                            }
                            if (existing != null) {
                                notice = "Already downloaded or queued · ${track.title}"
                                downloadTrack = null
                            } else {
                                val audio = withContext(Dispatchers.IO) { source.resolveForDownload(track) }
                                val download = AudioDownloads.enqueueTrack(this@MainActivity, downloadManager, track, audio, category)
                                downloads = listOf(download) + downloads
                                AudioDownloads.save(prefs, downloads)
                                downloadTrack = null
                                notice = "Download started · ${track.title}"
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            downloadDialogError = failure.message ?: "Couldn't prepare this song. Try again."
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

                val openPlaylistArtwork = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    val target = artworkTarget
                    artworkTarget = null
                    if (uri != null && target != null) lifecycleScope.launch {
                        savingArtwork = true
                        var imported = ""
                        try {
                            withContext(Dispatchers.IO) { imported = importPlaylistArtwork(this@MainActivity, uri) }
                            if (setCollectionArtwork(target, imported)) imported = ""
                            else notice = "That playlist is no longer available"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: "Couldn't save playlist artwork. Try another image or GIF."
                        } finally {
                            if (imported.isNotBlank()) withContext(NonCancellable + Dispatchers.IO) {
                                PlaylistArtworkFiles.remove(playlistArtworkDirectory(this@MainActivity), imported)
                            }
                            savingArtwork = false
                        }
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
                    }
                }

                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(error, notice) {
                    (error ?: notice)?.let { snackbar.showSnackbar(it) }
                }
                CompositionLocalProvider(
                    LocalDownloadTrack provides ::chooseDownload,
                    LocalTrackSelection provides TrackSelection(selectedTracks) { track ->
                        selectedTracks = if (selectedTracks.any { it.id == track.id }) selectedTracks.filterNot { it.id == track.id } else selectedTracks + track
                    },
                    LocalPlayNext provides ::playNext, LocalAddToQueue provides ::addToQueue, LocalRemoveFromQueue provides { track ->
                    mediaPlayer?.let { player ->
                        val index = ((player.currentMediaItemIndex + 1) until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == track.id }
                        if (index != null) { player.removeMediaItem(index); notice = "Removed from queue · ${track.title}" }
                        else notice = "This song is not in the upcoming queue"
                    }
                }) {
                Surface(Modifier.fillMaxSize(), color = Carbon) {
                    Box(Modifier.fillMaxSize()) {
                    PlayerBackdrop(current?.thumbnailUrl.orEmpty(), Modifier.matchParentSize())
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                        Column(Modifier.weight(1f)) {
                            AnimatedVisibility(page !in listOf("Now playing", "Settings"),
                                enter = fadeIn(tween(220)) + expandVertically(tween(280, easing = FastOutSlowInEasing)),
                                exit = fadeOut(tween(120)) + shrinkVertically(tween(240, easing = FastOutSlowInEasing))) {
                                Header(query, ::search, page, { openAudio.launch(arrayOf("audio/*")) }, { settingsOrigin = page; page = "Settings" })
                            }
                            if (tutorial.practicing) TutorialPracticeBar(tutorial, current != null)
                            AnimatedContent(targetState = page, modifier = Modifier.weight(1f),
                                transitionSpec = {
                                    (fadeIn(tween(260, delayMillis = 50)) + slideInVertically(tween(320, easing = LinearOutSlowInEasing)) { it / 24 })
                                        .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(220)) { -it / 48 })
                                        .using(null)
                                }, label = "page transition") { visiblePage ->
                            if (visiblePage == "Now playing" && current != null) {
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
                                    { page = previousPage }, ::closePlayback, { choosePlaylist(current!!) },
                                    { findArtists(current!!.artist) },
                                    mediaPlayer?.hasNextMediaItem() == true,
                                    { chooseDownload(current!!) },
                                )
                            } else when (visiblePage) {
                                "Settings" -> AppSettingsScreen(this@MainActivity, updates, { page = settingsOrigin }, tutorial.progress.completed, tutorial::open) { RecommendationRegionPicker() }
                                "Library" -> Library(
                                    likedIds.mapNotNull { knownTracks[it] },
                                    likedIds,
                                    playlists,
                                    current,
                                isPlaying,
                                { playlist -> selectedPlaylist = playlist.name; page = "Playlist" },
                                { track -> playSequence(track, likedIds.mapNotNull { knownTracks[it] }) },
                                ::toggleLike,
                                ::choosePlaylist,
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
                                        { chooseTracksForPlaylist(playlist.tracks) },
                                        { ids -> removeCollectionTracks(ids) },
                                        { name, album -> renameCollection(playlist, name, album) },
                                        savingArtwork,
                                        { artworkTarget = playlist.name; openPlaylistArtwork.launch(arrayOf("image/*")) },
                                        { setCollectionArtwork(playlist.name, "") },
                                    ) else Library(likedIds.mapNotNull { knownTracks[it] }, likedIds, playlists, current, isPlaying, { selectedPlaylist = it.name; page = "Playlist" }, ::play, ::toggleLike, ::choosePlaylist, downloads, downloadCategories, downloadStatuses, { downloadDialogError = null; showDownloadDialog = true }, { showCategoryDialog = true }, ::playDownloaded, ::removeAudioDownload, { showNewPlaylistDialog = true }, ::startRadio)
                                }
                                "Explore" -> ExploreScreen(exploreFeed, current, exploreSelection, { exploreSelection = it }, exploreScroll, ::search, ::play, ::toggleLike, ::startRadio, ::choosePlaylist, { exploreTracks = it })
                                else -> DiscoveryHome(discoveryFeed, discoveryScroll, likedArtists.toList(), current, likedIds, ::play, ::toggleLike, ::startRadio, ::choosePlaylist, isPlaying, ::openPlayer)
                            }
                            }
                        }
                        AnimatedVisibility(selectedTracks.isNotEmpty()) {
                            SelectionBar(selectedTracks.size, onAll = {
                                val visible = when (page) {
                                    "Search" -> results
                                    "Playlist" -> playlists.firstOrNull { it.name == selectedPlaylist }?.tracks.orEmpty()
                                    "Release" -> releaseTracks
                                    "Library" -> likedIds.mapNotNull { knownTracks[it] }
                                    "Up next" -> queueTracks
                                    "Home" -> discoveryFeed.snapshot()
                                    "Explore" -> exploreTracks
                                    else -> selectedTracks
                                }
                                selectedTracks = (selectedTracks + visible).distinctBy { it.id }
                            }, onAdd = { chooseTracksForPlaylist(selectedTracks) }, onCancel = { selectedTracks = emptyList() },
                                onRemove = if (page == "Playlist") ({ removeCollectionTracks(selectedTracks.mapTo(mutableSetOf()) { it.id }) }) else null)
                        }
                        AnimatedVisibility(page != "Now playing", enter = fadeIn(tween(200)) + expandVertically(tween(280)),
                            exit = fadeOut(tween(120)) + shrinkVertically(tween(240))) {
                            Column {
                                current?.let { track ->
                                    CurrentSongBar(track, isPlaying, position, duration, seekable, buffering,
                                        { mediaPlayer?.seekTo(it) },
                                        ::openPlayer,
                                        { mediaPlayer?.let { player -> if (player.isPlaying) player.pause() else player.play() } },
                                        ::closePlayback, { skipTrack(-1) }, { skipTrack(1) }, mediaPlayer?.hasNextMediaItem() == true)
                                }
                                NavigationDock(page) { if (it == "Search") { if (page != "Search") search("") } else page = it }
                            }
                        }
                    }
                    CollectionAddAnimation(collectionAdded, { collectionAdded = null },
                        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = if (page == "Now playing") 80.dp else DockClearance + 90.dp))
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (page == "Now playing") 8.dp else DockClearance))
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
                downloadTrack?.let { track ->
                    DownloadTrackDialog(track, downloadCategories, downloadResolving, downloadDialogError,
                        onDismiss = { if (!downloadResolving) { downloadTrack = null; downloadDialogError = null } }, onDownload = ::downloadSong)
                }
                if (showNewPlaylistDialog) CollectionNameDialog(
                    onDismiss = { showNewPlaylistDialog = false },
                    onSave = { name, album -> createCollection(name, album) },
                )
                if (tracksToSave.isNotEmpty()) CollectionPicker(
                    tracksToSave, playlists, onDismiss = { tracksToSave = emptyList() }, onChoose = ::addTracksToPlaylist,
                    onCreate = { name, album -> createCollection(name, album, tracksToSave) },
                )
                AppTutorial(tutorial) { destination ->
                    selectedTracks = emptyList()
                    when (destination) {
                        TutorialDestination.SEARCH -> search("")
                        TutorialDestination.PLAYER -> if (current != null) openPlayer() else {
                            search("")
                            notice = "Choose a song, or open a local file with the folder button, to try the player."
                        }
                        TutorialDestination.QUEUE -> if (current != null) {
                            previousPage = com.nazcarbon.music.data.PlayerNavigation.origin(page, previousPage)
                            page = "Up next"
                        } else {
                            search("")
                            notice = "Play a song first, then open Queue in the full player."
                        }
                        TutorialDestination.LIBRARY -> page = "Library"
                        TutorialDestination.SETTINGS -> { if (page != "Settings") settingsOrigin = com.nazcarbon.music.data.PlayerNavigation.origin(page, previousPage); page = "Settings" }
                    }
                }
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

@Composable private fun Header(query: String, onSearch: (String) -> Unit, page: String, onOpenAudio: () -> Unit, onSettings: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val searchVisibility = remember { MutableTransitionState(false) }
    searchVisibility.targetState = page == "Search" || page == "Artist Search"
    LaunchedEffect(page) {
        if (page == "Search") {
            snapshotFlow { searchVisibility.isIdle && searchVisibility.currentState }.first { it }
            focus.requestFocus()
            keyboard?.show()
        }
    }
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            PipesWordmark()
            Spacer(Modifier.weight(1f))
            if (page == "Home") IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Search, "Search music", tint = PipesPalette.Cyan) }
            IconButton(onClick = { keyboard?.hide(); onSettings() }) { Icon(Icons.Default.Settings, "App settings", tint = Muted) }
            IconButton(onClick = onOpenAudio) { Icon(Icons.Default.FolderOpen, "Open local audio, including lossless FLAC and WAV", tint = Muted) }
        }
        AnimatedVisibility(visibleState = searchVisibility,
            enter = expandVertically(tween(280, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(220, 60)),
            exit = shrinkVertically(tween(240, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(120))) {
        Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRequester(focus),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch(query) }),
            trailingIcon = { Row {
                if (query.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Close, "Clear search") }
            } },
            placeholder = { Text("Search music", color = Muted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Ink) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink, focusedBorderColor = Accent, unfocusedBorderColor = Color.Transparent, cursorColor = Accent, focusedContainerColor = Panel, unfocusedContainerColor = Panel),
        )
        }
        }

    }
}

@Composable private fun SearchHistoryPanel(history: List<String>, onSearch: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent searches", color = PipesPalette.Heading, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear all") }
        }
        if (history.isEmpty()) Text("Find your next favorite. Your searches will appear here.", color = Muted, modifier = Modifier.padding(PipesSpace.Page))
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
        Text(if (query.isBlank()) "Find a sound" else "Search results", color = PipesPalette.Heading, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
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
            error != null -> Column(Modifier.padding(PipesSpace.Page)) {
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
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 48.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(track.thumbnailUrl, null, Modifier.size(56.dp).artworkSurface(RoundedCornerShape(8.dp), 2.dp), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = if (active) PipesPalette.Cyan else PipesPalette.SongTitle, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(track.artist, color = Muted, maxLines = 1, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp).clickable(enabled = onArtist != null && !LocalTrackSelection.current.active) { onArtist?.invoke(track.artist) })
            }
            if (active) Icon(Icons.Default.GraphicEq, "Now playing", tint = PipesPalette.Cyan, modifier = Modifier.padding(horizontal = 8.dp).size(20.dp))
            IconButton(onClick = { onLike(track) }, enabled = !LocalTrackSelection.current.active) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (liked) "Unlike song" else "Like song", tint = if (liked) PipesPalette.Favorite else Muted, modifier = Modifier.size(20.dp)) }
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
    val visibleDownloads = downloads.filter { selectedCategory == "All" || it.category.equals(selectedCategory, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = PipesSpace.Page)) {
            item(key = "library-header") {
                Column(Modifier.padding(PipesSpace.Page)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Your library.", color = PipesPalette.Heading, fontSize = 32.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = onCreatePlaylist) { Icon(Icons.Default.PlaylistAdd, "Create playlist or album", tint = Ink) }
                    }
                    Text("${tracks.size} liked songs · ${playlists.count { !it.isAlbum }} playlists · ${playlists.count { it.isAlbum }} albums", color = Muted,
                        fontSize = 12.sp, modifier = Modifier.padding(top = PipesSpace.ArtworkText))
                }
            }
            item(key = "library-tools") {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = onAddDownload, modifier = Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Panel, contentColor = Ink)) {
                            Icon(Icons.Default.MusicNote, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download link")
                        }
                        FilledTonalButton(onClick = onManageCategories, modifier = Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Panel, contentColor = Ink)) {
                            Icon(Icons.Default.FolderOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Categories", maxLines = 1)
                        }
                    }
                    Text(
                        "Save audio and organize it into categories.",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = PipesSpace.Page, vertical = PipesSpace.ArtworkText),
                    )
                }
            }
            item {
                Text("DOWNLOADS · ${downloads.size}", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 24.dp, top = PipesSpace.Section, bottom = PipesSpace.ArtworkText))
            }
            if (categories.size > 1) item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            border = null,
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
                Text("PLAYLISTS & ALBUMS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 24.dp, top = PipesSpace.Section, bottom = PipesSpace.ArtworkText))
            }
            if (playlists.isEmpty()) item {
                Text("Create a playlist or custom album to collect your favorites.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            } else items(playlists, key = { "playlist:${it.name}" }) { playlist ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenPlaylist(playlist) }.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistArtwork(playlist, Modifier.size(56.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, color = PipesPalette.SongTitle, fontWeight = FontWeight.Bold)
                        Text("${if (playlist.isAlbum) "Album" else "Playlist"} · ${playlist.tracks.size} songs", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.PlayArrow, null, tint = Ink)
                }
            }
            item {
                Text("LIKED SONGS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 24.dp, top = PipesSpace.Section, bottom = PipesSpace.ArtworkText))
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
                Text(download.title, color = PipesPalette.SongTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    onCopy: () -> Unit,
    onRemoveTracks: (Set<String>) -> Unit,
    onRename: (String, Boolean) -> String?,
    artworkBusy: Boolean,
    onChooseArtwork: () -> Unit,
    onRemoveArtwork: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(playlist.name, color = PipesPalette.Heading, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { editing = true }, enabled = !artworkBusy) { Icon(Icons.Default.Edit, "Edit collection", tint = Ink) }
            IconButton(onClick = { deleting = true }, enabled = !artworkBusy) { Icon(Icons.Default.Delete, "Delete collection", tint = Ink) }
        }
        Text("${if (playlist.isAlbum) "Album" else "Playlist"} · ${playlist.tracks.size} songs · on this device", color = Muted, modifier = Modifier.padding(horizontal = 24.dp))
        PlaylistArtworkEditor(playlist, artworkBusy, onChooseArtwork, onRemoveArtwork)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            TextButton(onClick = { playlist.tracks.firstOrNull()?.let(onPlay) }, enabled = playlist.tracks.isNotEmpty()) { Text("Play all") }
            TextButton(onClick = onCopy, enabled = playlist.tracks.isNotEmpty()) { Text("Add all to…") }
            val selection = LocalTrackSelection.current
            TextButton(onClick = { playlist.tracks.firstOrNull()?.let(selection.toggle) }, enabled = playlist.tracks.isNotEmpty() && !selection.active) { Text("Select songs") }
        }
        if (playlist.sourceTotal > playlist.tracks.size + playlist.sourceSkipped) {
            Text("Spotify exposes ${playlist.tracks.size} of ${playlist.sourceTotal} songs without sign-in. This is a partial import.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
        }
        if (playlist.sourceSkipped > 0) {
            Text("${playlist.sourceSkipped} unavailable or non-music items skipped", color = Muted,
                fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
        if (playlist.tracks.isEmpty()) {
            Text("Add the playing song with +, or choose songs from search or an album. Hold a song to select several.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(PipesSpace.Page))
        } else LazyColumn {
            items(playlist.tracks, key = { it.id }) { track ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        TrackRow(track, current?.id == track.id && playing, false, onPlay, {}, onRadio, onSave)
                    }
                    IconButton(onClick = { onRemoveTracks(setOf(track.id)) }) {
                        Icon(Icons.Default.Close, "Remove ${track.title} from ${playlist.name}", tint = Muted)
                    }
                }
            }
        }
    }
    if (editing) CollectionNameDialog(playlist, onDismiss = { editing = false }, onSave = { name, album ->
        onRename(name, album).also { if (it == null) editing = false }
    })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text("Delete ${playlist.name}?") },
        text = { Text("This removes the collection from your library. Your downloaded files and other collections stay saved.") },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
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
        Text("Up next", color = PipesPalette.Heading, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
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
            else Text("Autoplay will fill this queue with related songs as you listen.", color = Muted, modifier = Modifier.padding(PipesSpace.Page))
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
        Text("Artists", color = PipesPalette.Heading, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text("Discography search · $query", color = Muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        when {
            loading -> PipesLoading(modifier = Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
            error != null -> Column(Modifier.padding(PipesSpace.Page)) {
                Text(error, color = Muted)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            artists.isEmpty() -> Text("No artist pages found. Try the artist's full name.", color = Muted, modifier = Modifier.padding(PipesSpace.Page))
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)) {
                items(artists, key = { it.url }) { artist ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpenArtist(artist) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkImage(artist.thumbnailUrl, null, Modifier.size(56.dp).artworkSurface(CircleShape, 2.dp), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(artist.name, color = PipesPalette.SongTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(
                        discography.artist.thumbnailUrl, null,
                        Modifier.size(80.dp).artworkSurface(CircleShape, 2.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(discography.artist.name, color = PipesPalette.Heading, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Artist discography", color = Muted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    ArtistArtwork(releases.firstOrNull()?.thumbnailUrl.orEmpty(), Modifier.size(80.dp))
                }
            }
            item(key = "artist-radio") {
                TextButton(onClick = onRadio, modifier = Modifier.padding(start = 12.dp)) {
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
            error != null -> item(key = "artist-error") { Text(error, color = Muted, modifier = Modifier.padding(PipesSpace.Page)) }
            discography == null || releases.isEmpty() -> item(key = "artist-empty") { Text("No albums or singles were found for this artist.", color = Muted, modifier = Modifier.padding(PipesSpace.Page)) }
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
                            horizontalArrangement = Arrangement.spacedBy(PipesSpace.Gap),
                        ) {
                            itemsIndexed(selectedReleases, key = { _, release -> "${if (showingSingles) "single" else "album"}:${release.url}" }) { index, release ->
                                ArtistReleaseCard(release, index, isSingle = showingSingles, onOpen = onOpenRelease)
                            }
                        }
                    }
                }
                item(key = "chronology-title") {
                    Text("Chronological discography · newest first", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp))
                }
                items(chronologicalReleases, key = { "list:${it.url}" }) { release -> ArtistReleaseRow(release, onOpenRelease) }
            }
        }
    }
}

private fun isSingleRelease(release: ArtistRelease): Boolean =
    release.kind.contains("single", true) || release.kind.contains("ep", true) || release.trackCount == 1L

@Composable private fun ArtistArtwork(imageUrl: String, modifier: Modifier = Modifier) {
    ArtworkImage(imageUrl, null, modifier.artworkSurface(RoundedCornerShape(8.dp), 4.dp), contentScale = ContentScale.Crop)
}

@Composable private fun ArtistReleaseCard(release: ArtistRelease, index: Int, isSingle: Boolean, onOpen: (ArtistRelease) -> Unit) {
    Column(Modifier.width(if (isSingle) 176.dp else 224.dp).clickable { onOpen(release) }.padding(bottom = 16.dp)) {
        ArtworkImage(release.thumbnailUrl, null, Modifier.fillMaxWidth().aspectRatio(1f).artworkSurface(RoundedCornerShape(8.dp), 4.dp), contentScale = ContentScale.Crop)
        Text("${(index + 1).toString().padStart(2, '0')}  /  ${release.kind.uppercase()}", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 16.dp))
        Text(release.title, color = PipesPalette.SongTitle, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(listOfNotNull(release.year?.toString(), release.trackCount.takeIf { it > 0 }?.let { "$it tracks" }).joinToString(" · "), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable private fun ArtistReleaseRow(release: ArtistRelease, onOpen: (ArtistRelease) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(release) }.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(release.thumbnailUrl, null, Modifier.size(56.dp).artworkSurface(RoundedCornerShape(8.dp), 2.dp), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(release.title, color = PipesPalette.SongTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            ArtworkImage(release.thumbnailUrl, null, Modifier.size(72.dp).artworkSurface(RoundedCornerShape(8.dp), 2.dp), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(release.title, color = PipesPalette.SongTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            error != null -> Text(error, color = Muted, modifier = Modifier.padding(PipesSpace.Page))
            tracks.isEmpty() -> Text("No tracks were found on this release.", color = Muted, modifier = Modifier.padding(PipesSpace.Page))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(track, false, false, onPlayTrack, {}, { onRadio() }, onAddTrack)
                }
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
    onStop: () -> Unit,
    onSave: () -> Unit,
    onArtist: () -> Unit,
    hasNext: Boolean,
    onDownload: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun feedback(action: () -> Unit) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        action()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        PlayerStars(playing, Modifier.matchParentSize())
        val compact = maxHeight < 700.dp
        Column(
            Modifier.fillMaxSize().padding(horizontal = PipesSpace.Page, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { feedback(onClose) }) { Icon(Icons.Default.KeyboardArrowDown, "Minimize player", tint = Ink) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                IconButton(onClick = { feedback(onStop) }) { Icon(Icons.Default.Close, "Stop playback and close player", tint = Ink) }
            }
            // Cover size follows the remaining space; metadata and controls stay visible.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()
                .padding(vertical = if (compact) 8.dp else 12.dp), contentAlignment = Alignment.Center) {
                val artworkSize = minOf(maxWidth, maxHeight, 380.dp).coerceAtLeast(0.dp)
                if (artworkSize > 0.dp) SwipeArtwork(track, Modifier.size(artworkSize), previous, next, hasNext)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = PipesPalette.SongTitle, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).clickable { onArtist() })
                }
                IconButton(onClick = { feedback(onLike) }) { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (liked) "Unlike song" else "Like song", tint = if (liked) PipesPalette.Favorite else Ink) }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(if (audioQuality.isBlank()) "Best available audio" else audioQuality,
                        color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
                    key(track.id) {
                        PlayerSeekControls(position, duration, seekable, playing, onSeek)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { feedback(onShuffle) }) { Icon(Icons.Default.Shuffle, if (shuffle) "Disable shuffle" else "Enable shuffle", tint = if (shuffle) PipesPalette.Cyan else Ink) }
                    IconButton(onClick = { feedback(previous) }) { Icon(Icons.Default.SkipPrevious, "Previous song", tint = Ink, modifier = Modifier.size(24.dp)) }
                    CarbonPlayButton(playing, buffering, toggle)
                    IconButton(onClick = { feedback(next) }, enabled = hasNext) { Icon(Icons.Default.SkipNext, "Next song", tint = if (hasNext) Ink else Muted.copy(alpha = 0.35f), modifier = Modifier.size(24.dp)) }
                    IconButton(onClick = { feedback(onRepeat) }) { Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat: ${when (repeat) { Player.REPEAT_MODE_ONE -> "one song"; Player.REPEAT_MODE_ALL -> "all songs"; else -> "off" }}",
                        tint = if (repeat != Player.REPEAT_MODE_OFF) PipesPalette.Cyan else Ink) }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Ink.copy(alpha = .08f))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { feedback(onRadio) }, modifier = Modifier.weight(1f), enabled = !radioLoading && !track.pageUrl.startsWith("content://")) {
                        Icon(Icons.Default.Radio, null, Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)); Text("Radio")
                    }
                    TextButton(onClick = { feedback(onQueue) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.QueueMusic, null, Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)); Text("Queue · $queueCount")
                    }
                    IconButton(onClick = { feedback(onDownload) }) {
                        Icon(Icons.Default.Download, "Download this song", tint = Ink)
                    }
                    IconButton(onClick = { feedback(onSave) }) {
                        Icon(Icons.Default.PlaylistAdd, "Add to playlist or album", tint = Muted, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun playbackTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun DiscoveryHome(
    feed: com.nazcarbon.music.data.DiscoveryFeed, list: androidx.compose.foundation.lazy.LazyListState,
    artists: List<String>, current: MusicTrack?, liked: Set<String>,
    onPlay: (MusicTrack) -> Unit, onLike: (MusicTrack) -> Unit,
    onRadio: (MusicTrack) -> Unit, onSave: (MusicTrack) -> Unit,
    playing: Boolean, onOpenPlayer: () -> Unit,
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
    LaunchedEffect(current?.id, artists, refreshToken) {
        while (loading) delay(100)
        loading = true
        exhausted = false
        error = null
        try {
            withContext(Dispatchers.IO) { feed.refresh(latestCurrent, latestArtists) }
            tracks = feed.snapshot()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Couldn't refresh discovery. Tap to try again." }
        finally { loading = false }
    }
    LaunchedEffect(list, tracks.size, loading, error, exhausted) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (tracks.isNotEmpty() && last >= 2 + (tracks.size - 1) / 2 - 3 && error == null) loadMore()
        }
    }
    LazyColumn(state = list, contentPadding = PaddingValues(bottom = PipesSpace.Page)) {
        item(key = "discovery-list-title") {
            Column(Modifier.fillMaxWidth().padding(PipesSpace.Page)) {
                Text(buildAnnotatedString {
                    append("Find your ")
                    withStyle(SpanStyle(color = PipesPalette.Heading)) { append("next feeling.") }
                }, color = Ink, fontSize = 32.sp, lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(current?.let { "TUNED TO ${it.artist.uppercase()}" } ?: "A NEW FREQUENCY", color = Muted, fontSize = 10.sp,
                        letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = { refreshToken++ }, enabled = !loading) { Text("Refresh ↗", color = Ink) }
                }
            }
        }
        if (tracks.isNotEmpty() || current != null) item(key = "discovery-feature") {
            FeaturedMusicCarousel(tracks, current, playing, onPlay, onOpenPlayer)
        }
        items(tracks.drop(1).chunked(2), key = { "discovery-pair:${it.first().id}" }) { pair ->
            Row(Modifier.padding(horizontal = PipesSpace.Page).padding(bottom = PipesSpace.Page),
                horizontalArrangement = Arrangement.spacedBy(PipesSpace.Gap)) {
                pair.forEach { track ->
                    Box(Modifier.weight(1f)) { MusicArtworkCard(track, onPlay, onLike, onRadio, onSave) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item(key = "discovery-footer") {
            Box(Modifier.fillMaxWidth().padding(PipesSpace.Page), contentAlignment = Alignment.Center) {
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
