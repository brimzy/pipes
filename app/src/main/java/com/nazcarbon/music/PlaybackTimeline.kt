package com.nazcarbon.music

import androidx.media3.common.C
import androidx.media3.common.Player
import com.nazcarbon.music.data.PlaybackOrder

/** Traverse the actual shuffle order without wrapping repeat-all back to the start. */
internal fun Player.playbackOrder(): List<Int> = buildList {
    val timeline = currentTimeline
    var index = timeline.getFirstWindowIndex(shuffleModeEnabled)
    while (index != C.INDEX_UNSET && size < mediaItemCount) {
        add(index)
        index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
    }
}

internal fun Player.upcomingIndices(): List<Int> = PlaybackOrder.upcoming(playbackOrder(), currentMediaItemIndex)
