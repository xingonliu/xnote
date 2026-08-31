package com.xnote.app.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

// -- Type Definitions

enum class XNoteScrollEdge {
    Top,
    Bottom,
}

@Immutable
data class XNoteScrollEdgeState(
    val canScrollBackward: Boolean = false,
    val canScrollForward: Boolean = false,
)

// -- Constants

private val ProgressiveBlurHeight = 64.dp
private val ProgressiveBlurRadius = 18.dp
private const val ProgressiveBlurTintIntensity = 0.22f
private const val ProgressiveBlurShader = """
    uniform shader content;

    uniform float2 size;
    layout(color) uniform half4 tint;
    uniform float tintIntensity;
    uniform float bottomEdge;

    half4 main(float2 coord) {
        float edgeCoordinate = mix(coord.y, size.y - coord.y, bottomEdge);
        float mask = smoothstep(size.y, size.y * 0.42, edgeCoordinate);
        return mix(content.eval(coord) * mask, tint * mask, tintIntensity);
    }
"""

// -- Composables

@Composable
fun rememberXNoteScrollEdgeState(
    scrollableState: ScrollableState,
): XNoteScrollEdgeState {
    val canScrollBackward by remember(scrollableState) {
        derivedStateOf { scrollableState.canScrollBackward }
    }
    val canScrollForward by remember(scrollableState) {
        derivedStateOf { scrollableState.canScrollForward }
    }

    return remember(canScrollBackward, canScrollForward) {
        XNoteScrollEdgeState(
            canScrollBackward = canScrollBackward,
            canScrollForward = canScrollForward,
        )
    }
}

@Composable
fun BoxScope.XNoteProgressiveBlur(
    backdrop: Backdrop,
    state: XNoteScrollEdgeState,
    edges: Set<XNoteScrollEdge>,
    alwaysVisibleEdges: Set<XNoteScrollEdge> = emptySet(),
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
) {
    if (XNoteScrollEdge.Top in edges) {
        XNoteProgressiveBlurLayer(
            edge = XNoteScrollEdge.Top,
            visible = XNoteScrollEdge.Top in alwaysVisibleEdges || state.canScrollBackward,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topInset),
        )
    }

    if (XNoteScrollEdge.Bottom in edges) {
        XNoteProgressiveBlurLayer(
            edge = XNoteScrollEdge.Bottom,
            visible = XNoteScrollEdge.Bottom in alwaysVisibleEdges || state.canScrollForward,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset),
        )
    }
}

@Composable
private fun XNoteProgressiveBlurLayer(
    edge: XNoteScrollEdge,
    visible: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val settings = LocalXNoteInteractionSettings.current
    val tint = MaterialTheme.colorScheme.background
    val targetAlpha = if (visible) 1f else 0f
    val alpha = if (settings.reduceMotion) {
        targetAlpha
    } else {
        val animatedAlpha by animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(XNoteShortAnimationDurationMillis),
            label = "XNoteProgressiveBlurAlpha",
        )
        animatedAlpha
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ProgressiveBlurHeight)
            .clearAndSetSemantics { }
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    blur(ProgressiveBlurRadius.toPx())
                    runtimeShaderEffect(
                        "AlphaMask",
                        ProgressiveBlurShader,
                        "content",
                    ) {
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("tint", tint)
                        setFloatUniform("tintIntensity", ProgressiveBlurTintIntensity)
                        setFloatUniform(
                            "bottomEdge",
                            if (edge == XNoteScrollEdge.Bottom) 1f else 0f,
                        )
                    }
                },
                layerBlock = { this.alpha = alpha },
            ),
    )
}
