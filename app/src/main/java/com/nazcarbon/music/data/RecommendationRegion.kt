package com.nazcarbon.music.data

object RecommendationRegion {
    fun language(track: MusicTrack): String {
        val text = track.title + " " + track.artist
        return when {
            text.any { it in '\uac00'..'\ud7af' } -> "ko"
            text.any { it in '\u3040'..'\u30ff' } -> "ja"
            text.any { it in '\u0900'..'\u097f' } -> "hi"
            text.any { it in '\u0400'..'\u04ff' } -> "ru"
            text.any { it in '\u0600'..'\u06ff' } -> "ar"
            text.any { it in '\u4e00'..'\u9fff' } -> "zh"
            else -> "en"
        }
    }
    fun country(preference: String): String? = preference.uppercase().takeIf {
        it in java.util.Locale.getISOCountries().toSet()
    }
}
