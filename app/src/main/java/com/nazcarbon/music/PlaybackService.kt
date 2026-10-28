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
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.data.NewPipeMusicSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.nazcarbon.music.data.PlaybackQueueRequests
import com.nazcarbon.music.data.retryPlaybackRequest
import kotlinx.coroutines.withContext

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var radioSource: NewPipeMusicSource? = null
    private var isRefillingQueue = false
    private val pendingTracks = ArrayDeque<MusicTrack>()
    private var pendingSession: String? = null
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
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory)))
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
        radioSource = NewPipeMusicSource().also { it.initialize() }
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                AudioLevels.clear()
                maybeExtendQueue(player)
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
                val stalled = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING &&
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
            { track, session -> queuedItem(track, track.streamUrl, session, false,
                autoplay = !track.pageUrl.startsWith("content://") && !track.pageUrl.startsWith("file://")) },
            { session, following ->
                pendingTracks.clear()
                pendingTracks.addAll(following)
                pendingSession = session.takeIf { following.isNotEmpty() }
            }) {
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
                        .add(SessionCommand("pipes.set_queue", Bundle.EMPTY)).build())
                    .build()
            }

            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (controller.uid != android.os.Process.myUid())
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                if (customCommand.customAction == "pipes.play_next") {
                    val current = player.currentMediaItem
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
                    val extras = current.mediaMetadata.extras
                    val sessionId = extras?.getString(AUTO_QUEUE_SESSION_KEY)
                    if (sessionId == null || args.getString("session") != sessionId)
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
                    val track = PlaybackQueueRequests.consume(args.getString("queue_id").orEmpty(), sessionId)?.singleOrNull()
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    val nextIndex = player.currentMediaItemIndex + 1
                    val existing = PlayNextQueue.existingUpcomingIndex(
                        (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
                        player.currentMediaItemIndex, track.id)
                    if (existing != null) player.moveMediaItem(existing, nextIndex)
                    else player.addMediaItem(nextIndex, queuedItem(track, track.streamUrl, sessionId,
                        extras.getBoolean(RADIO_METADATA_KEY), extras.getBoolean(AUTO_QUEUE_METADATA_KEY)))
                    if (player.shuffleModeEnabled) {
                        val timeline = player.currentTimeline
                        val order = buildList {
                            var index = timeline.getFirstWindowIndex(true)
                            while (index != C.INDEX_UNSET) {
                                add(index)
                                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
                            }
                        }
                        player.setShuffleOrder(DefaultShuffleOrder(
                            PlayNextQueue.prioritize(order, player.currentMediaItemIndex, nextIndex).toIntArray(),
                            System.nanoTime()))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction == "pipes.set_queue") {
                    val sessionId = player.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY)
                    if (sessionId == null || args.getString("session") != sessionId)
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
                    val tracks = PlaybackQueueRequests.consume(args.getString("queue_id").orEmpty(), sessionId)
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    pendingTracks.clear()
                    pendingSession = sessionId
                    pendingTracks.addAll(tracks)
                    maybeExtendQueue(player)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction != "pipes.start_radio") return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                val current = player.currentMediaItem ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
                pendingTracks.clear()
                pendingSession = null
                val extras = Bundle(current.mediaMetadata.extras ?: Bundle.EMPTY).apply {
                    putBoolean(RADIO_METADATA_KEY, true)
                    val session = java.util.UUID.randomUUID().toString()
                    putString("pipes_radio_session", session)
                    putBoolean(AUTO_QUEUE_METADATA_KEY, true)
                    putString(AUTO_QUEUE_SESSION_KEY, session)
                }
                player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
                player.replaceMediaItem(player.currentMediaItemIndex, current.buildUpon()
                    .setMediaMetadata(current.mediaMetadata.buildUpon().setExtras(extras).build()).build())
                maybeExtendQueue(player)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).setSessionActivity(openPlayer).build()
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
        recoveryJob = serviceScope.launch {
            delay(1_000L * recoveryAttempts)
            try {
                val extras = item.mediaMetadata.extras
                val page = extras?.getString("pipes_page_url") ?: item.mediaId
                val refreshed = withContext(Dispatchers.IO) {
                    radioSource!!.resolveForPlayback(MusicTrack(item.mediaId,
                        item.mediaMetadata.title?.toString().orEmpty(),
                        item.mediaMetadata.artist?.toString().orEmpty(),
                        extras?.getLong("pipes_duration") ?: 0L,
                        item.mediaMetadata.artworkUri?.toString().orEmpty(), "", page), forceRefresh = true)
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

    private fun maybeExtendQueue(player: Player) {
        val current = player.currentMediaItem ?: return
        val extras = current.mediaMetadata.extras ?: return
        val queueSession = extras.getString(AUTO_QUEUE_SESSION_KEY) ?: return
        if (pendingSession != null && pendingSession != queueSession) {
            pendingTracks.clear()
            pendingSession = null
        }
        val playlistQueue = pendingSession == queueSession
        if (!playlistQueue && !extras.getBoolean(AUTO_QUEUE_METADATA_KEY)) return
        val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
        if (android.os.SystemClock.elapsedRealtime() < queueRetryAfter || remaining > (if (extras.getBoolean(RADIO_METADATA_KEY)) 10 else 3) || isRefillingQueue || (playlistQueue && pendingTracks.isEmpty())) return
        val seed = MusicTrack(current.mediaId, current.mediaMetadata.title?.toString().orEmpty(),
            current.mediaMetadata.artist?.toString().orEmpty(), extras.getLong("pipes_duration"),
            current.mediaMetadata.artworkUri?.toString().orEmpty(), "", extras.getString("pipes_page_url") ?: current.mediaId)
        val isRadio = extras.getBoolean(RADIO_METADATA_KEY)
        isRefillingQueue = true
        mediaSession?.setSessionExtras(Bundle().apply {
            putBoolean("radio_loading", isRadio)
            putBoolean("autoplay_loading", true)
        })
        serviceScope.launch {
            var added = 0
            try {
                val source = radioSource ?: return@launch
                fun sessionStillActive() = player.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY) == queueSession &&
                    (playlistQueue || player.currentMediaItem?.mediaId == current.mediaId)
                val existingUrls = (0 until player.mediaItemCount).map {
                    player.getMediaItemAt(it).mediaMetadata.extras?.getString("pipes_page_url")
                }.toSet()
                val radioCandidates = if (playlistQueue) emptyList() else withContext(Dispatchers.IO) {
                    com.nazcarbon.music.data.MusicRecommendations.radio(seed, source.relatedSongs(seed, getSharedPreferences("pipes", MODE_PRIVATE).getString("recommendation_region", "AUTO") ?: "AUTO").filterNot { it.pageUrl in existingUrls })
                }
                val candidates = ArrayDeque(radioCandidates)
                var examined = 0
                while (added < (if (isRadio) 30 else 6) && examined < (if (isRadio) 60 else 18) && sessionStillActive()) {
                    val batch = if (playlistQueue) {
                        (0 until minOf(3, pendingTracks.size)).map { pendingTracks.removeFirst() }
                    } else (0 until minOf(3, candidates.size)).map { candidates.removeFirst() }
                    if (batch.isEmpty()) break
                    examined += batch.size
                    val resolved = coroutineScope {
                        batch.map { track -> async(Dispatchers.IO) {
                            try { retryPlaybackRequest { source.resolveForPlayback(track) } }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { null }
                        } }.awaitAll()
                    }
                    if (!sessionStillActive()) return@launch
                    for (track in resolved.filterNotNull()) {
                        val ended = player.playbackState == Player.STATE_ENDED
                        player.addMediaItem(queuedItem(track, track.streamUrl, queueSession, isRadio, autoplay = !playlistQueue))
                        added++
                        if (ended && player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                            player.prepare()
                        }
                    }
                }
                if (added == 0) mediaSession?.setSessionExtras(Bundle().apply {
                    putString("queue_error", if (playlistQueue) "Couldn't match the remaining playlist songs." else "Radio couldn't find playable songs. Try again.")
                })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mediaSession?.setSessionExtras(Bundle().apply { putString("queue_error", "Couldn't load related music. Try radio again.") })
            } finally {
                isRefillingQueue = false
                queueRetryAfter = if (added == 0) android.os.SystemClock.elapsedRealtime() + 15_000 else 0L
                val previous = mediaSession?.sessionExtras ?: Bundle.EMPTY
                mediaSession?.setSessionExtras(Bundle(previous).apply {
                    putBoolean("radio_loading", false)
                    putBoolean("autoplay_loading", false)
                })
                val activeSession = player.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY)
                if (activeSession != queueSession || (!playlistQueue && player.currentMediaItem?.mediaId != current.mediaId) || (playlistQueue && added > 0 && pendingTracks.isNotEmpty() &&
                    player.mediaItemCount - player.currentMediaItemIndex - 1 <= 3)) maybeExtendQueue(player)
            }
        }
    }

    private fun queuedItem(track: MusicTrack, url: String, queueSession: String, isRadio: Boolean, autoplay: Boolean) =
        MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(url))
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
