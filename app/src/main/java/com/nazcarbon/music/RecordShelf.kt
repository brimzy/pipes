package com.nazcarbon.music

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.ui.theme.PipesPalette
import kotlinx.coroutines.launch
import kotlin.math.abs

private val Paper = Color(0xFFF0ECE4)
private val PaperInk = Color(0xFF242322)
private val PaperSoft = Color(0xFF6A665F)

@Composable internal fun PipesWordmark() {
    val shine = rememberInfiniteTransition(label = "Pipes shine").animateFloat(
        -1f, 3f, infiniteRepeatable(tween(4200, delayMillis = 1800, easing = LinearEasing)), label = "shine sweep")
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("PIPES", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp,
            style = TextStyle(brush = Brush.linearGradient(
                listOf(PipesPalette.Accent, Color.White, PipesPalette.Accent),
                start = Offset(shine.value * 100f, 0f), end = Offset(shine.value * 100f + 70f, 24f))))
        Text(version, color = PipesPalette.TextSoft, fontWeight = FontWeight.Bold, fontSize = 10.sp,
            modifier = Modifier.padding(start = 9.dp))
    }
}

@Composable internal fun RecordShelf(
    title: String, subtitle: String, tracks: List<MusicTrack>, liked: Set<String>,
    onPlay: (MusicTrack) -> Unit, onLike: (MusicTrack) -> Unit,
    onQueue: (MusicTrack) -> Unit, onRadio: (MusicTrack) -> Unit, onSave: (MusicTrack) -> Unit,
) {
    val pager = rememberPagerState(pageCount = { tracks.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().background(Paper).padding(vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = PaperInk, fontFamily = FontFamily.Serif, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = PaperSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            }
            if (tracks.size > 1) {
                IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }, enabled = pager.currentPage > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous record", tint = PaperSoft)
                }
                IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(tracks.lastIndex)) } }, enabled = pager.currentPage < tracks.lastIndex) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next record", tint = PaperSoft)
                }
            }
        }
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Album, null, tint = PaperSoft, modifier = Modifier.size(64.dp))
                    Text("Your next favorite belongs here.", color = PaperSoft, modifier = Modifier.padding(top = 14.dp))
                }
            }
        } else {
            HorizontalPager(state = pager, key = { tracks[it].id }, contentPadding = PaddingValues(horizontal = 22.dp),
                pageSpacing = 18.dp, verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()) { index ->
                val track = tracks[index]
                val settle = remember { Animatable(0f) }
                LaunchedEffect(pager.settledPage) {
                    if (pager.settledPage == index) {
                        settle.snapTo(-2.2f)
                        settle.animateTo(0f, spring(dampingRatio = .38f, stiffness = 180f))
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    RecordSleeve(track, Modifier.fillMaxWidth().height(185.dp).graphicsLayer {
                        val offset = (pager.currentPage - index) + pager.currentPageOffsetFraction
                        rotationZ = offset.coerceIn(-1f, 1f) * -7f + settle.value
                        rotationY = offset.coerceIn(-1f, 1f) * -12f
                        translationY = abs(offset).coerceAtMost(1f) * 12.dp.toPx()
                        cameraDistance = 20f * density
                    })
                    HorizontalDivider(color = Color(0xFFD5CFC5), modifier = Modifier.padding(bottom = 14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("RECORD / ${index + 1}", color = PaperSoft, fontSize = 10.sp, letterSpacing = 1.6.sp)
                        Text("${index + 1} / ${tracks.size}", color = PaperSoft, fontSize = 10.sp)
                    }
                    Text(track.title, color = PaperInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        fontSize = 27.sp, lineHeight = 31.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp).heightIn(min = 34.dp))
                    Text(track.artist, color = PaperSoft, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp))
                    val duration = if (track.durationSeconds > 0) "${track.durationSeconds / 60} min ${track.durationSeconds % 60} sec" else "Duration available during playback"
                    Text(duration, color = PaperSoft, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                    Text(recordDescription(track), color = PaperSoft, fontSize = 13.sp, lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 12.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(if (track.pageUrl.startsWith("content://")) "On device" else "Online", if (track.id in liked) "In your collection" else "Discovery").forEach {
                            Text(it, color = PaperSoft, fontSize = 10.sp, modifier = Modifier.clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFFE5DFD5)).padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { onPlay(track) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = PaperInk, contentColor = Paper)) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)); Text("Play record")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onLike(track) }) { Icon(if (track.id in liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            if (track.id in liked) "Unlike song" else "Like song", tint = PaperInk) }
                        var menu by remember(track.id) { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Record options", tint = PaperInk) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Add to queue") }, { menu = false; onQueue(track) })
                                DropdownMenuItem({ Text("Start song radio") }, { menu = false; onRadio(track) }, enabled = !track.pageUrl.startsWith("content://"))
                                DropdownMenuItem({ Text("Save to playlist") }, { menu = false; onSave(track) })
                            }
                        }
                    }
                }
            }
            if (tracks.size > 1) Text("SWIPE TO BROWSE · SCROLL TO EXPLORE", color = PaperSoft, fontSize = 9.sp,
                letterSpacing = 1.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
        }
    }
}

