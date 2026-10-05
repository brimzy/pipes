package com.nazcarbon.music.data

import org.json.JSONArray

object SearchHistory {
    fun add(history: List<String>, query: String): List<String> {
        val clean = query.trim()
        if (clean.isEmpty()) return history
        return (listOf(clean) + history.filterNot { it.equals(clean, ignoreCase = true) }).take(30)
    }

    fun encode(history: List<String>): String = JSONArray(history).toString()

    fun decode(value: String): List<String> = runCatching {
        val array = JSONArray(value)
        (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.trim()?.takeIf(String::isNotEmpty) }
            .distinctBy { it.lowercase() }.take(30)
    }.getOrDefault(emptyList())
}
