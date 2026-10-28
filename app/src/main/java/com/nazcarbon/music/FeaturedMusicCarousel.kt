package com.nazcarbon.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.ui.theme.PipesPalette as P

@Composable
internal fun FeaturedMusicCarousel(tracks: List<MusicTrack>, current: MusicTrack?, playing: Boolean,
    onPlay: (MusicTrack) -> Unit, onOpen: () -> Unit) {
    val cards = (listOfNotNull(current) + tracks).distinctBy { it.id }.take(6)
    if (cards.isEmpty()) return
    key(current?.id) {
        val pager = rememberPagerState(pageCount = { cards.size })
        val audioLevels = rememberAudioLevels(playing && cards.getOrNull(pager.currentPage)?.id == current?.id)
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            HorizontalPager(pager, contentPadding = PaddingValues(horizontal = PipesSpace.Page),
                pageSpacing = 12.dp, key = { cards[it].id }) { index ->
                val track = cards[index]
                val selected = track.id == current?.id
                Box(Modifier.fillMaxWidth().aspectRatio(1.15f)
                    .clip(RoundedCornerShape(24.dp)).background(P.Dock)
                    .clickable(role = Role.Button, onClickLabel = if (selected) "Open player" else "Play ${track.title}") {
                        if (selected) onOpen() else onPlay(track)
                    }) {
                    ArtworkImage(track.thumbnailUrl, null, Modifier.matchParentSize()
                        .blur(40.dp, BlurredEdgeTreatment.Unbounded), contentScale = ContentScale.Crop, alpha = .55f)
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                        P.Background.copy(alpha = .12f), P.Background.copy(alpha = .88f)))))
                    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (selected) "YOUR FREQUENCY" else "IN YOUR ORBIT", color = P.TextSoft, fontSize = 10.sp, letterSpacing = 1.sp)
                            Text("${index + 1} / ${cards.size}", color = P.TextSoft, fontSize = 10.sp)
                        }
                        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                val edge = minOf(maxWidth, maxHeight)
                                ArtworkImage(track.thumbnailUrl, null, Modifier.size(edge)
                                    .artworkSurface(RoundedCornerShape(14.dp), 16.dp), contentScale = ContentScale.Crop)
                            }
                        }
                        Text(track.title, color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = P.TextSoft, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        if (selected && playing) Canvas(Modifier.fillMaxWidth().height(20.dp).padding(top = 8.dp)) {
                            val levels = audioLevels.value
                            val step = 4.dp.toPx()
                            val start = (size.width - levels.size * step) / 2f
                            levels.forEachIndexed { i, level ->
                                val half = 1.dp.toPx() + level * 5.dp.toPx()
                                val x = start + (i + .5f) * step
                                drawLine(P.Text.copy(alpha = .65f), Offset(x, size.height / 2 - half),
                                    Offset(x, size.height / 2 + half), 1.5.dp.toPx(), StrokeCap.Round)
                            }
                        } else Text(if (selected) "OPEN PLAYER" else "TAP TO PLAY", color = P.TextSoft,
                            fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                repeat(cards.size) { index ->
                    Box(Modifier.padding(horizontal = 3.dp).size(if (pager.currentPage == index) 6.dp else 4.dp)
                        .background(if (pager.currentPage == index) P.Cyan else P.Text.copy(alpha = .25f), CircleShape))
                }
            }
        }
    }
}
