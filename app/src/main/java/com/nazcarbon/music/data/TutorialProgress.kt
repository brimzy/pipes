package com.nazcarbon.music.data

import org.json.JSONObject

/** Versioned separately from app updates so this tour is offered once, including on upgrade. */
data class TutorialProgress(
    val lesson: Int = 0,
    val mode: Mode = Mode.WELCOME,
    val completed: Boolean = false,
) {
    enum class Mode { WELCOME, GUIDE, PRACTICE, CLOSED }

    fun open() = copy(lesson = if (completed) 0 else lesson, mode = Mode.GUIDE, completed = false)
    fun practice() = copy(mode = Mode.PRACTICE)
    fun pause() = copy(mode = Mode.CLOSED)
    fun back() = copy(lesson = (lesson - 1).coerceAtLeast(0), mode = Mode.GUIDE)
    fun next() = if (lesson == LESSON_COUNT - 1) finish() else copy(lesson = lesson + 1, mode = Mode.GUIDE)
    fun finish() = copy(lesson = LESSON_COUNT - 1, mode = Mode.CLOSED, completed = true)
    fun encode(): String = JSONObject().put("lesson", lesson).put("mode", mode.name).put("completed", completed).toString()

    companion object {
        const val LESSON_COUNT = 7
        const val PREFERENCE_KEY = "tutorial_5_1_55"

        fun decode(value: String?): TutorialProgress {
            if (value == null) return TutorialProgress()
            return runCatching {
                val json = JSONObject(value)
                TutorialProgress(json.getInt("lesson").coerceIn(0, LESSON_COUNT - 1),
                    Mode.valueOf(json.getString("mode")), json.optBoolean("completed"))
            }.getOrElse { TutorialProgress(mode = Mode.CLOSED) }
        }
    }
}
