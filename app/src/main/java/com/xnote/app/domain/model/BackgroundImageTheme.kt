package com.xnote.app.domain.model

import kotlin.math.pow
import kotlin.math.min

// -- Constants

private const val ColorBucketCount = 512

// -- Functions

/** Groups similar colors so small highlights do not outweigh the dominant background. */
fun backgroundImageUsesDarkTheme(pixels: IntArray, width: Int): Boolean? {
    require(width > 0 && pixels.size % width == 0)
    val height = pixels.size / width
    val weights = DoubleArray(ColorBucketCount)
    val luminances = DoubleArray(ColorBucketCount)
    for ((index, pixel) in pixels.withIndex()) {
        val alpha = pixel ushr 24
        if (alpha == 0) continue
        val red = pixel ushr 16 and 255
        val green = pixel ushr 8 and 255
        val blue = pixel and 255
        val bucket = ((red shr 5) shl 6) or ((green shr 5) shl 3) or (blue shr 5)
        val weight = alpha * backgroundRowWeight(index / width, height)
        weights[bucket] += weight
        luminances[bucket] += weight * (0.2126 * linearChannel(red) +
            0.7152 * linearChannel(green) + 0.0722 * linearChannel(blue))
    }
    val dominant = weights.indices.maxBy { weights[it] }
    if (weights[dominant] == 0.0) return null
    // The crossover gives black and white text equal contrast against the source color.
    return luminances[dominant] / weights[dominant] < 0.179
}

private fun backgroundRowWeight(row: Int, height: Int): Double {
    val position = (row + 0.5) / height
    val edgeDistance = min(position, 1.0 - position)
    // Around each quarter-height boundary, blend over 1/8 of the viewport height.
    val transition = ((edgeDistance - 0.1875) / 0.125).coerceIn(0.0, 1.0)
    val smooth = transition * transition * (3.0 - 2.0 * transition)
    return 1.5 - 0.5 * smooth
}

private fun linearChannel(channel: Int): Double {
    val value = channel / 255.0
    return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}
