package com.nazcarbon.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Neutral surfaces, with blue reserved for focus and selected controls.
private val PipesScheme = darkColorScheme(
    primary = Color(0xFF04D1F9),
    onPrimary = Color(0xFF080808),
    primaryContainer = Color(0xFF242424),
    onPrimaryContainer = Color(0xFFF2F4F8),
    background = Color(0xFF080808),
    onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF202020),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF080808),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF181818),
    surfaceContainerHigh = Color(0xFF202020),
    surfaceContainerHighest = Color(0xFF282828),
    onSurface = Color(0xFFF2F4F8),
    onSurfaceVariant = Color(0xFFE6E8EC),
    secondary = Color(0xFFF2F4F8),
    onSecondary = Color(0xFF080808),
    secondaryContainer = Color(0xFF252525),
    onSecondaryContainer = Color(0xFFF2F4F8),
    tertiary = Color(0xFFE6E8EC),
    outline = Color(0xFF393939),
    outlineVariant = Color(0xFF282828),
)

@Composable
fun PipesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PipesScheme, content = content)
}
