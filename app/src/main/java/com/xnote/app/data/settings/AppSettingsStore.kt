package com.xnote.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xnote.app.domain.model.AppSettings
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.ThemeMode
import com.xnote.app.domain.model.defaultBackgroundKey
import com.xnote.app.domain.model.encode
import com.xnote.app.domain.model.parseBackgroundKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// -- Constants

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "xnote_settings",
)

private val DefaultBackgroundKey = stringPreferencesKey("default_background_key")
private val ThemeModeKey = stringPreferencesKey("theme_mode")

// -- Type Definitions

class AppSettingsStore(
    context: Context,
) : AppSettingsRepository {
    private val dataStore = context.applicationContext.settingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        preferences.toAppSettings()
    }

    override suspend fun setDefaultBackground(background: BackgroundKey) {
        dataStore.edit { preferences ->
            preferences[DefaultBackgroundKey] = background.encode()
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[ThemeModeKey] = mode.storageValue()
        }
    }
}

// -- Functions

private fun Preferences.toAppSettings(): AppSettings {
    return AppSettings(
        defaultBackground = parseBackgroundKey(this[DefaultBackgroundKey])
            ?: defaultBackgroundKey(),
        themeMode = this[ThemeModeKey].toThemeMode(),
    )
}

private fun ThemeMode.storageValue(): String = when (this) {
    ThemeMode.System -> "system"
    ThemeMode.Light -> "light"
    ThemeMode.Dark -> "dark"
}

private fun String?.toThemeMode(): ThemeMode = when (this) {
    "light" -> ThemeMode.Light
    "dark" -> ThemeMode.Dark
    else -> ThemeMode.System
}
