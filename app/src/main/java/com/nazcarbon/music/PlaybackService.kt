package com.nazcarbon.music

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import com.nazcarbon.music.data.PlaybackStreams
import com.nazcarbon.music.data.PlaybackStreamCache
import com.nazcarbon.music.data.PlaybackOrder
import com.nazcarbon.music.data.ListeningProfile
import com.nazcarbon.music.data.PlaylistStore
import com.nazcarbon.music.data.MusicPlaylist
import com.nazcarbon.music.data.MusicRecommendations
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.nazcarbon.music.data.PlayNextQueue
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import com.nazcarbon.music.data.AudioDownloads
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.data.NewPipeMusicSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nazcarbon.music.data.PlaybackQueueRequests
import com.nazcarbon.music.data.retryPlaybackRequest
import kotlinx.coroutines.withContext

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var radioSource: NewPipeMusicSource? = null
    private var isRefillingQueue = false
    private lateinit var streamCache: PlaybackStreamCache
    private var activeQueueSession: String? = null
    private var refillJob: Job? = null
    private var recoveryJob: Job? = null
    private var recoveryItem: MediaItem? = null
    private var recoveryAttempts = 0
    private var queueRetryAfter = 0L


    override fun onCreate() {
        super.onCreate()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
            )
            .setAllowCrossProtocolRedirects(false)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(ReactiveAudioProcessor()))
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        }
        radioSource = NewPipeMusicSource().also { it.initialize() }
        streamCache = PlaybackStreamCache { track ->
            AudioDownloads.resolveForPlayback(this@PlaybackService, track) {
                radioSource!!.resolveForPlayback(it)
            }
        }
        val dataSource = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, httpFactory)) { spec ->
            spec.withUri(Uri.parse(streamCache.resolve(spec.uri.toString())))
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                AudioLevels.clear()
                updateQueueSession(player)
                if (mediaItem != recoveryItem) {
                    recoveryJob?.cancel()
                    recoveryJob = null
                    recoveryAttempts = 0
                    recoveryItem = mediaItem
                }
                if (player.isPlaying) recordListening(player)
                maybeExtendQueue(player)
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                updateQueueSession(player)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) recordListening(player)
            }
            override fun onPlayerError(error: PlaybackException) {
                recoverPlayback(player)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) maybeExtendQueue(player)
            }
        })
        serviceScope.launch {
            var stalledSince = 0L
            var lastPosition = -1L
            while (true) {
                delay(5_000)
                val stalled = player.playWhenReady && player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE && player.playbackState == Player.STATE_BUFFERING &&
                    player.currentPosition == lastPosition
                if (!stalled) stalledSince = 0L
                else if (stalledSince == 0L) stalledSince = android.os.SystemClock.elapsedRealtime()
                else if (android.os.SystemClock.elapsedRealtime() - stalledSince >= 30_000) {
                    recoverPlayback(player)
                    stalledSince = 0L
                }
                if (player.currentPosition > lastPosition && player.playbackState == Player.STATE_READY) {
                    recoveryAttempts = 0
                }
                if (player.playWhenReady && player.playerError != null) recoverPlayback(player)
                lastPosition = player.currentPosition
                if (player.playWhenReady) maybeExtendQueue(player)
            }
        }
        val openPlayer = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaLibrarySession.Builder(this, player, object : CarMediaLibrary(this@PlaybackService, serviceScope, radioSource!!,
            { track, session -> queuedItem(track, session, false,
                autoplay = canAutoplay(track)) }) {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                if (controller.uid != android.os.Process.myUid()) {
                    val car = canBrowse(session, controller)
                    if (!controller.isTrusted && !car) return MediaSession.ConnectionResult.reject()
                    val commands = Player.Commands.Builder()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_GET_TIMELINE)
                    if (car) commands.add(Player.COMMAND_SET_MEDIA_ITEM).add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                        .add(Player.COMMAND_PREPARE).add(Player.COMMAND_STOP).add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(if (car) MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS else MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS)
                        .setAvailablePlayerCommands(commands.build())
                        .build()
                }
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand("pipes.play_next", Bundle.EMPTY))
                        .add(SessionCommand("pipes.start_radio", Bundle.EMPTY))
                        .add(SessionCommand("pipes.set_queue", Bundle.EMPTY))
                        .add(SessionCommand("pipes.toggle_shuffle", Bundle.EMPTY))
                        .add(SessionCommand("pipes.add_queue", Bundle.EMPTY)).build())
                    .build()
            }

            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (controller.uid != android.os.Process.myUid())
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                if (customCommand.customAction == "pipes.toggle_shuffle") {
                    val order = player.playbackOrder()
                    val current = player.currentMediaItemIndex
                    val position = order.indexOf(current)
                    if (position >= 0) {
                        val past = order.take(position + 1)
                        val upcoming = order.drop(position + 1)
                        if (!player.shuffleModeEnabled) {
                            player.setShuffleOrder(DefaultShuffleOrder(
                                (past + upcoming.shuffled()).toIntArray(), System.nanoTime()))
                        } else {
                            // Materialize the played prefix before restoring natural order, so no
                            // unplayed song disappears behind the current physical list index.
                            PlaybackOrder.movesTo(past + upcoming.sorted()).forEach { (from, to) ->
                                player.moveMediaItem(from, to)
                            }
                        }
                    }
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    maybeExtendQueue(player)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction == "pipes.play_next") {
                    val current = player.currentMediaItem
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
                    val extras = current.mediaMetadata.extras
                    val sessionId = extras?.getString(AUTO_QUEUE_SESSION_KEY)
                    if (sessionId == null || args.getString("session") != sessionId)
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
                    val track = PlaybackQueueRequests.consume(args.getString("queue_id").orEmpty(), sessionId)?.singleOrNull()
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    val existing = PlaybackOrder.existingUpcoming(
                        (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
                        player.playbackOrder(), player.currentMediaItemIndex, track.id)
                    if (player.shuffleModeEnabled) {
                        val next = existing ?: player.mediaItemCount.also {
                            player.addMediaItem(queuedItem(track, sessionId,
                                extras.getBoolean(RADIO_METADATA_KEY), extras.getBoolean(AUTO_QUEUE_METADATA_KEY)))
                        }
                        player.setShuffleOrder(DefaultShuffleOrder(
                            PlayNextQueue.prioritize(player.playbackOrder(), player.currentMediaItemIndex, next).toIntArray(),
                            System.nanoTime()))
                    } else {
                        val next = player.currentMediaItemIndex + 1
                        if (existing != null) player.moveMediaItem(existing, next)
                        else player.addMediaItem(next, queuedItem(track, sessionId,
                            extras.getBoolean(RADIO_METADATA_KEY), extras.getBoolean(AUTO_QUEUE_METADATA_KEY)))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction == "pipes.set_queue" || customCommand.customAction == "pipes.add_queue") {
                    val sessionId = player.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY)
                    if (sessionId == null || args.getString("session") != sessionId)
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
                    val tracks = PlaybackQueueRequests.consume(args.getString("queue_id").orEmpty(), sessionId)
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    appendTracks(player, tracks, sessionId,
                        player.currentMediaItem?.mediaMetadata?.extras?.getBoolean(RADIO_METADATA_KEY) == true)
                    val current = player.currentMediaItem!!
                    val metadata = Bundle(current.mediaMetadata.extras).apply {
                        putBoolean(AUTO_QUEUE_METADATA_KEY, canAutoplay(trackFromItem(current)))
                    }
                    player.replaceMediaItem(player.currentMediaItemIndex,
                        current.buildUpon().setMediaMetadata(current.mediaMetadata.buildUpon().setExtras(metadata).build()).build())
                    maybeExtendQueue(player)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction != "pipes.start_radio") return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                val current = player.currentMediaItem ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
                val extras = Bundle(current.mediaMetadata.extras ?: Bundle.EMPTY).apply {
                    putBoolean(RADIO_METADATA_KEY, true)
                    val session = java.util.UUID.randomUUID().toString()
                    putString("pipes_radio_session", session)
                    putBoolean(AUTO_QUEUE_METADATA_KEY, true)
                    putString(AUTO_QUEUE_SESSION_KEY, session)
                }
                player.upcomingIndices().sortedDescending().forEach(player::removeMediaItem)
                player.replaceMediaItem(player.currentMediaItemIndex, current.buildUpon()
                    .setMediaMetadata(current.mediaMetadata.buildUpon().setExtras(extras).build()).build())
                maybeExtendQueue(player)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).setSessionActivity(openPlayer).build()
    }

    private fun updateQueueSession(player: Player) {
        val session = player.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY)
        if (session == activeQueueSession) return
        activeQueueSession = session
        refillJob?.cancel()
        isRefillingQueue = false
        queueRetryAfter = 0L
        mediaSession?.setSessionExtras(Bundle.EMPTY)
    }

    private fun recoverPlayback(player: Player) {
        if (recoveryJob?.isActive == true) return
        val item = player.currentMediaItem ?: return
        if (recoveryItem != item) {
            recoveryItem = item
            recoveryAttempts = 0
        }
        if (recoveryAttempts >= 3) {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
            } else {
                mediaSession?.setSessionExtras(Bundle().apply {
                    putString("queue_error", "Playback couldn't reconnect. Try playing again.")
                })
            }
            return
        }
        recoveryAttempts++
        item.localConfiguration?.uri?.toString()?.let(streamCache::invalidate)
        recoveryJob = serviceScope.launch {
            delay(1_000L * recoveryAttempts)
            try {
                val extras = item.mediaMetadata.extras
                val page = extras?.getString("pipes_page_url") ?: item.mediaId
                val refreshed = withContext(Dispatchers.IO) {
                    AudioDownloads.resolveForPlayback(this@PlaybackService, MusicTrack(item.mediaId,
                        item.mediaMetadata.title?.toString().orEmpty(),
                        item.mediaMetadata.artist?.toString().orEmpty(),
                        extras?.getLong("pipes_duration") ?: 0L,
                        item.mediaMetadata.artworkUri?.toString().orEmpty(), "", page)) { radioSource!!.resolveForPlayback(it, forceRefresh = true) }
                }
                if (player.currentMediaItem != item) return@launch
                val position = player.currentPosition
                val replacement = item.buildUpon().setUri(refreshed.streamUrl).build()
                recoveryItem = replacement
                player.replaceMediaItem(player.currentMediaItemIndex, replacement)
                player.seekTo(player.currentMediaItemIndex, position)
                player.prepare()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (player.currentMediaItem == item) {
                    recoveryJob = null
                    recoverPlayback(player)
                }
            }
        }
    }

    private fun recordListening(player: Player) {
        val item = player.currentMediaItem ?: return
        val prefs = getSharedPreferences("pipes", MODE_PRIVATE)
        val track = trackFromItem(item)
        val previous = PlaylistStore.load(prefs, "listening_history").flatMap { it.tracks }
        if (previous.firstOrNull()?.id == track.id) return
        PlaylistStore.save(prefs, listOf(MusicPlaylist("Recently played", ListeningProfile.record(previous, track))), "listening_history")
    }

    private fun canAutoplay(track: MusicTrack) =
        !com.nazcarbon.music.data.OfflinePlayback.isLocal(track)

    private fun appendTracks(player: ExoPlayer, tracks: List<MusicTrack>, session: String, radio: Boolean) {
        if (tracks.isEmpty()) return
        val oldCount = player.mediaItemCount
        val order = player.playbackOrder()
        val ended = player.playbackState == Player.STATE_ENDED
        player.addMediaItems(tracks.map { queuedItem(it, session, radio, canAutoplay(it)) })
        if (player.shuffleModeEnabled) {
            // DefaultShuffleOrder may insert new items behind the playhead. Keep them reachable.
            player.setShuffleOrder(DefaultShuffleOrder(
                (order + (oldCount until player.mediaItemCount).shuffled()).toIntArray(), System.nanoTime()))
        }
        if (ended && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
        }
    }

    private fun maybeExtendQueue(player: ExoPlayer) {
        val current = player.currentMediaItem ?: return
        val extras = current.mediaMetadata.extras ?: return
        val queueSession = extras.getString(AUTO_QUEUE_SESSION_KEY) ?: return
        if (!extras.getBoolean(AUTO_QUEUE_METADATA_KEY) || player.repeatMode != Player.REPEAT_MODE_OFF) return
        if (android.os.SystemClock.elapsedRealtime() < queueRetryAfter ||
            player.upcomingIndices().size > 10 || isRefillingQueue) return
        val seed = trackFromItem(current)
        val isRadio = extras.getBoolean(RADIO_METADATA_KEY)
        isRefillingQueue = true
        mediaSession?.setSessionExtras(Bundle().apply {
            putBoolean("radio_loading", isRadio)
            putBoolean("autoplay_loading", true)
        })
        refillJob = serviceScope.launch {
            var added = 0
            try {
                val source = radioSource ?: return@launch
                fun sessionStillActive() = player.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY) == queueSession
                val history = (0 until player.mediaItemCount).map { trackFromItem(player.getMediaItemAt(it)) }
                val region = getSharedPreferences("pipes", MODE_PRIVATE).getString("recommendation_region", "AUTO") ?: "AUTO"
                // Try the latest song and a different recent seed if a source radio is exhausted.
                val seeds = (listOf(seed) + history.asReversed()).distinctBy { it.pageUrl }
                    .filter { canAutoplay(it) }.take(3)
                val fallbackCandidates = mutableListOf<MusicTrack>()
                for (anchor in seeds) {
                    val candidates = try {
                        withContext(Dispatchers.IO) {
                            retryPlaybackRequest { source.relatedSongs(anchor, region) }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                    if (!sessionStillActive()) return@launch
                    fallbackCandidates += candidates
                    val fresh = MusicRecommendations.discovery(seed, candidates, history)
                    if (fresh.isNotEmpty()) {
                        appendTracks(player, fresh, queueSession, isRadio)
                        added = fresh.size
                        break
                    }
                }
                if (added == 0 && fallbackCandidates.isNotEmpty() && sessionStillActive()) {
                    val order = player.playbackOrder()
                    val position = order.indexOf(player.currentMediaItemIndex)
                    val recent = order.take(position).takeLast(8).map { trackFromItem(player.getMediaItemAt(it)) }
                    val queued = (listOf(player.currentMediaItemIndex) + player.upcomingIndices())
                        .map { trackFromItem(player.getMediaItemAt(it)) }
                    val continued = MusicRecommendations.continuation(seed, fallbackCandidates, queued, recent)
                    appendTracks(player, continued, queueSession, isRadio)
                    added = continued.size
                }
                if (added == 0) mediaSession?.setSessionExtras(Bundle().apply {
                    putString("queue_error", "Couldn't find more related songs. Autoplay will retry.")
                })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mediaSession?.setSessionExtras(Bundle().apply { putString("queue_error", "Couldn't load more music. Autoplay will retry.") })
            } finally {
                if (activeQueueSession == queueSession) {
                    isRefillingQueue = false
                    queueRetryAfter = if (added == 0) android.os.SystemClock.elapsedRealtime() + 15_000 else 0L
                    val previous = mediaSession?.sessionExtras ?: Bundle.EMPTY
                    mediaSession?.setSessionExtras(Bundle(previous).apply {
                        putBoolean("radio_loading", false)
                        putBoolean("autoplay_loading", false)
                    })
                }
            }
        }
    }

    private fun trackFromItem(item: MediaItem): MusicTrack {
        val extras = item.mediaMetadata.extras
        return MusicTrack(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(),
            item.mediaMetadata.artist?.toString().orEmpty(), extras?.getLong("pipes_duration") ?: 0L,
            item.mediaMetadata.artworkUri?.toString().orEmpty(), "", extras?.getString("pipes_page_url") ?: item.mediaId)
    }

    private fun queuedItem(track: MusicTrack, queueSession: String, isRadio: Boolean, autoplay: Boolean) =
        MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(PlaybackStreams.encode(track)))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setArtworkUri(Uri.parse(track.thumbnailUrl))
                    .setExtras(Bundle().apply {
                        putBoolean(RADIO_METADATA_KEY, isRadio)
                        putString("pipes_radio_session", if (isRadio) queueSession else null)
                        putBoolean(AUTO_QUEUE_METADATA_KEY, autoplay)
                        putString("pipes_page_url", track.pageUrl)
                        putLong("pipes_duration", track.durationSeconds)
                        putString(AUTO_QUEUE_SESSION_KEY, queueSession)
                        putString("audio_quality", track.audioQuality)
                    })
                    .build(),
            )
            .build()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onDestroy() {
        AudioLevels.clear()
        serviceScope.cancel()
        radioSource = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val RADIO_METADATA_KEY = "pipes_radio"
        const val AUTO_QUEUE_METADATA_KEY = "pipes_auto_queue"
        const val AUTO_QUEUE_SESSION_KEY = "pipes_auto_queue_session"
    }
}
