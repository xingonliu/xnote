package com.xnote.app.feature.background

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.CreamBuiltinBackgroundId
import com.xnote.app.domain.model.DefaultBuiltinBackgroundId
import com.xnote.app.domain.model.GridBuiltinBackgroundId
import com.xnote.app.domain.model.RuledBuiltinBackgroundId

// -- Type Definitions

data class XNoteBuiltinBackgroundPreset(
    val id: String,
    @param:StringRes val nameRes: Int,
)

private enum class BackgroundPattern {
    Plain,
    Linen,
    Ruled,
    Grid,
}

// -- Constants

val XNoteBuiltinBackgroundPresets = listOf(
    XNoteBuiltinBackgroundPreset(DefaultBuiltinBackgroundId, R.string.background_builtin_default),
    XNoteBuiltinBackgroundPreset(CreamBuiltinBackgroundId, R.string.background_builtin_cream),
    XNoteBuiltinBackgroundPreset(RuledBuiltinBackgroundId, R.string.background_builtin_ruled),
    XNoteBuiltinBackgroundPreset(GridBuiltinBackgroundId, R.string.background_builtin_grid),
)

// -- Composables

@Composable
fun XNoteNoteSurface(
    background: BackgroundKey,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier) {
        BuiltinBackground(background.id, Modifier.fillMaxSize())
        content()
    }
}

@Composable
private fun BuiltinBackground(
    id: String,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val baseColor = builtinBaseColor(id, isDark)
    val detailColor = builtinDetailColor(id, isDark)
    val pattern = builtinPattern(id)
    Canvas(modifier = modifier.background(baseColor)) {
        when (pattern) {
            BackgroundPattern.Plain -> Unit
            BackgroundPattern.Linen -> {
                val spacing = 12.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(
                        detailColor,
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                    )
                    x += spacing
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(
                        detailColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                    )
                    y += spacing
                }
            }
            BackgroundPattern.Ruled -> {
                val spacing = 30.dp.toPx()
                var y = spacing
                while (y <= size.height) {
                    drawLine(
                        detailColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                    )
                    y += spacing
                }
            }
            BackgroundPattern.Grid -> {
                val spacing = 24.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(
                        detailColor,
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                    )
                    x += spacing
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(
                        detailColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                    )
                    y += spacing
                }
            }
        }
    }
}

// -- Functions

private fun builtinPattern(id: String): BackgroundPattern = when (id) {
    CreamBuiltinBackgroundId -> BackgroundPattern.Linen
    RuledBuiltinBackgroundId -> BackgroundPattern.Ruled
    GridBuiltinBackgroundId -> BackgroundPattern.Grid
    else -> BackgroundPattern.Plain
}

private fun builtinBaseColor(id: String, isDark: Boolean): Color = when (id) {
    CreamBuiltinBackgroundId -> if (isDark) Color(0xFF1C1C1E) else Color(0xFFF7F7F9)
    RuledBuiltinBackgroundId -> if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    GridBuiltinBackgroundId -> if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    else -> if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
}

private fun builtinDetailColor(id: String, isDark: Boolean): Color = when (id) {
    CreamBuiltinBackgroundId -> if (isDark) Color(0x12FFFFFF) else Color(0x14000000)
    RuledBuiltinBackgroundId -> if (isDark) Color(0x24AEAEB2) else Color(0x248E8E93)
    GridBuiltinBackgroundId -> if (isDark) Color(0x20AEAEB2) else Color(0x208E8E93)
    else -> Color.Transparent
}
