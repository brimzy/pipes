package com.nazcarbon.music.data

object UpdatePromptPolicy {
    const val REMINDER_INTERVAL_MS = 5 * 24 * 60 * 60 * 1000L

    fun shouldPrompt(installed: Long, available: Long?, remindAfter: Long, now: Long, manual: Boolean = false): Boolean =
        available != null && available > installed && (manual || now >= remindAfter)

    fun nextReminder(now: Long): Long = now + REMINDER_INTERVAL_MS
}
