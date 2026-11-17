package com.nazcarbon.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazcarbon.music.data.LyricLine
import com.nazcarbon.music.data.LyricsSource
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.data.TrackLyrics
import com.nazcarbon.music.ui.theme.PipesPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException

@Composable
internal fun PlayerLyrics(
    track: MusicTrack,
    duration: Long,
    position: Long,
    playing: Boolean,
    readPosition: (String) -> Long?,
    onSeek: ((Long) -> Unit)?,
) {
    var result by remember(track) { mutableStateOf<Result<TrackLyrics?>?>(null) }
    var retry by remember(track) { mutableIntStateOf(0) }
    LaunchedEffect(track, retry) {
        result = null
        result = try {
            Result.success(withContext(Dispatchers.IO) {
                LyricsSource.fetch(track, track.durationSeconds.takeIf { it > 0 } ?: (duration + 500) / 1_000)
            })
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: JSONException) {
            Result.failure(error)
        }
    }

    val lyrics = result?.getOrNull()
    when {
        lyrics == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when {
                    result == null -> "Loading lyrics…"
                    result?.isFailure == true -> "Couldn’t load lyrics"
                    else -> "No lyrics found for this song"
                }, color = Color.White)
                if (result?.isFailure == true) {
                    TextButton(onClick = { retry++ }) { Text("Try again") }
                }
            }
        }
        lyrics.timed.isEmpty() -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
        ) {
            item { Text(lyrics.plain, color = Color.White, fontSize = 20.sp, lineHeight = 30.sp) }
        }
        else -> {
            val list = rememberLazyListState()
            val wordSynced = remember(lyrics) { lyrics.timed.any { it.words.isNotEmpty() } }
            var sampledPosition by remember(track.id) { mutableLongStateOf(position) }
            val latestReadPosition by rememberUpdatedState(readPosition)
            LaunchedEffect(track.id, playing, wordSynced) {
                while (playing && wordSynced) {
                    latestReadPosition(track.id)?.let { sampledPosition = it }
                    delay(25)
                }
            }
            val lyricPosition = if (playing && wordSynced) sampledPosition else position
            val active = lyrics.activeLine(lyricPosition)
            LaunchedEffect(active) {
                list.animateScrollToItem(active.coerceAtLeast(0), -list.layoutInfo.viewportSize.height / 4)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(lyrics.timed) { index, line ->
                    if (line.text.isNotBlank()) {
                        val highlighted = index == active && (line.endMs == null || lyricPosition < line.endMs)
                        LyricsLine(
                            line,
                            highlighted,
                            if (highlighted) line.activeWord(lyricPosition) else -1,
                            onSeek,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsLine(
    line: LyricLine,
    highlighted: Boolean,
    activeWord: Int,
    onSeek: ((Long) -> Unit)?,
) {
    val color by animateColorAsState(
        when {
            highlighted && line.words.isEmpty() -> Color.White
            highlighted -> Color.White.copy(alpha = 0.82f)
            else -> Color.White.copy(alpha = 0.65f)
        },
        label = "Lyric line color",
    )
    val pulse = remember(activeWord) { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (activeWord >= 0) pulse.animateTo(1f, tween(durationMillis = 100))
    }
    val word = line.words.getOrNull(activeWord)
    val text = buildAnnotatedString {
        append(line.text)
        if (word != null) {
            val growth = pulse.value
            addStyle(
                SpanStyle(
                    color = Color.White,
                    fontSize = (22 + 4 * growth).sp,
                    shadow = Shadow(PipesPalette.Cyan, Offset.Zero, 7f + 12f * growth),
                ),
                word.range.first,
                word.range.last + 1,
            )
        }
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth()
            .then(if (onSeek == null) Modifier else Modifier.clickable {
                onSeek(line.startMs.coerceAtLeast(0))
            })
            .padding(horizontal = 8.dp, vertical = 6.dp),
        style = TextStyle(
            color = color,
            fontSize = 22.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            shadow = if (highlighted && line.words.isEmpty()) Shadow(PipesPalette.Cyan, Offset.Zero, 16f) else null,
        ),
    )
}
