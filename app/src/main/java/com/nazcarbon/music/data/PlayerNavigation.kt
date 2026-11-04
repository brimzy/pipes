package com.nazcarbon.music.data

internal object PlayerNavigation {
    fun origin(page: String, previous: String): String {
        val playerPages = setOf("Now playing", "Up next")
        return (if (page in playerPages) previous else page).takeUnless { it in playerPages } ?: "Home"
    }
}
