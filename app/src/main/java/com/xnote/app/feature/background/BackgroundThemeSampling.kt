package com.xnote.app.feature.background

import android.graphics.Bitmap
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.scale
import kotlin.math.roundToInt

// -- Constants

internal const val BackgroundThemeSampleSize = 64

// -- Functions

internal fun sampleVisibleBackground(source: Bitmap, viewport: IntSize): IntArray {
    require(viewport.width > 0 && viewport.height > 0)
    // Match Image's centered ContentScale.Crop before reducing the visible rectangle to a sample.
    val scale = maxOf(viewport.width.toDouble() / source.width, viewport.height.toDouble() / source.height)
    val width = (viewport.width / scale).roundToInt().coerceIn(1, source.width)
    val height = (viewport.height / scale).roundToInt().coerceIn(1, source.height)
    val cropped = Bitmap.createBitmap(source, (source.width - width) / 2, (source.height - height) / 2, width, height)
    try {
        val scaled = cropped.scale(BackgroundThemeSampleSize, BackgroundThemeSampleSize)
        try {
            // ImageDecoder may return GPU storage; only the small sample needs a software copy.
            val sample = if (scaled.config == Bitmap.Config.HARDWARE) scaled.copy(Bitmap.Config.ARGB_8888, false) else scaled
            try {
                return IntArray(BackgroundThemeSampleSize * BackgroundThemeSampleSize).also {
                    sample.getPixels(it, 0, BackgroundThemeSampleSize, 0, 0, BackgroundThemeSampleSize, BackgroundThemeSampleSize)
                }
            } finally {
                if (sample !== scaled) sample.recycle()
            }
        } finally {
            if (scaled !== cropped) scaled.recycle()
        }
    } finally {
        if (cropped !== source) cropped.recycle()
    }
}
