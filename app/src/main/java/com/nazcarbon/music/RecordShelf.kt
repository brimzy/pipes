package com.nazcarbon.music

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.platform.LocalConfiguration
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

private val ShelfBackground = PipesPalette.Background
private val ShelfText = PipesPalette.Text
private val ShelfMuted = PipesPalette.TextSoft

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
    val compact = LocalConfiguration.current.screenHeightDp < 700
    val pager = rememberPagerState(pageCount = { tracks.size })
    val scope = rememberCoroutineScope()
    val selected = tracks.getOrNull(pager.currentPage)
    Column(Modifier.fillMaxWidth().background(ShelfBackground).padding(top = 16.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), color = PipesPalette.Accent, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                Text(selected?.title ?: "Your collection", color = ShelfText, fontFamily = FontFamily.Serif,
                    fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                Text(selected?.artist ?: subtitle, color = ShelfMuted, fontSize = 12.sp, lineHeight = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            if (tracks.size > 1) {
                IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }, enabled = pager.currentPage > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous record", tint = ShelfMuted)
                }
                IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(tracks.lastIndex)) } }, enabled = pager.currentPage < tracks.lastIndex) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next record", tint = ShelfMuted)
                }
            }
        }
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Album, null, tint = ShelfMuted, modifier = Modifier.size(64.dp))
                    Text("Your next favorite belongs here.", color = ShelfMuted, modifier = Modifier.padding(top = 14.dp))
                }
            }
        } else {
            HorizontalPager(state = pager, key = { tracks[it].id }, contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 12.dp, verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { index ->
                val track = tracks[index]
                val settle = remember { Animatable(0f) }
                var lastOffset by remember { mutableFloatStateOf(0f) }
                var hasDragged by remember { mutableStateOf(false) }
                LaunchedEffect(pager.isScrollInProgress) {
                    if (pager.isScrollInProgress) {
                        hasDragged = true
                        settle.snapTo(0f)
                        snapshotFlow { pager.currentPage - index + pager.currentPageOffsetFraction }.collect { lastOffset = it }
                    } else if (hasDragged && pager.settledPage == index) {
                        settle.snapTo(if (lastOffset < 0) -1.6f else 1.6f)
                        settle.animateTo(0f, spring(dampingRatio = .48f, stiffness = 220f))
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    RecordSleeve(track, Modifier.fillMaxWidth().height(if (compact) 120.dp else 150.dp).graphicsLayer {
                        val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                        rotationY = offset * -8f
                        cameraDistance = 24f * density
                    })
                    HorizontalDivider(color = PipesPalette.Line, modifier = Modifier.padding(bottom = 12.dp))
                    Column(Modifier.fillMaxWidth().graphicsLayer {
                        val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                        translationX = -offset * size.width * .08f
                        rotationY = offset * 14f
                        cameraDistance = 24f * density
                        alpha = 1f - abs(offset) * .3f
                    }.clip(RoundedCornerShape(14.dp))
                        .background(PipesPalette.Raised).border(1.dp, PipesPalette.Line, RoundedCornerShape(14.dp))
                        .padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RECORDING", color = PipesPalette.Accent, fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 1.4.sp)
                            Text("${index + 1} / ${tracks.size}", color = ShelfMuted, fontSize = 10.sp, lineHeight = 12.sp)
                        }
                        Text(track.title, color = ShelfText, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            fontSize = 22.sp, lineHeight = 27.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp).heightIn(min = 28.dp).graphicsLayer {
                                rotationZ = settle.value * 1.8f
                                translationY = -abs(settle.value) * 2.dp.toPx()
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)
                            })
                        Text(track.artist, color = ShelfMuted, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp))
                        val duration = if (track.durationSeconds > 0) "${track.durationSeconds / 60} min ${track.durationSeconds % 60} sec" else "Duration available during playback"
                        Text(duration, color = ShelfMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
                        key(track.id) {
                            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
                                Text(recordDescription(track), color = ShelfMuted, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = { onPlay(track) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = PipesPalette.Accent, contentColor = PipesPalette.Background)) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text("Play")
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { onLike(track) }) { Icon(if (track.id in liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (track.id in liked) "Unlike song" else "Like song", tint = if (track.id in liked) PipesPalette.Accent else ShelfText) }
                            var menu by remember(track.id) { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Record options", tint = ShelfText) }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem({ Text("Add to queue") }, { menu = false; onQueue(track) })
                                    DropdownMenuItem({ Text("Start song radio") }, { menu = false; onRadio(track) }, enabled = !track.pageUrl.startsWith("content://"))
                                    DropdownMenuItem({ Text("Save to playlist") }, { menu = false; onSave(track) })
                                }
                            }
                        }
                    }
                }
            }
            if (tracks.size > 1) Text("SWIPE TO BROWSE", color = ShelfMuted, fontSize = 9.sp, lineHeight = 12.sp,
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
    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomCenter) {
        val sleeve = minOf(maxWidth * .50f, maxHeight - 8.dp, 140.dp)
        VinylDisc(track.thumbnailUrl, Modifier.size(sleeve * .97f).align(Alignment.BottomCenter)
            .offset(x = sleeve * .36f, y = (-7).dp))
        Box(Modifier.size(sleeve).offset(x = -sleeve * .17f, y = (-5).dp)
            .shadow(12.dp, RoundedCornerShape(1.dp)).background(PipesPalette.Raised)) {
            ArtworkImage(track.thumbnailUrl, "Record sleeve for ${track.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = .12f)))
        }
    }
}

@Composable internal fun VinylDisc(label: String, modifier: Modifier) {
    Box(modifier.clip(CircleShape).drawWithCache {
        val sheen = Brush.sweepGradient(listOf(Color(0xFF060606), Color(0xFF272727), Color(0xFF080808), Color(0xFF383838), Color(0xFF060606)))
        onDrawBehind {
            drawCircle(sheen)
            for (i in 0..32) drawCircle(Color.White.copy(alpha = .075f), radius = size.minDimension * (.18f + i * .0095f), style = Stroke(.55.dp.toPx()))
            drawCircle(PipesPalette.Line, style = Stroke(1.dp.toPx()))
        }
    }, contentAlignment = Alignment.Center) {
        ArtworkImage(label, null, Modifier.fillMaxSize(.30f).clip(CircleShape).background(PipesPalette.Accent))
        Box(Modifier.size(5.dp).background(PipesPalette.Background, CircleShape).border(.5.dp, PipesPalette.TextSoft, CircleShape))
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
