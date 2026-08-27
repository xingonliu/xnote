package com.xnote.app.feature.background

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.background.ResolvedNoteBackground
import com.xnote.app.domain.model.CreamBuiltinBackgroundId
import com.xnote.app.domain.model.DefaultBuiltinBackgroundId
import com.xnote.app.domain.model.GridBuiltinBackgroundId
import com.xnote.app.domain.model.RuledBuiltinBackgroundId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

private sealed interface BackgroundBitmapState {
    data object Loading : BackgroundBitmapState
    data class Loaded(val bitmap: ImageBitmap) : BackgroundBitmapState
    data object Failed : BackgroundBitmapState
}

// -- Constants

val XNoteBuiltinBackgroundPresets = listOf(
    XNoteBuiltinBackgroundPreset(DefaultBuiltinBackgroundId, R.string.background_builtin_default),
    XNoteBuiltinBackgroundPreset(CreamBuiltinBackgroundId, R.string.background_builtin_cream),
    XNoteBuiltinBackgroundPreset(RuledBuiltinBackgroundId, R.string.background_builtin_ruled),
    XNoteBuiltinBackgroundPreset(GridBuiltinBackgroundId, R.string.background_builtin_grid),
)

private const val MaximumDecodedBackgroundEdgePx = 2_048

// -- Composables

@Composable
fun XNoteNoteSurface(
    background: ResolvedNoteBackground,
    modifier: Modifier = Modifier,
    onBackgroundLoadFailed: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier) {
        when (background) {
            is ResolvedNoteBackground.Builtin -> {
                BuiltinBackground(background.key.id, Modifier.fillMaxSize())
            }
            is ResolvedNoteBackground.UserImage -> {
                BuiltinBackground(DefaultBuiltinBackgroundId, Modifier.fillMaxSize())
                UserImageBackground(
                    background = background,
                    onLoadFailed = onBackgroundLoadFailed,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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
                    drawLine(detailColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height))
                    x += spacing
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(detailColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y))
                    y += spacing
                }
            }
            BackgroundPattern.Ruled -> {
                val spacing = 30.dp.toPx()
                var y = spacing
                while (y <= size.height) {
                    drawLine(detailColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y))
                    y += spacing
                }
            }
            BackgroundPattern.Grid -> {
                val spacing = 24.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(detailColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height))
                    x += spacing
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(detailColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y))
                    y += spacing
                }
            }
        }
    }
}

@Composable
private fun UserImageBackground(
    background: ResolvedNoteBackground.UserImage,
    onLoadFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by produceState<BackgroundBitmapState>(
        initialValue = BackgroundBitmapState.Loading,
        key1 = background.file.absolutePath,
    ) {
        value = withContext(Dispatchers.IO) {
            decodeBackground(background.file.absolutePath)
                ?.let(BackgroundBitmapState::Loaded)
                ?: BackgroundBitmapState.Failed
        }
    }
    if (state is BackgroundBitmapState.Failed) {
        LaunchedEffect(background.file.absolutePath) { onLoadFailed() }
    }
    val loaded = state as? BackgroundBitmapState.Loaded
    if (loaded != null) {
        Image(
            bitmap = loaded.bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        Box(
            modifier = modifier.background(
                MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
            ),
        )
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
    CreamBuiltinBackgroundId -> if (isDark) Color(0xFF211E18) else Color(0xFFFFF4D9)
    RuledBuiltinBackgroundId -> if (isDark) Color(0xFF181D22) else Color(0xFFF8FBFF)
    GridBuiltinBackgroundId -> if (isDark) Color(0xFF19201F) else Color(0xFFF6FCFA)
    else -> if (isDark) Color(0xFF1C1A17) else Color(0xFFFFFCF5)
}

private fun builtinDetailColor(id: String, isDark: Boolean): Color = when (id) {
    CreamBuiltinBackgroundId -> if (isDark) Color(0x12FFE2A8) else Color(0x147A5A28)
    RuledBuiltinBackgroundId -> if (isDark) Color(0x244A90C2) else Color(0x245B8FB5)
    GridBuiltinBackgroundId -> if (isDark) Color(0x2055B6A1) else Color(0x205A9C8F)
    else -> Color.Transparent
}

private fun decodeBackground(path: String): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > MaximumDecodedBackgroundEdgePx ||
        bounds.outHeight / sampleSize > MaximumDecodedBackgroundEdgePx
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return runCatching { BitmapFactory.decodeFile(path, options)?.asImageBitmap() }.getOrNull()
}
