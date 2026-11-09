package com.nazcarbon.music.data

internal object PlaybackOrder {
    fun upcoming(order: List<Int>, current: Int): List<Int> {
        val position = order.indexOf(current)
        return if (position < 0) emptyList() else order.drop(position + 1)
    }

    fun existingUpcoming(ids: List<String>, order: List<Int>, current: Int, id: String): Int? =
        upcoming(order, current).firstOrNull { ids[it] == id }

    /** Moves reorder the timeline without resetting the playing recording or its position. */
    fun movesTo(order: List<Int>): List<Pair<Int, Int>> {
        require(order.toSet() == order.indices.toSet())
        val entries = order.indices.toMutableList()
        return buildList {
            order.forEachIndexed { destination, original ->
                val from = entries.indexOf(original)
                if (from != destination) {
                    entries.add(destination, entries.removeAt(from))
                    add(from to destination)
                }
            }
        }
    }

    fun sequence(tracks: List<MusicTrack>, selected: MusicTrack, shuffle: Boolean): List<MusicTrack> {
        val unique = tracks.distinctBy { it.id }
        return if (shuffle) listOf(selected) + unique.filterNot { it.id == selected.id }
        else unique.dropWhile { it.id != selected.id }.ifEmpty { listOf(selected) }
    }
}
