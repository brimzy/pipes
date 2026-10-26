package com.nazcarbon.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.ui.theme.PipesPalette as P

@Composable
internal fun CurrentSongBar(
    track: MusicTrack, playing: Boolean, position: Long, duration: Long,
    seekable: Boolean, onSeek: (Long) -> Unit, openPlayer: () -> Unit, toggle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PipesSpace.Page, vertical = 4.dp)
        .carbonSurface(RoundedCornerShape(12.dp), 4.dp)) {
        Row(Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open full player", onClick = openPlayer)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(track.thumbnailUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, color = P.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = P.TextSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = toggle) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (playing) "Pause" else "Play", tint = P.Text)
            }
        }
        key(track.id) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                PlayerSeekControls(position, duration, seekable, playing, onSeek)
            }
        }
    }
}
