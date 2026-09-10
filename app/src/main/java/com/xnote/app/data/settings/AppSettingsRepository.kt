package com.xnote.app.data.settings

import com.xnote.app.domain.model.AppSettings
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.ThemeMode
import com.xnote.app.domain.model.AppFontSize
import com.xnote.app.domain.model.ReadingLayout
import com.xnote.app.domain.model.defaultAppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// -- Type Definitions

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setDefaultBackground(background: BackgroundKey)

    suspend fun setMarkdownShortcutsEnabled(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setReduceMotion(enabled: Boolean)
    suspend fun setHighContrast(enabled: Boolean)
    suspend fun setFontSize(size: AppFontSize)
    suspend fun setReadingLayout(layout: ReadingLayout)
}

class InMemoryAppSettingsRepository(
    initialSettings: AppSettings = defaultAppSettings(),
) : AppSettingsRepository {
    private val state = MutableStateFlow(initialSettings)

    override val settings: Flow<AppSettings> = state

    override suspend fun setDefaultBackground(background: BackgroundKey) {
        state.value = state.value.copy(defaultBackground = background)
    }

    override suspend fun setMarkdownShortcutsEnabled(enabled: Boolean) {
        state.value = state.value.copy(markdownShortcutsEnabled = enabled)
    }

    override suspend fun setThemeMode(mode: ThemeMode) { state.value = state.value.copy(themeMode = mode) }
    override suspend fun setReduceMotion(enabled: Boolean) { state.value = state.value.copy(reduceMotion = enabled) }
    override suspend fun setHighContrast(enabled: Boolean) { state.value = state.value.copy(highContrast = enabled) }
    override suspend fun setFontSize(size: AppFontSize) { state.value = state.value.copy(fontSize = size) }
    override suspend fun setReadingLayout(layout: ReadingLayout) { state.value = state.value.copy(readingLayout = layout) }
}
