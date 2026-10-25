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

// Source: nyoom-engineering/base16-oxocarbon, base16-oxocarbon-dark.yaml.
object PipesPalette {
    val Background = Oxocarbon.Base00
    val Card = Oxocarbon.Base01
    val Raised = Oxocarbon.Base02
    val Line = Oxocarbon.Base03
    val TextSoft = Oxocarbon.Base04
    val Text = Oxocarbon.Base05
    val White = Oxocarbon.Base06
    val Teal = Oxocarbon.Base07
    val Cyan = Oxocarbon.Base08
    val Accent = Oxocarbon.Base08
    val Blue = Oxocarbon.Base09
    val Error = Oxocarbon.Base0A
    val Sky = Oxocarbon.Base0B
    val Favorite = Oxocarbon.Base0C
    val Success = Oxocarbon.Base0D
    val Violet = Oxocarbon.Base0E
    val Ice = Oxocarbon.Base0F
}

// Explicitly map every Material role: no default Material purple/red containers.
private val PipesScheme = darkColorScheme(
    primary = PipesPalette.Accent,
    onPrimary = PipesPalette.Background,
    primaryContainer = PipesPalette.Card,
    onPrimaryContainer = PipesPalette.Accent,
    inversePrimary = PipesPalette.Teal,
    secondary = PipesPalette.Blue,
    onSecondary = PipesPalette.Background,
    secondaryContainer = PipesPalette.Card,
    onSecondaryContainer = PipesPalette.Blue,
    tertiary = PipesPalette.Violet,
    onTertiary = PipesPalette.Background,
    tertiaryContainer = PipesPalette.Card,
    onTertiaryContainer = PipesPalette.Violet,
    background = PipesPalette.Background,
    onBackground = PipesPalette.Text,
    surface = PipesPalette.Card,
    onSurface = PipesPalette.Text,
    surfaceVariant = PipesPalette.Raised,
    onSurfaceVariant = PipesPalette.TextSoft,
    surfaceTint = Color.Transparent,
    inverseSurface = PipesPalette.TextSoft,
    inverseOnSurface = PipesPalette.Background,
    error = PipesPalette.Error,
    onError = PipesPalette.Background,
    errorContainer = PipesPalette.Card,
    onErrorContainer = PipesPalette.Error,
    outline = PipesPalette.Line,
    outlineVariant = PipesPalette.Raised,
    scrim = PipesPalette.Background,
    surfaceBright = PipesPalette.Raised,
    surfaceDim = PipesPalette.Background,
    surfaceContainer = PipesPalette.Card,
    surfaceContainerHigh = PipesPalette.Raised,
    surfaceContainerHighest = PipesPalette.Line,
    surfaceContainerLow = PipesPalette.Card,
    surfaceContainerLowest = PipesPalette.Background,
    primaryFixed = PipesPalette.Cyan,
    primaryFixedDim = PipesPalette.Teal,
    onPrimaryFixed = PipesPalette.Background,
    onPrimaryFixedVariant = PipesPalette.Background,
    secondaryFixed = PipesPalette.Ice,
    secondaryFixedDim = PipesPalette.Blue,
    onSecondaryFixed = PipesPalette.Background,
    onSecondaryFixedVariant = PipesPalette.Background,
    tertiaryFixed = PipesPalette.Violet,
    tertiaryFixedDim = PipesPalette.Violet,
    onTertiaryFixed = PipesPalette.Background,
    onTertiaryFixedVariant = PipesPalette.Background,
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
