package com.xnote.app.design.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Clock

// Adapted from AndroidLiquidGlass catalog commit 65ab177 under Apache-2.0.

// -- Type Definitions

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedRange<Float>,
    visibilityThreshold: Float = 0.001f,
    private val initialScale: Float = 1f,
    private val pressedScale: Float = 78f / 56f,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit = {},
    val onDragStopped: DampedDragAnimation.(wasDragged: Boolean) -> Unit = {},
    val onDragCancelled: DampedDragAnimation.() -> Unit = {},
    val onDrag: DampedDragAnimation.(
        size: IntSize,
        position: Offset,
        dragAmount: Offset,
    ) -> Unit = { _, _, _ -> },
) {
    // -- Constants

    private val valueAnimationSpec = spring(0.85f, 450f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    // -- State

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)
    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    // -- Derived Values

    val value: Float
        get() = valueAnimation.value

    val targetValue: Float
        get() = valueAnimation.targetValue

    val pressProgress: Float
        get() = pressProgressAnimation.value

    val scaleX: Float
        get() = scaleXAnimation.value

    val scaleY: Float
        get() = scaleYAnimation.value

    val velocity: Float
        get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = { _, wasDragged ->
                onDragStopped(wasDragged)
                release()
            },
            onDragCancel = {
                onDragCancelled()
                release()
            },
            followPointerImmediately = true,
        ) { change, dragAmount ->
            onDrag(size, change.position, dragAmount)
        }
    }

    // -- Functions

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.snapTo(targetValue)
            updateVelocity()
        }
    }

    fun animatePositionToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                valueAnimation.animateTo(value.coerceIn(valueRange), valueAnimationSpec)
            }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            Clock.System.now().toEpochMilliseconds(),
            Offset(value, 0f),
        )
        val targetVelocity = velocityTracker.calculateVelocity().x /
            (valueRange.endInclusive - valueRange.start)
        animationScope.launch {
            velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec)
        }
    }
}


