package com.nazcarbon.music

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazcarbon.music.data.LyricsSource
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.data.TrackLyrics
import com.nazcarbon.music.ui.theme.PipesPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException

@Composable
internal fun PlayerLyrics(
    track: MusicTrack,
    duration: Long,
    position: Long,
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
            val active = lyrics.activeLine(position)
            LaunchedEffect(active) {
                list.animateScrollToItem(active.coerceAtLeast(0), -list.layoutInfo.viewportSize.height / 4)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(lyrics.timed, key = { _, line -> line.startMs }) { index, line ->
                    if (line.text.isBlank()) return@itemsIndexed
                    val highlighted = index == active
                    val color by animateColorAsState(
                        if (highlighted) Color.White else Color.White.copy(alpha = 0.65f),
                        label = "Lyric color",
                    )
                    Text(
                        line.text,
                        modifier = Modifier.fillMaxWidth()
                            .then(if (onSeek == null) Modifier else Modifier.clickable {
                                onSeek(line.startMs.coerceAtLeast(0))
                            })
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        style = TextStyle(
                            color = color,
                            fontSize = 22.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = if (highlighted) Shadow(PipesPalette.Cyan, Offset.Zero, 16f) else null,
                        ),
                    )
                }
            }
        }
    }
}
