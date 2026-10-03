package com.nazcarbon.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PipesScheme = darkColorScheme(
    primary = Color(0xFF04D1F9),
    onPrimary = Color(0xFF090B0F),
    background = Color(0xFF090B0F),
    surface = Color(0xFF11161D),
    surfaceVariant = Color(0xFF1B222C),
    onSurface = Color(0xFFF2F4F8),
    onSurfaceVariant = Color(0xFF9BA2A8),
    secondary = Color(0xFF3DDBD9),
    outline = Color(0xFF393939),
)

@Composable
fun PipesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PipesScheme, content = content)
}
