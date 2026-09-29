package com.xnote.app.domain.document

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// -- Type Definitions

data class MediaGeometry(val width: Float, val height: Float, val centerX: Float, val centerY: Float, val boundsWidth: Float, val boundsHeight: Float) {
    val right: Float get() = centerX + boundsWidth / 2f
    val bottom: Float get() = centerY + boundsHeight / 2f
}

data class NoteFlowGroup(val blocks: List<NoteBlock>) {
    val wraps: Boolean get() = blocks.firstOrNull().let { it is PlacedMediaBlock && it.layout != MediaLayout.Block }
}

// -- Functions

fun PlacedMediaBlock.geometry(contentWidth: Float, ratio: Float): MediaGeometry {
    val width = contentWidth * (if (layout == MediaLayout.Wrap) 0.42f else 0.7f) * scale
    val height = width * ratio
    val radians = Math.toRadians(rotationDegrees.toDouble())
    val boundsWidth = abs(cos(radians)).toFloat() * width + abs(sin(radians)).toFloat() * height
    val boundsHeight = abs(sin(radians)).toFloat() * width + abs(cos(radians)).toFloat() * height
    val centerX = (if (layout == MediaLayout.Wrap) boundsWidth / 2f else contentWidth / 2f) + offsetX
    return MediaGeometry(width, height, centerX, boundsHeight / 2f + offsetY, boundsWidth, boundsHeight)
}

fun List<NoteBlock>.flowGroups(): List<NoteFlowGroup> {
    val groups = mutableListOf<NoteFlowGroup>()
    var pending = mutableListOf<NoteBlock>()
    fun flush() {
        if (pending.isNotEmpty()) groups += NoteFlowGroup(pending.toList())
        pending = mutableListOf()
    }
    for (block in this) {
        if (block is PlacedMediaBlock && block.layout != MediaLayout.Block) pending += block
        else if (block is TextBlock && pending.isNotEmpty()) pending += block
        else { flush(); groups += NoteFlowGroup(listOf(block)) }
    }
    flush()
    return groups
}
