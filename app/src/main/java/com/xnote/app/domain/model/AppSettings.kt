package com.xnote.app.domain.model

// -- Type Definitions

enum class ThemeMode {
    System,
    Light,
    Dark,
}

enum class AppFontSize(val scale: Float) { Standard(1f), Large(1.15f), ExtraLarge(1.3f) }

enum class ReadingLayout(val widthDp: Int, val lineHeightScale: Float) {
    Compact(560, 1f), Standard(840, 1f), Relaxed(640, 1.25f),
}

data class AppSettings(
    val defaultBackground: BackgroundKey,
    val themeMode: ThemeMode,
    val markdownShortcutsEnabled: Boolean,
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    val fontSize: AppFontSize = AppFontSize.Standard,
    val readingLayout: ReadingLayout = ReadingLayout.Standard,
)

// -- Functions

fun defaultAppSettings(): AppSettings = AppSettings(
    defaultBackground = defaultBackgroundKey(),
    themeMode = ThemeMode.System,
    markdownShortcutsEnabled = true,
)
