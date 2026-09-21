package com.xnote.app.domain.model

import com.xnote.app.data.settings.InMemoryAppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class BackgroundImageThemeTest {
    @Test fun lightAndDarkImagesChooseMatchingTheme() {
        assertEquals(false, backgroundImageUsesDarkTheme(intArrayOf(0xFFF5E9CE.toInt())))
        assertEquals(true, backgroundImageUsesDarkTheme(intArrayOf(0xFF192538.toInt())))
    }

    @Test fun dominantColorWinsOverBrightHighlightsAndDarkDetails() {
        assertEquals(true, backgroundImageUsesDarkTheme(IntArray(100) {
            if (it < 80) 0xFF202830.toInt() else 0xFFFFFFFF.toInt()
        }))
        assertEquals(false, backgroundImageUsesDarkTheme(IntArray(100) {
            if (it < 80) 0xFFEAE5E0.toInt() else 0xFF000000.toInt()
        }))
    }

    @Test fun perceptualBrightnessDistinguishesYellowFromBlue() {
        assertEquals(false, backgroundImageUsesDarkTheme(intArrayOf(0xFFFFFF00.toInt())))
        assertEquals(true, backgroundImageUsesDarkTheme(intArrayOf(0xFF0000FF.toInt())))
    }

    @Test fun transparentPixelsDoNotDetermineTheme() {
        assertNull(backgroundImageUsesDarkTheme(intArrayOf(0x00FFFFFF)))
        assertNull(backgroundImageUsesDarkTheme(intArrayOf()))
        assertEquals(true, backgroundImageUsesDarkTheme(IntArray(100) {
            if (it == 0) 0xFF101010.toInt() else 0x00FFFFFF
        }))
    }

    @Test fun switchDefaultsOnAndDoesNotChangeApplicationTheme() = runTest {
        val repository = InMemoryAppSettingsRepository()
        val original = repository.settings.first()
        assertTrue(original.editorAutoThemeEnabled)
        repository.setEditorAutoThemeEnabled(false)
        assertEquals(original.copy(editorAutoThemeEnabled = false), repository.settings.first())
        repository.setEditorAutoThemeEnabled(true)
        assertEquals(original, repository.settings.first())
    }
}
