package com.nazcarbon.music

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nazcarbon.music.data.TutorialProgress
import com.nazcarbon.music.data.TutorialProgress.Mode
import com.nazcarbon.music.ui.theme.PipesPalette as P

internal enum class TutorialDestination { SEARCH, PLAYER, QUEUE, LIBRARY, SETTINGS }

private data class TutorialLesson(
    val title: String,
    val introduction: String,
    val tips: List<Pair<String, String>>,
    val icon: ImageVector,
    val action: String,
    val destination: TutorialDestination,
)

private val tutorialLessons = listOf(
    TutorialLesson("Find your next song", "Four tabs, four ways into your music.", listOf(
        "Search" to "Type a song or artist, or paste a YouTube link, Spotify song, or public Spotify playlist. Results load as you type. Tap a song to play it.",
        "Home & Explore" to "Home follows the playing song and your liked artists. Explore lets you choose a mood or genre, then refresh or retry its suggestions.",
        "Your own audio" to "The folder button in the top bar opens audio from your device, including FLAC and WAV. Online search needs a connection; local files do not.",
        "Spotify links" to "Spotify supplies song information; playback comes from YouTube Music. A public playlist import can be partial, and Pipes shows that in the collection.",
    ), Icons.Default.Search, "Try Search", TutorialDestination.SEARCH),
    TutorialLesson("Get to know the player", "Play a song to open the full player. Try these controls silently below.", listOf(
        "Seek with the waveform" to "Drag to preview a time; release to seek. Seeking becomes available when the song's duration is known.",
        "Keep browsing" to "The down arrow minimizes the player while music continues. Tap the mini-player's song title or artwork to reopen it. Swipe its song row left for next or right for previous.",
        "Stop means stop" to "The close button stops playback and clears the queue. Use pause to keep your place. Shuffle changes order; repeat cycles through off, all songs, and one song.",
        "Meet the artist" to "Tap the artist's name in the full player to find their albums and singles. Open a release to play it or add the whole release to a collection.",
    ), Icons.Default.PlayArrow, "Try the player", TutorialDestination.PLAYER),
    TutorialLesson("Decide what plays next", "The queue is for this listening session. Radio keeps it growing.", listOf(
        "Open the song menu" to "Use the three-dot Song actions button on a song row or artwork card. Play next puts a song immediately after the current one; Add to queue puts it at the end.",
        "A useful swipe" to "Swipe a song row right to add it to the queue. Swipe left to remove an upcoming entry. The mini-player uses swipes to change songs instead.",
        "See the order" to "Open Queue in the full player to see what's playing and what's next. Only upcoming entries can be removed. With nothing playing, Play next starts the song.",
        "Start radio" to "Choose Radio on an online song to keep related recordings coming. Starting radio on the current song keeps its playback position. Radio needs an online source.",
    ), Icons.Default.QueueMusic, "Try the queue", TutorialDestination.QUEUE),
    TutorialLesson("Make a collection yours", "Likes, playlists, and custom albums stay in Library on this device.", listOf(
        "Save a favorite" to "Tap the heart to keep a song in Liked songs. This saves a library entry; download the song separately for offline listening.",
        "Choose where songs go" to "Use + in the full player or Add to playlist or album from a song menu. Choose an existing playlist or album, or create one in the picker. Duplicate songs are skipped.",
        "Save several at once" to "Hold a song to select it, tap more songs, then choose Add to…. Select all includes the songs currently loaded on that screen. Cancel leaves them alone.",
        "Edit your collection" to "Open it in Library. The pencil changes its name or switches between Playlist and Album. Remove songs individually or in a selection. Deleting a collection leaves other collections and downloads intact.",
    ), Icons.Default.PlaylistAdd, "Try Library", TutorialDestination.LIBRARY),
    TutorialLesson("Give it a cover", "A custom album is your editable collection, with a cover you choose.", listOf(
        "Pick an image or GIF" to "Open a playlist or custom album in Library and choose Choose artwork. Android's file picker lets you select an image or animated GIF.",
        "Keep it yours" to "The cover appears in Library, the collection, and the Add to… picker. Pipes saves a copy, so moving the original image does not remove it. Animated GIFs keep moving.",
        "Change your mind" to "Change artwork replaces your cover. Use default artwork restores the first song's image or an empty collection's music icon. Renaming and editing songs preserve your custom cover.",
        "Choose a smaller image" to "Choose artwork up to 20 MB and about 16 megapixels. If an image cannot be read, your current cover stays in place.",
    ), Icons.Default.Image, "Choose a collection", TutorialDestination.LIBRARY),
    TutorialLesson("Take music offline", "Saving to a playlist and downloading are separate actions.", listOf(
        "Download a song" to "Use the download arrow in the full player, mini-player, or song menu, then choose a category. Pipes saves the source's best available downloadable audio; some sources do not offer a usable file.",
        "Check Library" to "Downloads shows transfer status and completed files. Wait for completion before going offline. Categories organize these files; playlists organize song entries.",
        "Download a link" to "Library's Download link button accepts direct HTTPS audio files or accessible public Instagram video posts. Paste YouTube and Spotify links into Search instead, then use a song's Download action.",
        "Local files" to "Use the top-bar folder button to open a file already on your device. Local FLAC and WAV keep their original format; downloading an online recording does not make it lossless.",
    ), Icons.Default.Download, "See downloads", TutorialDestination.LIBRARY),
    TutorialLesson("Settle in", "You can come back to this guide whenever you want.", listOf(
        "Find Settings" to "The gear button is in the top bar on Home, Search, Explore, and Library. From the full player, minimize first.",
        "Tune recommendations" to "Settings lets you choose a recommendation region or follow the current song automatically. Liked artists also help shape Home.",
        "Keep Pipes updated" to "Choose Check for updates in Settings. Updates download first; Android asks before installation. Later postpones reminders for five days. Official updates preserve your library.",
        "Resume or replay" to "Open Settings → Learn Pipes to resume a paused tutorial or replay it after finishing. The guide works offline; trying online music still needs a connection.",
    ), Icons.Default.Settings, "Open Settings", TutorialDestination.SETTINGS),
)

