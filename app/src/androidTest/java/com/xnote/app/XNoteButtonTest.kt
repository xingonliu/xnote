package com.xnote.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.design.XNoteButton
import com.xnote.app.design.XNoteTheme
import com.xnote.app.design.liquidglass.LiquidButton
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

// -- Tests

class XNoteButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun disabledButtonsRejectTouchAndEnabledButtonsRemainClickableAcrossMotionSettings() {
        var enabled by mutableStateOf(false)
        var reduceMotion by mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            XNoteTheme(reduceMotion = reduceMotion) {
                XNoteButton({ clicks++ }, enabled = enabled, modifier = Modifier.testTag("button")) { Text("操作") }
            }
        }
        for (reduced in listOf(false, true)) {
            compose.runOnIdle { reduceMotion = reduced; enabled = false }
            compose.onNodeWithTag("button").assertIsNotEnabled().performTouchInput { click() }
            compose.runOnIdle { assertEquals(0, clicks); enabled = true }
            compose.onNodeWithTag("button").assertIsEnabled().performTouchInput { click() }
            compose.runOnIdle { assertEquals(1, clicks); clicks = 0 }
        }
    }

    @Test
    fun pressAndCancelDeformThenRestoreTheSurfaceWithoutInvokingTheAction() {
        var clicks = 0
        compose.setContent {
            XNoteTheme(reduceMotion = false) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp)) {
                    XNoteButton({ clicks++ }, Modifier.testTag("button")) { Text("按住并拖动") }
                }
            }
        }
        val resting = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("button").performTouchInput { down(center); moveTo(center + androidx.compose.ui.geometry.Offset(12f, 4f)) }
        compose.mainClock.advanceTimeBy(250)
        val pressed = compose.onRoot().captureToImage().asAndroidBitmap()
        assertFalse("Press must change the rendered surface", resting.sameAs(pressed))
        screenshot("button-pressed")
        compose.onNodeWithTag("button").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(2000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("button").assertHeightIsEqualTo(40.dp)
        compose.runOnIdle { assertEquals(0, clicks) }
        compose.onNodeWithTag("button").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun surfaceVariantsRenderAcrossThemesAndContrastModes() {
        var dark by mutableStateOf(false)
        var contrast by mutableStateOf(false)
        compose.setContent {
            XNoteTheme(darkTheme = dark, highContrast = contrast, reduceMotion = true) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text("内容区按钮", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        XNoteButton({}, Modifier.testTag("regular")) { Text("普通操作") }
                        XNoteButton({}, tint = MaterialTheme.colorScheme.primary) { Text("保存") }
                        XNoteButton({}, enabled = false) { Text("不可用") }
                    }
                    XNoteButton({}, Modifier.fillMaxWidth()) { Text("模型与服务商 · 长按钮") }
                    XNoteButton({}, Modifier.size(40.dp).testTag("circle")) { Text("＋") }
                    Text("悬浮层按钮", style = MaterialTheme.typography.titleLarge)
                    LiquidButton({}, rememberLayerBackdrop()) { Text("玻璃材质") }
                }
            }
        }
        for (isDark in listOf(false, true)) {
            for (highContrast in listOf(false, true)) {
                compose.runOnIdle { dark = isDark; contrast = highContrast }
                compose.onNodeWithTag("regular").assertHeightIsEqualTo(40.dp)
                compose.onNodeWithTag("circle").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
                screenshot("buttons-${if (isDark) "dark" else "light"}-${if (highContrast) "contrast" else "normal"}")
            }
        }
    }

    // -- Functions

    private fun screenshot(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.getExternalFilesDir(null), "button-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
