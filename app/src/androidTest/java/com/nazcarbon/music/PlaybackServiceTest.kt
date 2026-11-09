package com.nazcarbon.music

import android.content.ComponentName
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.data.PlaybackQueueRequests
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun await(message: String, timeoutMs: Long = 15_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            var matched = false
            main { matched = predicate() }
            if (matched) return
            Thread.sleep(100)
        }
        fail(message)
    }

    @Test fun fullQueueShuffleBackgroundAndAudioFocus() {
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val file = java.io.File(context.filesDir, "playback-regression.wav")
        val samples = 44100 * 60
        val data = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(44100).putInt(88200).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { data.putShort((kotlin.math.sin(it * 2 * Math.PI * 220 / 44100) * 2000).toInt().toShort()) }
        file.writeBytes(data.array())
        val tracks = (0 until 120).map { MusicTrack("regression-$it", "Song $it", "Regression", 60, "", "", file.toURI().toString()) }
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        main { future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync() }
        val player = future!!.get(15, TimeUnit.SECONDS)
        fun command(action: String, arguments: Bundle = Bundle.EMPTY) {
            var result: com.google.common.util.concurrent.ListenableFuture<SessionResult>? = null
            main { result = player.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), arguments) }
            assertEquals(SessionResult.RESULT_SUCCESS, result!!.get(15, TimeUnit.SECONDS).resultCode)
        }
        val preferences = context.getSharedPreferences("pipes", android.content.Context.MODE_PRIVATE)
        val previousHistory = preferences.getString("listening_history", null)
        try {
            val session = "regression-session"
            main {
                player.setMediaItem(MediaItem.Builder().setMediaId(tracks[0].id).setUri(file.toURI().toString())
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(tracks[0].title).setArtist(tracks[0].artist)
                        .setExtras(Bundle().apply {
                            putString("pipes_auto_queue_session", session)
                            putString("pipes_page_url", tracks[0].pageUrl)
                        }).build()).build())
                player.prepare()
                player.play()
            }
            await("Initial local audio did not start") { player.isPlaying }
            command("pipes.set_queue", Bundle().apply {
                putString("session", session)
                putString("queue_id", PlaybackQueueRequests.register(session, tracks.drop(1)))
            })
            await("The complete playlist must be visible") { player.mediaItemCount == 120 }
            command("pipes.toggle_shuffle")
            await("Shuffle must keep all upcoming tracks reachable") { player.shuffleModeEnabled && player.upcomingIndices().size == 119 }
            main {
                assertEquals(120, player.playbackOrder().distinct().size)
                assertNotEquals((1 until 120).toList(), player.upcomingIndices())
                player.seekTo(player.upcomingIndices()[75], 0)
            }
            await("Shuffled local entry did not resolve") { player.isPlaying }
            var before = emptySet<String>()
            main { before = player.upcomingIndices().map { player.getMediaItemAt(it).mediaId }.toSet() }
            command("pipes.toggle_shuffle")
            await("Disabling shuffle lost upcoming tracks") {
                !player.shuffleModeEnabled && player.upcomingIndices().map { player.getMediaItemAt(it).mediaId }.toSet() == before
            }
            command("pipes.toggle_shuffle")
            // Advance to a high physical index that still has lower indices ahead in shuffle.
            main {
                val upcoming = player.upcomingIndices()
                val anchor = upcoming.first { value -> upcoming.drop(upcoming.indexOf(value) + 1).any { it < value } }
                player.seekTo(anchor, 0)
            }
            await("Shuffled jump did not start") { player.isPlaying }
            var chosen: MusicTrack? = null
            main {
                val index = player.upcomingIndices().first { it < player.currentMediaItemIndex }
                chosen = tracks.single { it.id == player.getMediaItemAt(index).mediaId }
            }
            command("pipes.play_next", Bundle().apply {
                putString("session", session)
                putString("queue_id", PlaybackQueueRequests.register(session, listOf(chosen!!)))
            })
            await("Play next must override shuffle") { player.getMediaItemAt(player.nextMediaItemIndex).mediaId == chosen!!.id }
            main { player.seekToNextMediaItem() }
            await("Playlist must advance through lazy local sources") { player.isPlaying && player.currentMediaItem?.mediaId == chosen!!.id }
            val appended = tracks.first().copy(id = "regression-appended", title = "Appended song")
            command("pipes.add_queue", Bundle().apply {
                putString("session", session)
                putString("queue_id", PlaybackQueueRequests.register(session, listOf(appended)))
            })
            await("Appending during shuffle put the song behind the playhead") {
                player.upcomingIndices().any { player.getMediaItemAt(it).mediaId == appended.id }
            }
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            var initialPosition = 0L
            main { initialPosition = player.currentPosition }
            await("Playback stopped after leaving the activity") { player.isPlaying && player.currentPosition > initialPosition + 1000 }

            val audioManager = context.getSystemService(AudioManager::class.java)
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener({}, android.os.Handler(android.os.Looper.getMainLooper())).build()
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audioManager.requestAudioFocus(focus))
            try {
                await("Another app's audio focus must pause Pipes") { !player.isPlaying && !player.playWhenReady }
                Thread.sleep(1200)
                main { assertFalse("Recovery must not override audio focus", player.playWhenReady) }
            } finally { audioManager.abandonAudioFocusRequest(focus) }
        } finally {
            main { player.stop(); player.clearMediaItems(); player.release() }
            preferences.edit().apply {
                if (previousHistory == null) remove("listening_history") else putString("listening_history", previousHistory)
            }.commit()
            file.delete()
        }
    }
    @Test fun liveSingleSongAutoplayBuildsQueueAndAdvancesNaturally() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("live_source") == "1")
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val source = com.nazcarbon.music.data.NewPipeMusicSource().also { it.initialize() }
        val track = source.resolveForPlayback(source.searchSongs("Daft Punk Get Lucky").first())
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        main { future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync() }
        val player = future!!.get(15, TimeUnit.SECONDS)
        val preferences = context.getSharedPreferences("pipes", android.content.Context.MODE_PRIVATE)
        val history = preferences.getString("listening_history", null)
        try {
            main {
                player.setMediaItem(MediaItem.Builder().setMediaId(track.id).setUri(track.streamUrl)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist)
                        .setExtras(Bundle().apply {
                            putString("pipes_auto_queue_session", "live-regression")
                            putBoolean("pipes_auto_queue", true)
                            putString("pipes_page_url", track.pageUrl)
                            putLong("pipes_duration", track.durationSeconds)
                        }).build()).build())
                player.prepare()
                player.play()
            }
            await("One online song should grow a substantial autoplay queue", 60_000) {
                player.isPlaying && player.mediaItemCount >= 11 && player.duration > 2_000
            }
            main { player.seekTo(player.duration - 1_000) }
            await("Autoplay should advance naturally to a lazily resolved online song", 45_000) {
                player.isPlaying && player.currentMediaItem?.mediaId != track.id && player.currentPosition > 500
            }
        } finally {
            main { player.stop(); player.clearMediaItems(); player.release() }
            preferences.edit().apply {
                if (history == null) remove("listening_history") else putString("listening_history", history)
            }.commit()
        }
    }

}
