package com.nazcarbon.music.data

internal object PlayNextQueue {
    fun existingUpcomingIndex(ids: List<String>, current: Int, trackId: String): Int? =
        ((current + 1) until ids.size).firstOrNull { ids[it] == trackId }

    /** Keep the rest of the shuffle order intact while making the selected song next. */
    fun prioritize(order: List<Int>, current: Int, next: Int): List<Int> {
        require(current != next && current in order && next in order)
        val remaining = order.filterNot { it == next }.toMutableList()
        remaining.add(remaining.indexOf(current) + 1, next)
        return remaining
    }
}