internal class AppTutorialState(private val preferences: SharedPreferences) {
    var progress by mutableStateOf(TutorialProgress.decode(preferences.getString(TutorialProgress.PREFERENCE_KEY, null)))
        private set
    val showingDialog get() = progress.mode == Mode.WELCOME || progress.mode == Mode.GUIDE
    val practicing get() = progress.mode == Mode.PRACTICE
    fun update(value: TutorialProgress) {
        progress = value
        preferences.edit().putString(TutorialProgress.PREFERENCE_KEY, value.encode()).apply()
    }
    fun open() = update(progress.open())
    fun pause() = update(progress.pause())
}

@Composable
internal fun AppTutorial(state: AppTutorialState, onTry: (TutorialDestination) -> Unit) {
    val progress = state.progress
    if (progress.mode == Mode.WELCOME) {
        AlertDialog(onDismissRequest = state::pause,
            icon = { Icon(Icons.Default.PlayArrow, null) },
            title = { Text("Learn Pipes") },
            text = { Text("Find music, learn the player, and make your library yours. Seven short lessons include silent practice controls and chances to try the real app. Skip, pause, or resume from Settings at any time.") },
            confirmButton = { TextButton(onClick = state::open) { Text("Start tutorial") } },
            dismissButton = { TextButton(onClick = state::pause) { Text("Later") } })
    } else if (progress.mode == Mode.GUIDE) {
        val lesson = tutorialLessons[progress.lesson]
        Dialog(onDismissRequest = { if (progress.lesson > 0) state.update(progress.back()) else state.pause() },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = P.Background) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("LEARN PIPES · ${progress.lesson + 1} / ${tutorialLessons.size}", style = MaterialTheme.typography.labelLarge,
                                color = P.Cyan, modifier = Modifier.weight(1f))
                            TextButton(onClick = state::pause) { Text("Save & close") }
                        }
                        LinearProgressIndicator(progress = { (progress.lesson + 1f) / tutorialLessons.size }, modifier = Modifier.fillMaxWidth(), color = P.Cyan)
                        key(progress.lesson) {
                            val scroll = rememberScrollState()
                            Column(Modifier.weight(1f).verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Surface(shape = RoundedCornerShape(16.dp), color = P.Raised) {
                                    Icon(lesson.icon, null, Modifier.padding(16.dp).size(32.dp), tint = P.Cyan)
                                }
                                Text(lesson.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                                Text(lesson.introduction, style = MaterialTheme.typography.bodyLarge, color = P.TextSoft)
                                if (progress.lesson == 0) TutorialNavigationPractice()
                                if (progress.lesson == 1) TutorialPlayerPractice()
                                lesson.tips.forEach { (title, body) ->
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                                        Text(body, style = MaterialTheme.typography.bodyMedium, color = P.TextSoft)
                                    }
                                }
                                OutlinedButton(onClick = { state.update(progress.practice()); onTry(lesson.destination) }, modifier = Modifier.fillMaxWidth()) { Text(lesson.action) }
                                Text("Try it in the app, then use Continue tutorial to return here.", style = MaterialTheme.typography.bodySmall, color = P.TextSoft)
                            }
                            if (scroll.canScrollForward) Text("Scroll for tips and a Try… shortcut ↓", style = MaterialTheme.typography.bodySmall,
                                color = P.TextSoft, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { state.update(progress.back()) }, enabled = progress.lesson > 0) { Text("Back") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { state.update(progress.next()) }) { Text(if (progress.lesson == tutorialLessons.lastIndex) "Finish" else "Next lesson") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TutorialPracticeBar(state: AppTutorialState, hasSong: Boolean) {
    Surface(color = P.Card, tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("TRY IT · ${tutorialLessons[state.progress.lesson].title}", style = MaterialTheme.typography.labelMedium, color = P.Cyan)
            Text(when (state.progress.lesson) {
                0 -> "Search for a song or paste a supported link."
                1 -> if (hasSong) "Try seeking, pausing, and minimizing." else "Play a song in Search or open a local file with the folder button first."
                2 -> if (hasSong) "Use Song actions to choose Play next, then check Queue." else "Play a song first, then open Queue in the full player."
                3 -> "Open a collection, or use + to create one."
                4 -> "Open a collection, then choose or change its artwork."
                5 -> "Check transfer status under Downloads."
                else -> "Find Learn Pipes here whenever you need it."
            }, style = MaterialTheme.typography.bodySmall, color = P.TextSoft)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { state.update(state.progress.copy(mode = Mode.GUIDE)) }) { Text("Continue tutorial") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = state::pause) { Icon(Icons.Default.Close, "Pause tutorial") }
            }
        }
    }
}

