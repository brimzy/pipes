package com.nazcarbon.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontSynthesis
import com.nazcarbon.music.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PipesPalette {
    val Background = Color(0xFF161616)
    val Card = Color(0xFF262626)
    val Raised = Color(0xFF393939)
    val Line = Color(0xFF525252)
    val Text = Color(0xFFF2F2F2)
    val TextSoft = Color(0xFFD0D0D0)
    val Accent = Color(0xFF78A9FF)
    val Cyan = Color(0xFF3DDBD9)
    val Violet = Color(0xFFBE95FF)
    val Error = Color(0xFFFF7EB6)
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
    surfaceContainerHighest = PipesPalette.Line,
    onSurface = PipesPalette.Text,
    onSurfaceVariant = PipesPalette.TextSoft,
    secondary = PipesPalette.Cyan,
    onSecondary = PipesPalette.Background,
    secondaryContainer = PipesPalette.Raised,
    onSecondaryContainer = PipesPalette.Text,
    tertiary = PipesPalette.Violet,
    error = PipesPalette.Error,
    onError = PipesPalette.Background,
    outline = PipesPalette.Line,
    outlineVariant = PipesPalette.Raised,
)

val Libron = FontFamily(
    Font(R.font.libron_regular, FontWeight.Normal),
    Font(R.font.libron_bold, FontWeight.Bold),
    Font(R.font.libron_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.libron_bolditalic, FontWeight.Bold, FontStyle.Italic),
)

private val PipesTypography = Typography().run {
    fun androidx.compose.ui.text.TextStyle.libron() = copy(fontFamily = Libron, fontSynthesis = FontSynthesis.None)
    copy(
        displayLarge = displayLarge.libron(), displayMedium = displayMedium.libron(), displaySmall = displaySmall.libron(),
        headlineLarge = headlineLarge.libron(), headlineMedium = headlineMedium.libron(), headlineSmall = headlineSmall.libron(),
        titleLarge = titleLarge.libron(), titleMedium = titleMedium.libron(), titleSmall = titleSmall.libron(),
        bodyLarge = bodyLarge.libron(), bodyMedium = bodyMedium.libron(), bodySmall = bodySmall.libron(),
        labelLarge = labelLarge.libron(), labelMedium = labelMedium.libron(), labelSmall = labelSmall.libron(),
    )
}

@Composable
fun PipesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PipesScheme, typography = PipesTypography, content = content)
}
