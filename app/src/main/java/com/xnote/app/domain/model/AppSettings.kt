package com.xnote.app.domain.model

// -- Type Definitions

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class AppSettings(
    val defaultBackgroundKey: String,
    val themeMode: ThemeMode,
) {
    fun referencedAttachmentIds(): Set<String> {
        val attachmentId = parseBackgroundKey(defaultBackgroundKey)?.attachmentIdOrNull()
        return if (attachmentId == null) emptySet() else setOf(attachmentId)
    }
}

fun defaultAppSettings(): AppSettings = AppSettings(
    defaultBackgroundKey = defaultBackgroundKey().encode(),
    themeMode = ThemeMode.System,
)
