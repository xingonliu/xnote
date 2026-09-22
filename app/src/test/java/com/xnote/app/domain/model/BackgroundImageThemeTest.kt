package com.xnote.app.domain.model

import com.xnote.app.data.settings.InMemoryAppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class BackgroundImageThemeTest {
    @Test fun lightAndDarkImagesChooseMatchingTheme() {
        assertEquals(false, backgroundImageUsesDarkTheme(intArrayOf(0xFFF5E9CE.toInt()), width = 1))
        assertEquals(true, backgroundImageUsesDarkTheme(intArrayOf(0xFF192538.toInt()), width = 1))
    }

    @Test fun dominantColorWinsOverBrightHighlightsAndDarkDetails() {
        assertEquals(true, backgroundImageUsesDarkTheme(IntArray(100) {
            if (it < 80) 0xFF202830.toInt() else 0xFFFFFFFF.toInt()
        }, width = 10))
        assertEquals(false, backgroundImageUsesDarkTheme(IntArray(100) {
            if (it < 80) 0xFFEAE5E0.toInt() else 0xFF000000.toInt()
        }, width = 10))
    }

    @Test fun perceptualBrightnessDistinguishesYellowFromBlue() {
        assertEquals(false, backgroundImageUsesDarkTheme(intArrayOf(0xFFFFFF00.toInt()), width = 1))
        assertEquals(true, backgroundImageUsesDarkTheme(intArrayOf(0xFF0000FF.toInt()), width = 1))
    }

    @Test fun transparentPixelsDoNotDetermineTheme() {
        assertNull(backgroundImageUsesDarkTheme(intArrayOf(0x00FFFFFF), width = 1))
        assertNull(backgroundImageUsesDarkTheme(intArrayOf(), width = 1))
        assertEquals(true, backgroundImageUsesDarkTheme(IntArray(100) {
            if (it == 0) 0xFF101010.toInt() else 0x00FFFFFF
        }, width = 10))
    }

    @Test fun broadTopAndBottomRegionsOutweighSlightlyLargerCenter() {
        for (edgeColor in listOf(0xFF101010.toInt(), 0xFFF0F0F0.toInt())) {
            val centerColor = if (edgeColor == 0xFF101010.toInt()) 0xFFF0F0F0.toInt() else 0xFF101010.toInt()
            val pixels = IntArray(64 * 64) {
                val row = it / 64
                if (row < 14 || row >= 50) edgeColor else centerColor
            }
            assertEquals(edgeColor == 0xFF101010.toInt(), backgroundImageUsesDarkTheme(pixels, width = 64))
        }
    }

    @Test fun narrowEdgeHighlightsDoNotOverruleThePage() {
        val pixels = IntArray(64 * 64) {
            val row = it / 64
            if (row < 4 || row >= 60) 0xFFFFFFFF.toInt() else 0xFF101010.toInt()
        }
        assertEquals(true, backgroundImageUsesDarkTheme(pixels, width = 64))
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
