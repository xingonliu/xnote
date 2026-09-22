package com.xnote.app

import android.content.Context
import android.graphics.Bitmap
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.xnote.app.feature.background.sampleVisibleBackground
import com.xnote.app.feature.background.XNoteNoteSurface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.model.*
import com.xnote.app.feature.background.EditorBackgroundTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

// -- Tests

class EditorBackgroundThemeTest {
    private val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = XNoteDatabase.createInMemory(context)
    private val root = File(context.cacheDir, "editor-theme-${System.nanoTime()}")
    private val library = NoteLibrary(database, AttachmentFileStore(root), SystemEpochClock)
    private val cleanup = object : TestWatcher() {
        override fun finished(description: Description) {
            database.close()
            root.deleteRecursively()
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(cleanup).around(compose)

    @Test fun centeredCropExcludesInvisibleColorsInBothOrientations() {
        for (landscape in listOf(true, false)) {
            val width = if (landscape) 320 else 80
            val height = if (landscape) 80 else 320
            val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height) {
                val position = if (landscape) it % width else it / width
                if (position in 120 until 200) 0xFFF0F0F0.toInt() else 0xFF101010.toInt()
            }
            source.setPixels(pixels, 0, width, 0, 0, width, height)
            try {
                assertEquals(false, backgroundImageUsesDarkTheme(sampleVisibleBackground(source, IntSize(80, 80)), 64))
                assertEquals(true, backgroundImageUsesDarkTheme(sampleVisibleBackground(source, IntSize(width, height)), 64))
                assertEquals(false, source.isRecycled)
            } finally { source.recycle() }
        }
        val tiny = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        tiny.eraseColor(0xFFFFFFFF.toInt())
        try {
            assertEquals(false, backgroundImageUsesDarkTheme(sampleVisibleBackground(tiny, IntSize(100, 1)), 64))
        } finally { tiny.recycle() }
    }

    @Test fun resizingEditorReevaluatesVisibleCropWithoutChangingOuterTheme() {
        val background = runBlocking {
            val bitmap = Bitmap.createBitmap(320, 80, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(320 * 80) { if (it % 320 in 120 until 200) 0xFFF0F0F0.toInt() else 0xFF101010.toInt() }
            bitmap.setPixels(pixels, 0, 320, 0, 0, 320, 80)
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bitmap.recycle()
            val attachment = library.putAttachment(AttachmentKind.Image, "image/png", "png", ByteArrayInputStream(bytes))
            BackgroundKey.Image(attachment.id, maskOpacity = 0)
        }
        var wide by mutableStateOf(true)
        compose.setContent {
            XNoteTheme(darkTheme = true, reduceMotion = true) {
                Column {
                    Text("outer-dark-${MaterialTheme.colorScheme.background.luminance() < 0.5f}")
                    EditorBackgroundTheme(background, library, defaultAppSettings().copy(reduceMotion = true), active = true) { image, onSizeChanged ->
                        XNoteNoteSurface(background,
                            Modifier.size(if (wide) 320.dp else 80.dp, 80.dp).onSizeChanged(onSizeChanged), image)
                        Text("crop-dark-${MaterialTheme.colorScheme.background.luminance() < 0.5f}-${image != null}")
                    }
                }
            }
        }
        fun awaitTheme(dark: Boolean) {
            compose.waitUntil(5_000) { compose.onAllNodesWithText("crop-dark-$dark-true").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("outer-dark-true").assertExists()
        }
        awaitTheme(true)
        compose.runOnIdle { wide = false }
        awaitTheme(false)
        compose.runOnIdle { wide = true }
        awaitTheme(true)
    }

    @Test fun imageReplacementToggleFallbackAndLocalThemeStayConsistent() {
        fun background(color: Int): BackgroundKey.Image = runBlocking {
            val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bitmap.recycle()
            val attachment = library.putAttachment(AttachmentKind.Image, "image/png", "png", ByteArrayInputStream(bytes))
            BackgroundKey.Image(attachment.id, maskOpacity = 0)
        }
        val light = background(0xFFF5E9CE.toInt())
        val dark = background(0xFF102030.toInt())
        var selected by mutableStateOf<BackgroundKey>(light)
        var enabled by mutableStateOf(true)
        var active by mutableStateOf(true)
        var outerDark by mutableStateOf(true)
        var outerScale = 0f
        var innerScale = 0f
        var window: Window? = null
        compose.setContent {
            XNoteTheme(darkTheme = outerDark, reduceMotion = true, fontScale = 1.3f) {
                outerScale = LocalDensity.current.fontScale
                window = LocalActivity.current?.window
                Column {
                    Text("outer-${MaterialTheme.colorScheme.background.luminance() < 0.5f}")
                    EditorBackgroundTheme(selected, library,
                        defaultAppSettings().copy(editorAutoThemeEnabled = enabled, reduceMotion = true), active, updateSystemBars = true) { image, onBackgroundSizeChanged ->
                        Box(Modifier.size(80.dp).onSizeChanged(onBackgroundSizeChanged))
                        innerScale = LocalDensity.current.fontScale
                        Text("editor-${MaterialTheme.colorScheme.background.luminance() < 0.5f}-${image != null}")
                    }
                }
            }
        }
        fun awaitTheme(darkTheme: Boolean, loaded: Boolean = true) {
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("editor-$darkTheme-$loaded").fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle {
                val currentWindow = checkNotNull(window)
                val controller = WindowCompat.getInsetsController(currentWindow, currentWindow.decorView)
                assertEquals(!darkTheme, controller.isAppearanceLightStatusBars)
                assertEquals(!darkTheme, controller.isAppearanceLightNavigationBars)
            }
        }
        awaitTheme(false)
        compose.onNodeWithText("outer-true").assertExists()
        compose.runOnIdle { assertEquals(outerScale, innerScale, 0.001f); enabled = false }
        awaitTheme(true)
        compose.runOnIdle { enabled = true }
        awaitTheme(false)
        compose.runOnIdle { selected = dark }
        awaitTheme(true)
        compose.runOnIdle { selected = light.copy(maskOpacity = 100) }
        awaitTheme(false)
        compose.runOnIdle { active = false }
        awaitTheme(true, false)
        compose.runOnIdle { active = true; selected = defaultBackgroundKey(); outerDark = false }
        awaitTheme(false, false)
        compose.runOnIdle { selected = BackgroundKey.Image("missing-image"); outerDark = true }
        awaitTheme(true, false)
    }
}
