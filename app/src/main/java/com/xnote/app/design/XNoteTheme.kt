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
    onPrimary = Color(0xFF2D1B00),
    primaryContainer = Color(0xFFFFE08A),
    onPrimaryContainer = Color(0xFF2C2100),
    secondary = Color(0xFF625B4D),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F4EC),
    onBackground = Color(0xFF211F1A),
    surface = Color(0xFFFFFCF4),
    onSurface = Color(0xFF211F1A),
    surfaceVariant = Color(0xFFE9E3D7),
    onSurfaceVariant = Color(0xFF4C473F),
    outline = Color(0xFF7D766A),
    error = Color(0xFFBA1A1A),
)

private val DarkColorScheme = darkColorScheme(
    primary = XNoteDarkPrimaryColor,
    onPrimary = Color(0xFF463700),
    primaryContainer = Color(0xFF645000),
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFFCCC3B3),
    onSecondary = Color(0xFF343027),
    background = Color(0xFF151412),
    onBackground = Color(0xFFE9E2D8),
    surface = Color(0xFF1D1C19),
    onSurface = Color(0xFFE9E2D8),
    surfaceVariant = Color(0xFF4C473F),
    onSurfaceVariant = Color(0xFFD0C7BA),
    outline = Color(0xFF978F82),
    error = Color(0xFFFFB4AB),
)

// -- Composables

@Composable
fun XNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = XNoteTypography,
        content = content,
    )
}
