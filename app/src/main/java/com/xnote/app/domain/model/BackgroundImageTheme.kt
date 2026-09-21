package com.xnote.app.domain.model

import kotlin.math.pow

// -- Constants

private const val ColorBucketCount = 512

// -- Functions

/** Groups similar colors so small highlights do not outweigh the dominant background. */
fun backgroundImageUsesDarkTheme(pixels: IntArray): Boolean? {
    val weights = LongArray(ColorBucketCount)
    val luminances = DoubleArray(ColorBucketCount)
    for (pixel in pixels) {
        val alpha = pixel ushr 24
        if (alpha == 0) continue
        val red = pixel ushr 16 and 255
        val green = pixel ushr 8 and 255
        val blue = pixel and 255
        val bucket = ((red shr 5) shl 6) or ((green shr 5) shl 3) or (blue shr 5)
        weights[bucket] += alpha.toLong()
        luminances[bucket] += alpha * (0.2126 * linearChannel(red) +
            0.7152 * linearChannel(green) + 0.0722 * linearChannel(blue))
    }
    val dominant = weights.indices.maxBy { weights[it] }
    if (weights[dominant] == 0L) return null
    // The crossover gives black and white text equal contrast against the source color.
    return luminances[dominant] / weights[dominant] < 0.179
}

private fun linearChannel(channel: Int): Double {
    val value = channel / 255.0
    return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}
