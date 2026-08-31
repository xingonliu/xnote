package com.xnote.app.data.settings

import com.xnote.app.domain.model.AppSettings
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.defaultAppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// -- Type Definitions

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setDefaultBackground(background: BackgroundKey)
}

class InMemoryAppSettingsRepository(
    initialSettings: AppSettings = defaultAppSettings(),
) : AppSettingsRepository {
    private val state = MutableStateFlow(initialSettings)

    override val settings: Flow<AppSettings> = state

    override suspend fun setDefaultBackground(background: BackgroundKey) {
        state.value = state.value.copy(defaultBackground = background)
    }
}
