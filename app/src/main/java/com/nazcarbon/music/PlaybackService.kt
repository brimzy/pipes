package com.nazcarbon.music

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.nazcarbon.music.data.PlaybackQueueRequests
import kotlinx.coroutines.withContext

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var radioSource: NewPipeMusicSource? = null
    private var isRefillingQueue = false
    private val pendingTracks = ArrayDeque<MusicTrack>()
    private var pendingSession: String? = null

    override fun onCreate() {
        super.onCreate()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
            )
            .setAllowCrossProtocolRedirects(true)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        radioSource = NewPipeMusicSource().also { it.initialize() }
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                maybeExtendQueue(player)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) maybeExtendQueue(player)
            }
        })
        mediaSession = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand("pipes.start_radio", Bundle.EMPTY))
                        .add(SessionCommand("pipes.set_queue", Bundle.EMPTY)).build())
                    .build()

            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
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
                // Only metadata changes: retain the stream, position and paused/playing state.
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
        }).build()
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
        if (remaining > 3 || isRefillingQueue || (playlistQueue && pendingTracks.isEmpty())) return
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
                fun sessionStillActive() = player.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY) == queueSession
                val existingUrls = (0 until player.mediaItemCount).map {
                    player.getMediaItemAt(it).mediaMetadata.extras?.getString("pipes_page_url")
                }.toSet()
                val radioCandidates = if (playlistQueue) emptyList() else withContext(Dispatchers.IO) {
                    source.relatedSongs(seed).filterNot { it.pageUrl in existingUrls }
                }
                val candidates = ArrayDeque(radioCandidates)
                var examined = 0
                while (added < 6 && examined < 18 && sessionStillActive()) {
                    val batch = if (playlistQueue) {
                        (0 until minOf(3, pendingTracks.size)).map { pendingTracks.removeFirst() }
                    } else (0 until minOf(3, candidates.size)).map { candidates.removeFirst() }
                    if (batch.isEmpty()) break
                    examined += batch.size
                    val resolved = coroutineScope {
                        batch.map { track -> async(Dispatchers.IO) {
                            try { source.resolveForPlayback(track) }
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
                val previous = mediaSession?.sessionExtras ?: Bundle.EMPTY
                mediaSession?.setSessionExtras(Bundle(previous).apply {
                    putBoolean("radio_loading", false)
                    putBoolean("autoplay_loading", false)
                })
                val activeSession = player.currentMediaItem?.mediaMetadata?.extras?.getString(AUTO_QUEUE_SESSION_KEY)
                if (activeSession != queueSession || (playlistQueue && added > 0 && pendingTracks.isNotEmpty() &&
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
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
