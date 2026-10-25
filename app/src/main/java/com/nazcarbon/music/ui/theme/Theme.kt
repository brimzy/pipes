package com.nazcarbon.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PipesPalette {
    val Background = Color(0xFF161616)
    val Card = Color(0xFF1C1C1C)
    val Raised = Color(0xFF262626)
    val Line = Color(0xFF393939)
    val Text = Color(0xFFF2F4F8)
    val TextSoft = Color(0xFFADB5BD)
    val Accent = Color(0xFFF2F4F8)
}

private val PipesScheme = darkColorScheme(
    primary = PipesPalette.Accent,
    onPrimary = PipesPalette.Background,
    primaryContainer = PipesPalette.Raised,
    onPrimaryContainer = PipesPalette.Text,
    background = PipesPalette.Background,
    onBackground = PipesPalette.Text,
    surface = PipesPalette.Card,
    surfaceVariant = PipesPalette.Raised,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = PipesPalette.Background,
    surfaceContainerLow = PipesPalette.Card,
    surfaceContainer = PipesPalette.Card,
    surfaceContainerHigh = PipesPalette.Raised,
    surfaceContainerHighest = Color(0xFF161616),
    onSurface = PipesPalette.Text,
    onSurfaceVariant = PipesPalette.TextSoft,
    secondary = PipesPalette.Text,
    onSecondary = PipesPalette.Background,
    secondaryContainer = PipesPalette.Raised,
    onSecondaryContainer = PipesPalette.Text,
    tertiary = PipesPalette.TextSoft,
    outline = PipesPalette.Line,
    outlineVariant = PipesPalette.Raised,
)

@Composable
fun PipesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PipesScheme, content = content)
}
