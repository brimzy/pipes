package com.nazcarbon.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// NazCarbon DashboardView.qml: black canvas, near-black cards, off-white content.
object PipesPalette {
    val Background = Color(0xFF000000)
    val Card = Color(0xFF090909)
    val Raised = Color(0xFF111111)
    val Line = Color(0xFF262626)
    val Text = Color(0xFFF2F4F8)
    val TextSoft = Color(0xFFDDE1E6)
    val Accent = Color(0xFF04D1F9)
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
