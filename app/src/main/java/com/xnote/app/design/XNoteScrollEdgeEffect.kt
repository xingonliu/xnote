package com.xnote.app.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

// -- Type Definitions

enum class XNoteScrollEdge {
    Top,
    Bottom,
}

enum class XNoteScrollEdgeStyle {
    Soft,
    Hard,
}

@Immutable
data class XNoteScrollEdgeState(
    val canScrollBackward: Boolean = false,
    val canScrollForward: Boolean = false,
)

private data class XNoteScrollEdgeVisuals(
    val height: Dp,
    val blurRadius: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val surfaceAlpha: Float,
)

// -- Constants

private val SoftVisuals = XNoteScrollEdgeVisuals(
    height = 28.dp,
    blurRadius = 12.dp,
    refractionHeight = 4.dp,
    refractionAmount = 7.dp,
    surfaceAlpha = 0.08f,
)

private val HardVisuals = XNoteScrollEdgeVisuals(
    height = 40.dp,
    blurRadius = 18.dp,
    refractionHeight = 7.dp,
    refractionAmount = 12.dp,
    surfaceAlpha = 0.14f,
)

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
fun BoxScope.XNoteScrollEdgeEffect(
    backdrop: Backdrop,
    state: XNoteScrollEdgeState,
    edges: Set<XNoteScrollEdge>,
    topStyle: XNoteScrollEdgeStyle = XNoteScrollEdgeStyle.Soft,
    bottomStyle: XNoteScrollEdgeStyle = XNoteScrollEdgeStyle.Soft,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
) {
    if (XNoteScrollEdge.Top in edges) {
        XNoteScrollEdgeLayer(
            edge = XNoteScrollEdge.Top,
            style = topStyle,
            visible = state.canScrollBackward,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topInset),
        )
    }

    if (XNoteScrollEdge.Bottom in edges) {
        XNoteScrollEdgeLayer(
            edge = XNoteScrollEdge.Bottom,
            style = bottomStyle,
            visible = state.canScrollForward,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset),
        )
    }
}

@Composable
private fun XNoteScrollEdgeLayer(
    edge: XNoteScrollEdge,
    style: XNoteScrollEdgeStyle,
    visible: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val settings = LocalXNoteInteractionSettings.current
    val visuals = if (style == XNoteScrollEdgeStyle.Hard) HardVisuals else SoftVisuals
    val targetAlpha = if (visible) 1f else 0f
    val alpha = if (settings.reduceMotion) {
        targetAlpha
    } else {
        val animatedAlpha by animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(XNoteShortAnimationDurationMillis),
            label = "XNoteScrollEdgeAlpha",
        )
        animatedAlpha
    }
    val surfaceAlpha = if (settings.highContrast) {
        visuals.surfaceAlpha * 1.6f
    } else {
        visuals.surfaceAlpha
    }
    val mask = if (edge == XNoteScrollEdge.Top) {
        Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
    } else {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(visuals.height)
            .clearAndSetSemantics { }
            .graphicsLayer {
                this.alpha = alpha
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { XNoteSmoothCornerShape(0.dp) },
                effects = {
                    vibrancy()
                    blur(visuals.blurRadius.toPx())
                    lens(
                        refractionHeight = visuals.refractionHeight.toPx(),
                        refractionAmount = visuals.refractionAmount.toPx(),
                        chromaticAberration = true,
                    )
                },
                highlight = null,
                shadow = null,
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = surfaceAlpha))
                },
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = mask,
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}
