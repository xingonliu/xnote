package com.xnote.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.design.XNotePageScaffold
import com.xnote.app.design.XNoteScrollEdge
import com.xnote.app.design.XNoteTheme
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// -- Tests

class XNoteProgressiveBlurTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun edgesBlurDetailProgressivelyInBothThemesAndRestoreWhenHidden() {
        var dark by mutableStateOf(false)
        var visible by mutableStateOf(true)
        composeRule.setContent {
            XNoteTheme(darkTheme = dark, reduceMotion = true) {
                val edges = if (visible) setOf(XNoteScrollEdge.Top, XNoteScrollEdge.Bottom) else emptySet()
                Box(Modifier.size(240.dp, 384.dp).testTag("blur-fixture")) {
                    XNotePageScaffold(
                        backdrop = rememberLayerBackdrop(),
                        scrollEdges = edges,
                        alwaysVisibleScrollEdges = edges,
                        content = {
                            Canvas(Modifier.fillMaxSize()) {
                                val stripe = 4.dp.toPx()
                                for (index in 0..(size.width / stripe).toInt()) {
                                    drawRect(
                                        color = if (index % 2 == 0) Color.Black else Color.White,
                                        topLeft = Offset(index * stripe, 0f),
                                        size = Size(stripe, size.height),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        for (isDark in listOf(false, true)) {
            composeRule.runOnIdle { dark = isDark }
            val bitmap = composeRule.onNodeWithTag("blur-fixture").captureToImage().asAndroidBitmap()
            val scale = bitmap.height / 384f
            val clear = rowContrast(bitmap, bitmap.height / 2)
            val top = rowContrast(bitmap, (12 * scale).toInt())
            val bottom = rowContrast(bitmap, bitmap.height - 1 - (12 * scale).toInt())
            val transition = rowContrast(bitmap, (120 * scale).toInt())
            assertTrue("Top edge must remove fine detail: $top / $clear", top < clear * 0.45f)
            assertTrue("Bottom edge must remove fine detail: $bottom / $clear", bottom < clear * 0.45f)
            assertTrue("Content end must stay clear: $transition / $clear", transition > clear * 0.8f)
            assertTrue("Both edges must match", abs(top - bottom) < clear * 0.1f)
            val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
                "progressive-blur-${if (isDark) "dark" else "light"}.png")
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        composeRule.runOnIdle { visible = false }
        val restored = composeRule.onNodeWithTag("blur-fixture").captureToImage().asAndroidBitmap()
        assertTrue("Hidden edges must restore source detail",
            rowContrast(restored, restored.height / 32) > rowContrast(restored, restored.height / 2) * 0.95f)
    }

    // -- Functions

    private fun rowContrast(bitmap: Bitmap, y: Int): Float {
        val values = (bitmap.width / 4 until bitmap.width * 3 / 4).map {
            android.graphics.Color.red(bitmap.getPixel(it, y)).toFloat()
        }
        val mean = values.average().toFloat()
        return values.sumOf { abs(it - mean).toDouble() }.toFloat() / values.size
    }
}
