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
import kotlinx.coroutines.withContext

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var radioSource: NewPipeMusicSource? = null
    private var isRefillingRadio = false

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
                maybeExtendRadio(player)
            }
        })
        mediaSession = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand("pipes.start_radio", Bundle.EMPTY)).build())
                    .build()

            override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (customCommand.customAction != "pipes.start_radio") return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                val current = player.currentMediaItem ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_INVALID_STATE))
                // Only metadata changes: retain the stream, position and paused/playing state.
                val extras = Bundle(current.mediaMetadata.extras ?: Bundle.EMPTY).apply {
                    putBoolean(RADIO_METADATA_KEY, true)
                    putString("pipes_radio_session", java.util.UUID.randomUUID().toString())
                }
                player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
                player.replaceMediaItem(player.currentMediaItemIndex, current.buildUpon()
                    .setMediaMetadata(current.mediaMetadata.buildUpon().setExtras(extras).build()).build())
                maybeExtendRadio(player)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).build()
    }

    private fun maybeExtendRadio(player: Player) {
        val current = player.currentMediaItem ?: return
        if (current.mediaMetadata.extras?.getBoolean(RADIO_METADATA_KEY) != true) return
        val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
        if (remaining > 3 || isRefillingRadio) return

        val seed = MusicTrack(
            id = current.mediaId,
            title = current.mediaMetadata.title?.toString().orEmpty(),
            artist = current.mediaMetadata.artist?.toString().orEmpty(),
            durationSeconds = 0,
            thumbnailUrl = current.mediaMetadata.artworkUri?.toString().orEmpty(),
            streamUrl = current.localConfiguration?.uri?.toString().orEmpty(),
            pageUrl = current.mediaId,
        )
        val radioSession = current.mediaMetadata.extras?.getString("pipes_radio_session")
        isRefillingRadio = true
        mediaSession?.setSessionExtras(Bundle().apply { putBoolean("radio_loading", true) })
        serviceScope.launch {
            try {
                val source = radioSource ?: return@launch
                val related = withContext(Dispatchers.IO) { source.relatedSongs(seed) }
                val existingIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                val nextTracks = related.filterNot { it.id in existingIds }.take(6)
                for (track in nextTracks) {
                    val resolved = try {
                        withContext(Dispatchers.IO) { source.resolveAudio(track) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { continue }
                    val active = player.currentMediaItem?.mediaMetadata?.extras
                    if (active?.getBoolean(RADIO_METADATA_KEY) != true || active.getString("pipes_radio_session") != radioSession) return@launch
                    if ((0 until player.mediaItemCount).none { player.getMediaItemAt(it).mediaId == track.id }) {
                        val ended = player.playbackState == Player.STATE_ENDED
                        player.addMediaItem(radioItem(resolved, resolved.streamUrl, radioSession))
                        if (ended && player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                            player.prepare()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Recommendation outages must never crash or interrupt the playing track.
            } finally {
                isRefillingRadio = false
                mediaSession?.setSessionExtras(Bundle().apply { putBoolean("radio_loading", false) })
                if (player.currentMediaItem?.mediaMetadata?.extras?.getString("pipes_radio_session") != radioSession) {
                    maybeExtendRadio(player)
                }
            }
        }
    }

    private fun radioItem(track: MusicTrack, url: String, radioSession: String?) =
        MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(url))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(Uri.parse(track.thumbnailUrl))
                    .setExtras(Bundle().apply {
                        putBoolean(RADIO_METADATA_KEY, true)
                        putString("pipes_radio_session", radioSession)
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
    }
}
