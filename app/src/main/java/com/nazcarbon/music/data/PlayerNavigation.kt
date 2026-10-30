package com.nazcarbon.music.data

/** The queue belongs to the player; it must never become the place we minimize back to. */
internal object PlayerNavigation {
    fun origin(page: String, previous: String): String {
        val playerPages = setOf("Now playing", "Up next")
        return (if (page in playerPages) previous else page).takeUnless { it in playerPages } ?: "Home"
    }
}
