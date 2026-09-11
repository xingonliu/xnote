package com.xnote.app.domain.document

import kotlin.math.atan2
import kotlin.math.hypot

// -- Functions

fun ImageBlock.transformed(scale: Float, rotation: Float, x: Float, y: Float): ImageBlock = copy(
    scale = scale.coerceIn(0.15f, 4f),
    rotationDegrees = ((rotation % 360f) + 360f) % 360f,
    offsetX = x,
    offsetY = y,
)

fun ImageBlock.transformFromHandle(startX: Float, startY: Float, endX: Float, endY: Float): ImageBlock {
    val startDistance = hypot(startX, startY)
    if (startDistance < 1f) return this
    val angle = Math.toDegrees((atan2(endY, endX) - atan2(startY, startX)).toDouble()).toFloat()
    return transformed(scale * hypot(endX, endY) / startDistance, rotationDegrees + angle, offsetX, offsetY)
}
