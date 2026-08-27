package com.xnote.app.design.liquidglass

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.xnote.app.design.LocalXNoteInteractionSettings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

// Adapted from AndroidLiquidGlass catalog commit 65ab177 under Apache-2.0.

// -- Constants

private val BottomTabsInset = 4.dp
private val BottomTabsHeight = 56.dp
private val BottomTabHeight = 48.dp
private const val BottomTabPressedScaleX = 0.97f
private const val BottomTabPressedScaleY = 0.9f
private const val BridgeBreakStart = 0.42f
private const val BridgeBreakEnd = 0.56f

// -- Composables

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    onTabReselected: (index: Int) -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    require(tabsCount > 0) { "LiquidBottomTabs requires at least one tab." }

    val interactionSettings = LocalXNoteInteractionSettings.current
    val hasInteractiveMotion = !interactionSettings.reduceMotion
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > 0.5f
    val accentColor = colorScheme.primary
    val containerColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(0.4f)
    } else {
        Color(0xFF121212).copy(0.4f)
    }
    val tabsBackdrop = rememberLayerBackdrop()
    val indicatorBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val hapticView = LocalView.current
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val currentOnTabReselected by rememberUpdatedState(onTabReselected)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val tabInset = with(density) { BottomTabsInset.toPx() }
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - tabInset * 2f) / tabsCount
        }
        val tabWidthDp = with(density) { tabWidth.toDp() }
        val tabHeightPx = with(density) { BottomTabHeight.toPx() }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val dragDirection = if (isLtr) 1f else -1f
        val animationScope = rememberCoroutineScope()
        val offsetAnimation = remember { Animatable(0f) }
        val reselectionWavePhase = remember { Animatable(1f) }
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex().fastCoerceIn(0, tabsCount - 1))
        }
        var gestureValue by remember(selectedTabIndex) {
            mutableFloatStateOf(selectedTabIndex().toFloat())
        }
        var gestureAnchorIndex by remember { mutableIntStateOf(currentIndex) }
        var pressedTabIndex by remember { mutableIntStateOf(-1) }
        var isGestureActive by remember { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }
        var preactivatedIndex by remember { mutableIntStateOf(currentIndex) }
        var isPreactivationActive by remember { mutableStateOf(false) }
        var reselectedIndex by remember { mutableIntStateOf(-1) }
        var reselectionSerial by remember { mutableIntStateOf(0) }
        var suppressedPointerClickIndex by remember { mutableIntStateOf(-1) }

        val tabValueAtPosition: (Float) -> Float = { positionX ->
            val value = if (isLtr) {
                (positionX - tabInset) / tabWidth - 0.5f
            } else {
                (constraints.maxWidth - tabInset - positionX) / tabWidth - 0.5f
            }
            value.fastCoerceIn(0f, (tabsCount - 1).toFloat())
        }
        val tabCenterX: (Float) -> Float = { value ->
            if (isLtr) {
                tabInset + (value + 0.5f) * tabWidth
            } else {
                constraints.maxWidth - tabInset - (value + 0.5f) * tabWidth
            }
        }
        val panelOffset by remember(density, hasInteractiveMotion) {
            derivedStateOf {
                if (!hasInteractiveMotion) return@derivedStateOf 0f
                val fraction = (offsetAnimation.value / constraints.maxWidth)
                    .fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
        val preactivationProgress by animateFloatAsState(
            targetValue = if (isPreactivationActive) 1f else 0f,
            animationSpec = if (hasInteractiveMotion) tween(100) else snap(),
            label = "bottom-tab-lens-preactivation",
        )

        fun triggerReselection(index: Int) {
            reselectedIndex = index
            reselectionSerial += 1
            hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (hasInteractiveMotion) {
                animationScope.launch {
                    reselectionWavePhase.snapTo(0f)
                    reselectionWavePhase.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(480, easing = LinearOutSlowInEasing),
                    )
                }
            }
            currentOnTabReselected(index)
        }

        val dampedDragAnimation = remember(
            animationScope,
            hapticView,
            isLtr,
            tabInset,
            tabWidth,
            tabsCount,
        ) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScaleX = BottomTabPressedScaleX,
                pressedScaleY = BottomTabPressedScaleY,
                onDragStarted = { position ->
                    gestureAnchorIndex = currentIndex
                    gestureValue = tabValueAtPosition(position.x)
                    pressedTabIndex = gestureValue.fastRoundToInt()
                    isGestureActive = true
                    isDragging = false
                    hapticView.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                },
                onDragStopped = { wasDragged ->
                    isGestureActive = false
                    isDragging = false
                    pressedTabIndex = -1
                    val targetIndex = gestureValue.fastRoundToInt()
                        .fastCoerceIn(0, tabsCount - 1)
                    if (!wasDragged) {
                        suppressedPointerClickIndex = targetIndex
                        if (targetIndex == currentIndex) {
                            triggerReselection(targetIndex)
                            animateToValue(targetIndex.toFloat())
                        } else {
                            hapticView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            currentIndex = targetIndex
                        }
                        animationScope.launch {
                            withFrameNanos { }
                            withFrameNanos { }
                            if (suppressedPointerClickIndex == targetIndex) {
                                suppressedPointerClickIndex = -1
                            }
                        }
                    } else if (targetIndex == currentIndex) {
                        animateToValue(targetIndex.toFloat())
                    } else {
                        currentIndex = targetIndex
                    }
                    if (wasDragged) {
                        hapticView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(0.62f, 420f, 0.5f),
                        )
                    }
                },
                onDragCancelled = {
                    isGestureActive = false
                    isDragging = false
                    pressedTabIndex = -1
                    animateToValue(currentIndex.toFloat())
                },
                onDrag = { _, dragAmount ->
                    if (dragAmount != Offset.Zero) {
                        isDragging = true
                        gestureValue = (gestureValue + dragAmount.x / tabWidth * dragDirection)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                        pressedTabIndex = gestureValue.fastRoundToInt()
                        snapToValue(gestureValue)
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                },
            )
        }

        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index.fastCoerceIn(0, tabsCount - 1)
                }
        }
        LaunchedEffect(dampedDragAnimation, hasInteractiveMotion) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    if (hasInteractiveMotion) {
                        dampedDragAnimation.animateToValue(index.toFloat())
                    }
                    currentOnTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        x = tabCenterX(dampedDragAnimation.value) + panelOffset,
                        y = size.height / 2f,
                    )
                },
            )
        }
        val pressProgress = if (hasInteractiveMotion) {
            dampedDragAnimation.pressProgress
        } else {
            0f
        }
        val indicatorValue = if (hasInteractiveMotion) {
            dampedDragAnimation.value
        } else {
            selectedTabIndex().toFloat()
        }
        val pressureValue = when {
            isGestureActive -> gestureValue
            pressedTabIndex >= 0 -> pressedTabIndex.toFloat()
            else -> dampedDragAnimation.targetValue
        }
        val tabContentTransform: (Int) -> LiquidBottomTabTransform = { tabIndex ->
            val proximity = (1f - abs(tabIndex - pressureValue)).fastCoerceIn(0f, 1f)
            val localPress = pressProgress * proximity
            LiquidBottomTabTransform(
                scaleX = lerp(1f, 0.94f, localPress),
                scaleY = lerp(1f, 0.86f, localPress),
                translationY = with(density) { 2.dp.toPx() } * localPress,
            )
        }
        val bridgeDistance = if (isDragging) {
            abs(indicatorValue - gestureAnchorIndex)
        } else {
            0f
        }
        val bridgeBreakProgress = (
            (bridgeDistance - BridgeBreakStart) / (BridgeBreakEnd - BridgeBreakStart)
            ).fastCoerceIn(0f, 1f)
        val bridgeAlpha = if (hasInteractiveMotion && isDragging) {
            pressProgress * (1f - bridgeBreakProgress)
        } else {
            0f
        }
        val bridgeTension = (bridgeDistance / BridgeBreakEnd).fastCoerceIn(0f, 1f)
        val anchorCenterX = tabCenterX(gestureAnchorIndex.toFloat()) + panelOffset
        val indicatorCenterX = tabCenterX(indicatorValue) + panelOffset
        val bridgeWidth = abs(indicatorCenterX - anchorCenterX) + tabWidth * 0.58f
        val bridgeHeight = lerp(tabHeightPx * 0.72f, tabHeightPx * 0.24f, bridgeTension)
        val bridgeLeft = min(anchorCenterX, indicatorCenterX) - tabWidth * 0.29f
        val bridgeTop = (tabHeightPx - bridgeHeight) / 2f

        if (preactivationProgress > 0f) {
            Box(
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .offset {
                        IntOffset(
                            x = (tabCenterX(preactivatedIndex.toFloat()) - tabWidth / 2f)
                                .fastRoundToInt(),
                            y = 0,
                        )
                    }
                    .graphicsLayer { alpha = preactivationProgress }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            blur(3.dp.toPx())
                            lens(6.dp.toPx(), 8.dp.toPx())
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = 0.28f * preactivationProgress)
                        },
                        onDrawSurface = {
                            drawRect(accentColor.copy(alpha = 0.025f * preactivationProgress))
                        },
                    )
                    .height(BottomTabHeight)
                    .width(tabWidthDp),
            )
        }

        Canvas(
            modifier = Modifier
                .clearAndSetSemantics { }
                .height(BottomTabsHeight)
                .fillMaxWidth(),
        ) {
            val phase = reselectionWavePhase.value
            if (phase < 1f && reselectedIndex >= 0) {
                val remaining = 1f - phase
                drawCircle(
                    color = accentColor.copy(alpha = 0.24f * remaining * remaining),
                    radius = lerp(10.dp.toPx(), 58.dp.toPx(), phase),
                    center = Offset(
                        x = tabCenterX(reselectedIndex.toFloat()) + panelOffset,
                        y = size.height / 2f - 48.dp.toPx() * phase,
                    ),
                    style = Stroke(width = lerp(2.dp.toPx(), 0.5.dp.toPx(), phase)),
                )
            }
        }

        CompositionLocalProvider(
            LocalLiquidBottomTabTransform provides tabContentTransform,
            LocalLiquidBottomTabClick provides { index ->
                val safeIndex = index.fastCoerceIn(0, tabsCount - 1)
                if (safeIndex == suppressedPointerClickIndex) {
                    suppressedPointerClickIndex = -1
                } else if (safeIndex == currentIndex) {
                    triggerReselection(safeIndex)
                } else {
                    hapticView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    currentIndex = safeIndex
                }
            },
            LocalLiquidBottomTabPreactivation provides { index, active ->
                if (active) {
                    preactivatedIndex = index.fastCoerceIn(0, tabsCount - 1)
                    isPreactivationActive = true
                } else if (preactivatedIndex == index) {
                    isPreactivationActive = false
                }
            },
            LocalLiquidBottomTabReselectionPulse provides { index ->
                if (index == reselectedIndex) reselectionSerial else 0
            },
        ) {
            CompositionLocalProvider(LocalLiquidBottomTabLayer provides LiquidBottomTabLayer.Base) {
                Row(
                    modifier = Modifier
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(24.dp.toPx(), 24.dp.toPx())
                            },
                            layerBlock = {
                                transformOrigin = TransformOrigin.Center
                                scaleX = lerp(1f, 1f + 4.dp.toPx() / size.width, pressProgress)
                                scaleY = lerp(1f, 0.985f, pressProgress)
                                translationY = 1.dp.toPx() * pressProgress
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(
                            if (hasInteractiveMotion) interactiveHighlight.modifier else Modifier,
                        )
                        .height(BottomTabsHeight)
                        .fillMaxWidth()
                        .padding(BottomTabsInset),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }

            // This hidden tinted layer is recorded separately; the liquid shapes below are
            // the only reveal mask, so partially covered vector paths stay geometrically split.
            CompositionLocalProvider(LocalLiquidBottomTabLayer provides LiquidBottomTabLayer.Highlight) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics { }
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(
                                    24.dp.toPx() * pressProgress,
                                    24.dp.toPx() * pressProgress,
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = pressProgress)
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(
                            if (hasInteractiveMotion) interactiveHighlight.modifier else Modifier,
                        )
                        .height(BottomTabHeight)
                        .fillMaxWidth()
                        .padding(horizontal = BottomTabsInset)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }

        if (bridgeAlpha > 0f) {
            Box(
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .offset {
                        IntOffset(bridgeLeft.fastRoundToInt(), bridgeTop.fastRoundToInt())
                    }
                    .graphicsLayer { alpha = bridgeAlpha }
                    .drawBackdrop(
                        backdrop = indicatorBackdrop,
                        shape = { Capsule() },
                        effects = {
                            lens(
                                6.dp.toPx() * bridgeAlpha,
                                10.dp.toPx() * bridgeAlpha,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = 0.42f * bridgeAlpha)
                        },
                        onDrawSurface = {
                            drawRect(accentColor.copy(alpha = 0.025f * bridgeAlpha))
                        },
                    )
                    .height(with(density) { bridgeHeight.toDp() })
                    .width(with(density) { bridgeWidth.toDp() }),
            )

            Box(
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .offset {
                        IntOffset(
                            x = (anchorCenterX - tabWidth / 2f).fastRoundToInt(),
                            y = 0,
                        )
                    }
                    .graphicsLayer { alpha = bridgeAlpha * 0.72f }
                    .drawBackdrop(
                        backdrop = indicatorBackdrop,
                        shape = { Capsule() },
                        effects = {
                            lens(
                                5.dp.toPx() * bridgeAlpha,
                                8.dp.toPx() * bridgeAlpha,
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = 0.3f * bridgeAlpha)
                        },
                        onDrawSurface = {
                            drawRect(accentColor.copy(alpha = 0.02f * bridgeAlpha))
                        },
                    )
                    .height(BottomTabHeight)
                    .width(tabWidthDp),
            )
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (tabCenterX(indicatorValue) - tabWidth / 2f + panelOffset)
                            .fastRoundToInt(),
                        y = 0,
                    )
                }
                .drawBackdrop(
                    backdrop = indicatorBackdrop,
                    shape = { Capsule() },
                    effects = {
                        lens(
                            10.dp.toPx() * pressProgress,
                            14.dp.toPx() * pressProgress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(
                            alpha = lerp(0.18f, 1f, pressProgress),
                        )
                    },
                    shadow = {
                        Shadow(alpha = lerp(0.12f, 1f, pressProgress))
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 2.dp + 7.dp * pressProgress,
                            alpha = lerp(0.12f, 1f, pressProgress),
                        )
                    },
                    layerBlock = {
                        transformOrigin = TransformOrigin.Center
                        scaleX = if (hasInteractiveMotion) dampedDragAnimation.scaleX else 1f
                        scaleY = if (hasInteractiveMotion) dampedDragAnimation.scaleY else 1f
                        translationY = 2.dp.toPx() * pressProgress
                        val velocity = if (hasInteractiveMotion) {
                            dampedDragAnimation.velocity / 10f
                        } else {
                            0f
                        }
                        scaleX /= 1f - (velocity * 0.78f).fastCoerceIn(-0.22f, 0.22f)
                        scaleY *= 1f - (velocity * 0.28f).fastCoerceIn(-0.18f, 0.18f)
                    },
                    onDrawSurface = {
                        drawRect(
                            color = if (isLightTheme) {
                                Color.Black.copy(0.1f)
                            } else {
                                Color.White.copy(0.1f)
                            },
                            alpha = 1f - pressProgress,
                        )
                        drawRect(accentColor.copy(alpha = 0.035f + 0.025f * pressProgress))
                    },
                )
                .height(BottomTabHeight)
                .width(tabWidthDp),
        )

        Box(
            modifier = Modifier
                .height(BottomTabsHeight)
                .fillMaxWidth()
                .then(
                    if (hasInteractiveMotion) interactiveHighlight.gestureModifier else Modifier,
                )
                .then(if (hasInteractiveMotion) dampedDragAnimation.modifier else Modifier),
        )
    }
}