internal fun recordDescription(track: MusicTrack): String = buildString {
    append(track.title.ifBlank { "Untitled recording" })
    if (track.artist.isNotBlank()) append(" by ${track.artist}")
    append(". ")
    append(if (track.pageUrl.startsWith("content://")) "Saved on this device for your listening sessions." else "A recording to spend some time with. Play it now, add it to your queue, or follow its sound into song radio.")
    if (track.audioQuality.isNotBlank()) append(" ${track.audioQuality}.")
}

@Composable private fun RecordSleeve(track: MusicTrack, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val sleeve = minOf(maxWidth * .60f, 170.dp)
        Box(Modifier.size(sleeve + 10.dp).offset(x = sleeve * .30f).shadow(12.dp, CircleShape)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.sweepGradient(listOf(Color(0xFF080808), Color(0xFF383838), Color(0xFF080808), Color(0xFF272727), Color(0xFF080808))))
                for (i in 0..20) drawCircle(Color.White.copy(alpha = .06f), radius = size.minDimension * (.22f + i * .012f), style = Stroke(.7.dp.toPx()))
            }
            ArtworkImage(track.thumbnailUrl, null, Modifier.align(Alignment.Center).fillMaxSize(.30f).clip(CircleShape))
            Box(Modifier.align(Alignment.Center).size(5.dp).background(Paper, CircleShape))
        }
        Box(Modifier.size(sleeve).offset(x = -sleeve * .12f).graphicsLayer { rotationZ = -3f }
            .shadow(14.dp, RoundedCornerShape(2.dp)).background(Color(0xFFE2DACE))) {
            ArtworkImage(track.thumbnailUrl, "Record sleeve for ${track.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = .4f)))
        }
    }
}

@Composable internal fun SwipeArtwork(track: MusicTrack, modifier: Modifier, previous: () -> Unit, next: () -> Unit, hasNext: Boolean) {
    var drag by remember(track.id) { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(drag, spring(dampingRatio = .55f, stiffness = 500f), label = "sleeve swipe")
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val latestPrevious by rememberUpdatedState(previous)
    val latestNext by rememberUpdatedState(next)
    val latestHasNext by rememberUpdatedState(hasNext)
    ArtworkImage(track.thumbnailUrl, "Artwork for ${track.title}. Swipe left for next, right for previous.",
        modifier.pointerInput(track.id, threshold) {
            detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f }, onDragEnd = {
                if (drag < -threshold && latestHasNext) latestNext()
                else if (drag > threshold) latestPrevious()
                drag = 0f
            }) { change, amount -> change.consume(); drag = (drag + amount).coerceIn(-threshold * 2, threshold * 2) }
        }.graphicsLayer { translationX = offset * .45f; rotationZ = offset / threshold * 4f }
            .clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
}
