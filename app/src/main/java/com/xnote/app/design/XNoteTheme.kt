package com.xnote.app.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// -- Constants

private val LightColorScheme = lightColorScheme(
    primary = XNoteLightPrimaryColor,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE8AD),
    onPrimaryContainer = Color(0xFF493500),
    secondary = Color(0xFF636366),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF636366),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5E5EA),
    onTertiaryContainer = Color(0xFF1C1C1E),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF636366),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF2C2C2E),
    inverseOnSurface = Color(0xFFF2F2F7),
    inversePrimary = XNoteDarkPrimaryColor,
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFFD1D1D6),
    error = Color(0xFFC9342B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF7D211B),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE5E5EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F9FB),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFEBEBF0),
    surfaceContainerHighest = Color(0xFFE5E5EA),
)

private val DarkColorScheme = darkColorScheme(
    primary = XNoteDarkPrimaryColor,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF493500),
    onPrimaryContainer = Color(0xFFFFE8AD),
    secondary = Color(0xFFAEAEB2),
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFF2F2F7),
    tertiary = Color(0xFFAEAEB2),
    onTertiary = Color(0xFF1C1C1E),
    tertiaryContainer = Color(0xFF2C2C2E),
    onTertiaryContainer = Color(0xFFF2F2F7),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF1C1C1E),
    inversePrimary = XNoteLightPrimaryColor,
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF6961),
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF541C19),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38383A),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF242426),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF38383A),
)

// -- Composables

@Composable
fun XNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean? = null,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    XNoteInteractionSettingsProvider(
        reduceMotion = reduceMotion,
        highContrast = highContrast,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = XNoteTypography,
            content = content,
        )
    }
}
