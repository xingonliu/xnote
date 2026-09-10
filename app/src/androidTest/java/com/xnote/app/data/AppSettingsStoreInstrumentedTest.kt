package com.xnote.app.data

import android.content.Context
import android.os.SystemClock
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xnote.app.MainActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xnote.app.data.settings.AppSettingsStore
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.GridBuiltinBackgroundId
import com.xnote.app.domain.model.defaultBackgroundKey
import com.xnote.app.domain.model.ThemeMode
import com.xnote.app.domain.model.AppFontSize
import com.xnote.app.domain.model.ReadingLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// -- Tests

@RunWith(AndroidJUnit4::class)
class AppSettingsStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun chosenThemeControlsSystemBarIconsIndependentlyOfSystemTheme() = runTest {
        val store = AppSettingsStore(context)
        val original = store.settings.first().themeMode
        try {
            store.setThemeMode(ThemeMode.Dark)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun awaitBarAppearance(light: Boolean) {
                    val deadline = SystemClock.uptimeMillis() + 5_000
                    var matches = false
                    while (!matches && SystemClock.uptimeMillis() < deadline) {
                        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                        scenario.onActivity { activity ->
                            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                            matches = controller.isAppearanceLightStatusBars == light && controller.isAppearanceLightNavigationBars == light
                        }
                        if (!matches) SystemClock.sleep(50)
                    }
                    assertEquals(true, matches)
                }
                awaitBarAppearance(false)
                store.setThemeMode(ThemeMode.Light)
                awaitBarAppearance(true)
            }
        } finally { store.setThemeMode(original) }
    }

    @Test fun appearancePersistsTogetherWithoutOverwritingBackgroundOrShortcuts() = runTest {
        val store = AppSettingsStore(context)
        val original = store.settings.first()
        try {
            store.setThemeMode(ThemeMode.Dark)
            store.setReduceMotion(true)
            store.setHighContrast(true)
            store.setFontSize(AppFontSize.ExtraLarge)
            store.setReadingLayout(ReadingLayout.Relaxed)
            assertEquals(original.copy(themeMode = ThemeMode.Dark, reduceMotion = true, highContrast = true,
                fontSize = AppFontSize.ExtraLarge, readingLayout = ReadingLayout.Relaxed), AppSettingsStore(context).settings.first())
        } finally {
            store.setThemeMode(original.themeMode)
            store.setReduceMotion(original.reduceMotion)
            store.setHighContrast(original.highContrast)
            store.setFontSize(original.fontSize)
            store.setReadingLayout(original.readingLayout)
        }
    }

    @Test
    fun defaultBackgroundPersistsAcrossStoreInstances() = runTest {
        val store = AppSettingsStore(context)
        val selected = BackgroundKey(GridBuiltinBackgroundId)
        try {
            store.setDefaultBackground(selected)

            val reopened = AppSettingsStore(context)
            assertEquals(selected, reopened.settings.first().defaultBackground)
        } finally {
            store.setDefaultBackground(defaultBackgroundKey())
        }
    }

    @Test
    fun markdownShortcutsDefaultOnAndPersistAcrossStoreInstances() = runTest {
        val store = AppSettingsStore(context)
        try {
            assertEquals(true, store.settings.first().markdownShortcutsEnabled)
            store.setMarkdownShortcutsEnabled(false)

            val reopened = AppSettingsStore(context)
            assertEquals(false, reopened.settings.first().markdownShortcutsEnabled)
        } finally {
            store.setMarkdownShortcutsEnabled(true)
        }
    }
}
