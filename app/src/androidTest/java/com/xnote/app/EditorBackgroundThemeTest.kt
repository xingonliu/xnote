package com.xnote.app

import android.content.Context
import android.graphics.Bitmap
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Column
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
                        defaultAppSettings().copy(editorAutoThemeEnabled = enabled, reduceMotion = true), active, updateSystemBars = true) { image ->
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
