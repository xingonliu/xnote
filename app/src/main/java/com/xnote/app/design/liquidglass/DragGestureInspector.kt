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

// -- Functions

suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange, wasDragged: Boolean) -> Unit = { _, _ -> },
    onDragCancel: () -> Unit = {},
    consumeChanges: Boolean = true,
    followPointerImmediately: Boolean = true,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        onDragStart(down)
        var pointer = down.id
        var accumulatedDrag = Offset.Zero
        var wasDragged = false
        val touchSlop = viewConfiguration.touchSlop

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: break
            if (dragEvent.changedToUpIgnoreConsumed()) {
                onDragEnd(dragEvent, wasDragged)
                break
            }
            val dragAmount = dragEvent.position - dragEvent.previousPosition
            accumulatedDrag += dragAmount
            val crossedSlop = !wasDragged && accumulatedDrag.getDistance() >= touchSlop
            wasDragged = wasDragged || crossedSlop
            if (followPointerImmediately || wasDragged) {
                onDrag(
                    dragEvent,
                    if (followPointerImmediately || !crossedSlop) dragAmount else accumulatedDrag,
                )
            }
            if (wasDragged && consumeChanges) {
                dragEvent.consume()
            }
            pointer = dragEvent.id
        }
    }
}

