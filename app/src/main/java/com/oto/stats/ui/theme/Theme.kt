package com.oto.stats.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OtoColorScheme = darkColorScheme(
    primary = OtoColors.Accent,
    onPrimary = OtoColors.OnAccent,
    primaryContainer = OtoColors.AccentSubtle,
    onPrimaryContainer = OtoColors.Accent,

    // Secondary and tertiary stay neutral so no stray hues appear.
    secondary = OtoColors.TextSecondary,
    onSecondary = OtoColors.Canvas,
    secondaryContainer = OtoColors.Surface3,
    onSecondaryContainer = OtoColors.TextPrimary,
    tertiary = OtoColors.TextSecondary,
    onTertiary = OtoColors.Canvas,
    tertiaryContainer = OtoColors.Surface3,
    onTertiaryContainer = OtoColors.TextPrimary,

    background = OtoColors.Canvas,
    onBackground = OtoColors.TextPrimary,
    surface = OtoColors.Canvas,
    onSurface = OtoColors.TextPrimary,
    surfaceVariant = OtoColors.Surface2,
    onSurfaceVariant = OtoColors.TextSecondary,
    surfaceContainerLowest = OtoColors.Canvas,
    surfaceContainerLow = OtoColors.Surface1,
    surfaceContainer = OtoColors.Surface1,
    surfaceContainerHigh = OtoColors.Surface2,
    surfaceContainerHighest = OtoColors.Surface3,

    outline = OtoColors.TextTertiary,
    outlineVariant = OtoColors.Hairline,

    error = OtoColors.Error,
    onError = OtoColors.Canvas,
    errorContainer = OtoColors.ErrorSubtle,
    onErrorContainer = OtoColors.Error,
)

/** Oto is dark-only by design. */
@Composable
fun OtoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OtoColorScheme,
        typography = OtoTypography,
        content = content,
    )
}
