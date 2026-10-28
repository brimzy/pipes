package com.nazcarbon.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.ui.theme.PipesPalette as P

@Composable
internal fun CurrentSongBar(
    track: MusicTrack, playing: Boolean, position: Long, duration: Long,
    seekable: Boolean, buffering: Boolean, onSeek: (Long) -> Unit,
    openPlayer: () -> Unit, toggle: () -> Unit, onClose: () -> Unit,
    previous: () -> Unit, next: () -> Unit, hasNext: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PipesSpace.Page, vertical = 4.dp)
        .playerSurface(RoundedCornerShape(20.dp))) {
        Row(Modifier.fillMaxWidth().trackSwipe(track.id, hasNext, previous, next).padding(start = 8.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = "Open full player", onClick = openPlayer)
                .padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(track.thumbnailUrl, null,
                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = P.TextSoft, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
            IconButton(onClick = toggle, modifier = Modifier.size(48.dp)) {
                Box(Modifier.size(36.dp).playerSurface(CircleShape, 2.dp), contentAlignment = Alignment.Center) {
                    if (buffering) CircularProgressIndicator(Modifier.size(18.dp), color = P.Accent, strokeWidth = 2.dp)
                    else Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "Pause" else "Play", tint = P.Text, modifier = Modifier.size(22.dp))
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, "Stop playback and close player", tint = P.TextSoft, modifier = Modifier.size(18.dp))
            }
        }
        key(track.id) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                PlayerSeekControls(position, duration, seekable, playing, onSeek, compact = true)
            }
        }
    }
}
