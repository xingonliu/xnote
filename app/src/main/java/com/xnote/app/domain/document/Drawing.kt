package com.xnote.app.domain.document

import kotlinx.serialization.Serializable

// -- Type Definitions

@Serializable
data class DrawingPoint(val x: Float, val y: Float)

@Serializable
data class DrawingStroke(
    val points: List<DrawingPoint>,
    val color: Long = 0xff202020,
    val width: Float = 6f,
    val erase: Boolean = false,
)

// -- Constants

const val DrawingWidth = 1024
const val DrawingHeight = 768
