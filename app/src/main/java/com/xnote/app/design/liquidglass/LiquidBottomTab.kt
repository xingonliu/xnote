package com.xnote.app.design.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.shapes.Capsule
import com.xnote.app.design.LocalXNoteInteractionSettings

// Adapted from AndroidLiquidGlass catalog commit 65ab177 under Apache-2.0.

// -- Type Definitions

internal data class LiquidBottomTabTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationY: Float = 0f,
)

internal enum class LiquidBottomTabLayer {
    Base,
    Highlight,
}

private data class AxialPulseTransform(
    val scaleX: Float,
    val scaleY: Float,
)

// -- Constants

internal val LocalLiquidBottomTabTransform = staticCompositionLocalOf<(Int) -> LiquidBottomTabTransform> {
    { LiquidBottomTabTransform() }
}
internal val LocalLiquidBottomTabClick = staticCompositionLocalOf<(Int) -> Unit> {
    { }
}
internal val LocalLiquidBottomTabLayer = staticCompositionLocalOf {
    LiquidBottomTabLayer.Base
}
internal val LocalLiquidBottomTabPreactivation = staticCompositionLocalOf<(Int, Boolean) -> Unit> {
    { _, _ -> }
}
internal val LocalLiquidBottomTabReselectionPulse = staticCompositionLocalOf<(Int) -> Int> {
    { 0 }
}

// -- Functions

private fun axialPulseTransform(phase: Float): AxialPulseTransform {
    return when {
        phase < 0.2f -> {
            val progress = phase / 0.2f
            AxialPulseTransform(
                scaleX = lerp(1f, 0.9f, progress),
                scaleY = lerp(1f, 0.78f, progress),
            )
        }

        phase < 0.46f -> {
            val progress = (phase - 0.2f) / 0.26f
            AxialPulseTransform(
                scaleX = lerp(0.9f, 1.06f, progress),
                scaleY = lerp(0.78f, 1.1f, progress),
            )
        }

        phase < 0.7f -> {
            val progress = (phase - 0.46f) / 0.24f
            AxialPulseTransform(
                scaleX = lerp(1.06f, 0.98f, progress),
                scaleY = lerp(1.1f, 0.96f, progress),
            )
        }

        else -> {
            val progress = (phase - 0.7f) / 0.3f
            AxialPulseTransform(
                scaleX = lerp(0.98f, 1f, progress),
                scaleY = lerp(0.96f, 1f, progress),
            )
        }
    }
}

// -- Composables

@Composable
fun RowScope.LiquidBottomTab(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSettings = LocalXNoteInteractionSettings.current
    val hasInteractiveMotion = !interactionSettings.reduceMotion
    val layer = LocalLiquidBottomTabLayer.current
    val isInteractiveLayer = layer == LiquidBottomTabLayer.Base
    val onClick = LocalLiquidBottomTabClick.current
    val transform = LocalLiquidBottomTabTransform.current
    val onPreactivation = LocalLiquidBottomTabPreactivation.current
    val reselectionPulse = LocalLiquidBottomTabReselectionPulse.current
    val accentColor = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var isFocused by remember { mutableStateOf(false) }
    val isPreactivated = isInteractiveLayer && (isHovered || isFocused)
    val preactivationProgress by animateFloatAsState(
        targetValue = if (isPreactivated) 1f else 0f,
        animationSpec = if (hasInteractiveMotion) tween(90) else snap(),
        label = "bottom-tab-preactivation",
    )
    val pulseSignal = reselectionPulse(index)
    val pulsePhase = remember { Animatable(1f) }
    val restingAlpha = if (interactionSettings.highContrast) 0.72f else 0.52f
    val baseAlpha = lerp(restingAlpha, 0.78f, preactivationProgress)
    val contentTransform = transform(index)
    val pulseTransform = axialPulseTransform(pulsePhase.value)

    LaunchedEffect(isPreactivated, index, isInteractiveLayer) {
        if (isInteractiveLayer) {
            onPreactivation(index, isPreactivated)
        }
    }
    LaunchedEffect(pulseSignal, hasInteractiveMotion) {
        if (pulseSignal == 0 || !hasInteractiveMotion) {
            pulsePhase.snapTo(1f)
        } else {
            pulsePhase.snapTo(0f)
            pulsePhase.animateTo(
                targetValue = 1f,
                animationSpec = tween(420, easing = LinearEasing),
            )
        }
    }
    DisposableEffect(index, isInteractiveLayer) {
        onDispose {
            if (isInteractiveLayer) {
                onPreactivation(index, false)
            }
        }
    }

    val interactionModifier = if (isInteractiveLayer) {
        Modifier
            .clip(Capsule())
            .hoverable(interactionSource = interactionSource)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = { onClick(index) },
            )
    } else {
        Modifier
    }

    Column(
        modifier
            .then(interactionModifier)
            .fillMaxHeight()
            .weight(1f)
            .drawWithContent {
                if (isInteractiveLayer && preactivationProgress > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.16f * preactivationProgress),
                                accentColor.copy(alpha = 0.04f * preactivationProgress),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = 30.dp.toPx(),
                        ),
                        radius = 30.dp.toPx(),
                        center = center,
                    )
                }
                drawContent()
            }
            .graphicsLayer {
                alpha = if (isInteractiveLayer) baseAlpha else 1f
                scaleX = contentTransform.scaleX * pulseTransform.scaleX
                scaleY = contentTransform.scaleY * pulseTransform.scaleY
                translationY = contentTransform.translationY
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
