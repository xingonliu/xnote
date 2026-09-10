package com.xnote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.xnote.app.domain.model.ThemeMode
import com.xnote.app.domain.model.defaultAppSettings
import com.xnote.app.design.XNoteTheme

// -- Activities

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as XNoteApplication).container
            val settings by container.settings.settings.collectAsState(defaultAppSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            XNoteTheme(
                darkTheme = darkTheme,
                reduceMotion = settings.reduceMotion,
                highContrast = settings.highContrast,
                fontScale = settings.fontSize.scale,
                readingLayout = settings.readingLayout,
            ) {
                XNoteApp(
                    noteLibrary = container.noteLibrary,
                    searchHistory = container.searchHistory,
                    settings = container.settings,
                )
            }
        }
    }
}
