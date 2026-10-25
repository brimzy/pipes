package com.nazcarbon.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.nazcarbon.music.data.MusicTrack
import com.nazcarbon.music.ui.theme.PipesPalette as P

@Composable
internal fun MusicArtworkCard(track: MusicTrack, onPlay: (MusicTrack) -> Unit,
    onLike: (MusicTrack) -> Unit, onRadio: (MusicTrack) -> Unit, onSave: (MusicTrack) -> Unit) {
    val add = LocalAddToQueue.current
    val remove = LocalRemoveFromQueue.current
    QueueGesture(track, { onPlay(track) }, { add(track) }, { remove(track) },
        { onLike(track) }, { onRadio(track) }, { onSave(track) }) {
        Column {
            ArtworkImage(track.thumbnailUrl, null, Modifier.fillMaxWidth().aspectRatio(1f)
                .artworkSurface(RoundedCornerShape(PipesSpace.ArtworkText)), contentScale = ContentScale.Crop)
            Text(track.title, color = P.SongTitle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = PipesSpace.ArtworkText))
            Text(track.artist, color = P.TextSoft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = PipesSpace.Metadata))
        }
    }
}
