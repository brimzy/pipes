package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class UpdatePromptPolicyTest {
    private val now = 1_800_000_000_000L

    @Test fun `automatic prompt only appears for a newer available version`() {
        assertFalse(UpdatePromptPolicy.shouldPrompt(30025, null, 0, now))
        assertFalse(UpdatePromptPolicy.shouldPrompt(30025, 30025, 0, now))
        assertFalse(UpdatePromptPolicy.shouldPrompt(30025, 30024, 0, now))
        assertTrue(UpdatePromptPolicy.shouldPrompt(30025, 30026, 0, now))
    }
    @Test fun `declined update remains hidden for five full days`() {
        val savedDeadline = UpdatePromptPolicy.nextReminder(now)
        assertEquals(now + 432_000_000L, savedDeadline)
        assertFalse(UpdatePromptPolicy.shouldPrompt(30025, 30026, savedDeadline, now))
        assertFalse(UpdatePromptPolicy.shouldPrompt(30025, 30026, savedDeadline, savedDeadline - 1))
        assertTrue(UpdatePromptPolicy.shouldPrompt(30025, 30026, savedDeadline, savedDeadline))
    }
    @Test fun `newer release does not override an existing snooze`() {
        val savedDeadline = UpdatePromptPolicy.nextReminder(now)
        assertFalse(UpdatePromptPolicy.shouldPrompt(30025, 30027, savedDeadline, now + 86400000))
        assertTrue(UpdatePromptPolicy.shouldPrompt(30025, 30027, savedDeadline, savedDeadline))
    }
    @Test fun `manual check bypasses snooze but still needs a newer version`() {
        val savedDeadline = UpdatePromptPolicy.nextReminder(now)
        assertTrue(UpdatePromptPolicy.shouldPrompt(30025, 30026, savedDeadline, now, manual = true))
        assertFalse(UpdatePromptPolicy.shouldPrompt(30025, null, savedDeadline, now, manual = true))
        assertFalse(UpdatePromptPolicy.shouldPrompt(30025, 30025, savedDeadline, now, manual = true))
    }
}