@Composable
private fun TutorialNavigationPractice() {
    var page by rememberSaveable { mutableStateOf("Home") }
    TutorialPracticeSurface {
        Text("Try the tabs", style = MaterialTheme.typography.titleSmall)
        NavigationDock(page) { page = it }
        Text(when (page) {
            "Home" -> "Home · music shaped by your playing song and liked artists."
            "Search" -> "Search · songs, artists, YouTube links, and Spotify links."
            "Explore" -> "Explore · choose a mood or genre for a fresh starting point."
            else -> "Library · likes, playlists, custom albums, and downloads."
        }, style = MaterialTheme.typography.bodyMedium, color = P.TextSoft)
    }
}

@Composable
private fun TutorialPlayerPractice() {
    var position by rememberSaveable { mutableLongStateOf(45_000L) }
    var status by rememberSaveable { mutableStateOf("Drag the waveform. This practice player is silent.") }
    TutorialPracticeSurface {
        Text("Try the player controls", style = MaterialTheme.typography.titleSmall)
        PlayerSeekControls(position, 180_000L, true, false, { position = it; status = "Position saved. In a playing song, audio jumps here on release." })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { status = "Minimized: music keeps playing while you browse." }) { Icon(Icons.Default.KeyboardArrowDown, "Practice minimize") }
            TextButton(onClick = { status = "Paused: your place and queue are kept." }) { Text("Pause") }
            IconButton(onClick = { position = 0L; status = "Closed: playback stops and the queue is cleared." }) { Icon(Icons.Default.Close, "Practice stop") }
        }
        Text(status, style = MaterialTheme.typography.bodyMedium, color = P.TextSoft, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun TutorialPracticeSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = P.Card) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
