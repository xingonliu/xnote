package com.xnote.app.domain.document

import kotlin.math.atan2
import kotlin.math.hypot

// -- Functions

fun PlacedMediaBlock.transformed(scale: Float, rotation: Float, x: Float, y: Float): PlacedMediaBlock = withPlacement(
    scale = scale.coerceIn(0.15f, 4f),
    rotationDegrees = ((rotation % 360f) + 360f) % 360f,
    offsetX = x,
    offsetY = y,
)

fun PlacedMediaBlock.transformFromHandle(startX: Float, startY: Float, endX: Float, endY: Float): PlacedMediaBlock {
    val startDistance = hypot(startX, startY)
    if (startDistance < 1f) return this
    val angle = Math.toDegrees((atan2(endY, endX) - atan2(startY, startX)).toDouble()).toFloat()
    return transformed(scale * hypot(endX, endY) / startDistance, rotationDegrees + angle, offsetX, offsetY)
}
