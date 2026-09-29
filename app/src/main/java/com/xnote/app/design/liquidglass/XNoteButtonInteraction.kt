package com.xnote.app.design.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.xnote.app.design.LocalXNoteInteractionSettings
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

// Deformation extracted from AndroidLiquidGlass catalog commit 65ab177 under Apache-2.0.

// -- Type Definitions

internal class XNoteButtonInteraction(
    val enabled: Boolean,
    private val highlight: InteractiveHighlight,
) {
    // -- Derived Values

    val modifier: Modifier = if (enabled) {
        Modifier.then(highlight.modifier).then(highlight.gestureModifier)
    } else {
        Modifier
    }

    // Keep the catalog's deformation identical for glass and ordinary surfaces.
    val layerBlock: (GraphicsLayerScope.() -> Unit)? = if (enabled) {
        {
            val width = size.width
            val height = size.height
            val progress = highlight.pressProgress
            val scale = lerp(1f, 1f + 4.dp.toPx() / size.height, progress)
            val maxOffset = size.minDimension
            val initialDerivative = 0.05f
            val offset = highlight.offset
            translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
            translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

            val maxDragScale = 4.dp.toPx() / size.height
            val offsetAngle = atan2(offset.y, offset.x)
            scaleX = scale +
                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                (width / height).fastCoerceAtMost(1f)
            scaleY = scale +
                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                (height / width).fastCoerceAtMost(1f)
        }
    } else {
        null
    }
}

// -- Functions

@Composable
internal fun rememberXNoteButtonInteraction(
    enabled: Boolean,
    softGlow: Boolean = false,
): XNoteButtonInteraction {
    val settings = LocalXNoteInteractionSettings.current
    val scope = rememberCoroutineScope()
    val highlight = remember(scope, softGlow) {
        InteractiveHighlight(animationScope = scope, softGlow = softGlow)
    }
    val hasMotion = enabled && !settings.reduceMotion
    return remember(highlight, hasMotion) { XNoteButtonInteraction(hasMotion, highlight) }
}
