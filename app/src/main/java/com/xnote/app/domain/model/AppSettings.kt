package com.xnote.app.domain.model

// -- Type Definitions

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class AppSettings(
    val defaultBackground: BackgroundKey,
    val themeMode: ThemeMode,
    val markdownShortcutsEnabled: Boolean,
)

fun defaultAppSettings(): AppSettings = AppSettings(
    defaultBackground = defaultBackgroundKey(),
    themeMode = ThemeMode.System,
    markdownShortcutsEnabled = true,
)
