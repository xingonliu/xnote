package com.xnote.app.design.liquidglass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.util.fastFirstOrNull

// Adapted from AndroidLiquidGlass catalog commit 65ab177 under Apache-2.0.

// -- Type Definitions

private data class DragEnd(
    val change: PointerInputChange,
    val wasDragged: Boolean,
)

// -- Functions

suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange, wasDragged: Boolean) -> Unit = { _, _ -> },
    onDragCancel: () -> Unit = {},
    consumeChanges: Boolean = true,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        onDragStart(down)
        val upEvent = drag(
            pointerId = down.id,
            touchSlop = viewConfiguration.touchSlop,
            consumeChanges = consumeChanges,
            onDrag = onDrag,
        )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent.change, upEvent.wasDragged)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    touchSlop: Float,
    consumeChanges: Boolean,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
): DragEnd? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }

    var pointer = pointerId
    var accumulatedDrag = Offset.Zero
    var wasDragged = false
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.changedToUpIgnoreConsumed()) {
            return DragEnd(change, wasDragged)
        }
        val dragAmount = change.position - change.previousPosition
        accumulatedDrag += dragAmount
        wasDragged = wasDragged || accumulatedDrag.getDistance() >= touchSlop
        onDrag(change, dragAmount)
        if (wasDragged && consumeChanges) {
            change.consume()
        }
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId,
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            }
            pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}
