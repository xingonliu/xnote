package com.xnote.app.design.liquidglass

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import kotlin.math.sign

// Adapted from AndroidLiquidGlass catalog commit 65ab177 under Apache-2.0.

// -- Constants

private val BottomTabsInset = 4.dp
private val BottomTabsHeight = 56.dp
private val BottomTabHeight = 48.dp
private const val BottomTabPressedScale = 78f / 56f
private const val BottomTabPressedContentScale = 1.2f

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
        Color(0xFFFAFAFA).copy(alpha = 0.4f)
    } else {
        Color(0xFF121212).copy(alpha = 0.4f)
    }
    val hapticView = LocalView.current
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val currentOnTabReselected by rememberUpdatedState(onTabReselected)
    val tabsBackdrop = rememberLayerBackdrop()

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
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val offsetAnimation = remember { Animatable(0f) }
        var currentIndex by remember(selectedTabIndex, tabsCount) {
            mutableIntStateOf(selectedTabIndex().fastCoerceIn(0, tabsCount - 1))
        }
        var gestureValue by remember(selectedTabIndex, tabsCount) {
            mutableFloatStateOf(selectedTabIndex().toFloat())
        }
        var pendingPointerClickIndex by remember { mutableIntStateOf(-1) }

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

        fun selectTab(index: Int) {
            val safeIndex = index.fastCoerceIn(0, tabsCount - 1)
            if (safeIndex == currentIndex) {
                hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                currentOnTabReselected(safeIndex)
            } else {
                hapticView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                currentIndex = safeIndex
            }
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
                pressedScale = BottomTabPressedScale,
                onDragStarted = { position ->
                    pendingPointerClickIndex = -1
                    gestureValue = tabValueAtPosition(position.x)
                    animatePositionToValue(gestureValue)
                    hapticView.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                },
                onDragStopped = { wasDragged ->
                    val targetIndex = if (wasDragged) targetValue else gestureValue
                    val safeIndex = targetIndex.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    if (!wasDragged) {
                        pendingPointerClickIndex = safeIndex
                        animationScope.launch {
                            withFrameNanos { }
                            withFrameNanos { }
                            if (pendingPointerClickIndex == safeIndex) {
                                pendingPointerClickIndex = -1
                                animateToValue(currentIndex.toFloat())
                            }
                        }
                    } else if (safeIndex == currentIndex) {
                        animateToValue(safeIndex.toFloat())
                    } else {
                        currentIndex = safeIndex
                        hapticView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(1f, 300f, 0.5f),
                        )
                    }
                },
                onDragCancelled = {
                    animateToValue(currentIndex.toFloat())
                },
                onDrag = { _, position, dragAmount ->
                    gestureValue = tabValueAtPosition(position.x)
                    updateValue(gestureValue)
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }

        LaunchedEffect(selectedTabIndex, tabsCount) {
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
            selectedTabIndex().toFloat().fastCoerceIn(0f, (tabsCount - 1).toFloat())
        }
        val contentTransform: (Int) -> LiquidBottomTabTransform = { tabIndex ->
            val proximity = (1f - abs(tabIndex - indicatorValue)).fastCoerceIn(0f, 1f)
            val localPress = pressProgress * proximity
            LiquidBottomTabTransform(
                scaleX = lerp(1f, BottomTabPressedContentScale, localPress),
                scaleY = lerp(1f, BottomTabPressedContentScale, localPress),
            )
        }
        val indicatorRoundRect: (Size) -> RoundRect = { size ->
            val scaleX = if (hasInteractiveMotion) {
                val velocity = dampedDragAnimation.velocity / 10f
                dampedDragAnimation.scaleX /
                    (1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f))
            } else {
                1f
            }
            val scaleY = if (hasInteractiveMotion) {
                val velocity = dampedDragAnimation.velocity / 10f
                dampedDragAnimation.scaleY *
                    (1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f))
            } else {
                1f
            }
            val currentWidth = tabWidth * scaleX
            val currentHeight = (size.height - tabInset * 2f) * scaleY
            val centerX = tabCenterX(indicatorValue)
            val centerY = size.height / 2f
            val radius = CornerRadius(currentHeight / 2f, currentHeight / 2f)
            RoundRect(
                left = centerX - currentWidth / 2f,
                top = centerY - currentHeight / 2f,
                right = centerX + currentWidth / 2f,
                bottom = centerY + currentHeight / 2f,
                topLeftCornerRadius = radius,
                topRightCornerRadius = radius,
                bottomLeftCornerRadius = radius,
                bottomRightCornerRadius = radius,
            )
        }

        Box(
            modifier = Modifier
                .height(BottomTabsHeight)
                .fillMaxWidth()
                .then(if (hasInteractiveMotion) interactiveHighlight.gestureModifier else Modifier)
                .then(if (hasInteractiveMotion) dampedDragAnimation.modifier else Modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
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
                            val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, pressProgress)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(if (hasInteractiveMotion) interactiveHighlight.modifier else Modifier)
                    .height(BottomTabsHeight)
                    .fillMaxWidth(),
            )

            CompositionLocalProvider(
                LocalLiquidBottomTabTransform provides contentTransform,
                LocalLiquidBottomTabLayer provides LiquidBottomTabLayer.Base,
            ) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics { }
                        .graphicsLayer {
                            translationX = panelOffset
                            clip = true
                            shape = GenericShape { size, _ ->
                                val fullPath = Path().apply {
                                    addRect(Rect(0f, 0f, size.width, size.height))
                                }
                                val indicatorPath = Path().apply {
                                    addRoundRect(indicatorRoundRect(size))
                                }
                                op(fullPath, indicatorPath, PathOperation.Difference)
                            }
                        }
                        .height(BottomTabsHeight)
                        .fillMaxWidth()
                        .padding(BottomTabsInset),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }

            CompositionLocalProvider(
                LocalLiquidBottomTabTransform provides contentTransform,
                LocalLiquidBottomTabLayer provides LiquidBottomTabLayer.Highlight,
            ) {
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
                                    refractionHeight = 24.dp.toPx() * pressProgress,
                                    refractionAmount = 24.dp.toPx() * pressProgress,
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = pressProgress)
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(if (hasInteractiveMotion) interactiveHighlight.modifier else Modifier)
                        .height(BottomTabHeight)
                        .fillMaxWidth()
                        .padding(horizontal = BottomTabsInset)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }

            Box(
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .offset {
                        IntOffset(
                            x = (tabCenterX(indicatorValue) - tabWidth / 2f + panelOffset)
                                .fastRoundToInt(),
                            y = 0,
                        )
                    }
                    .width(tabWidthDp)
                    .height(BottomTabHeight)
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { Capsule() },
                        effects = {
                            lens(
                                refractionHeight = 10.dp.toPx() * pressProgress,
                                refractionAmount = 14.dp.toPx() * pressProgress,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = pressProgress)
                        },
                        shadow = {
                            Shadow(alpha = pressProgress)
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp * pressProgress,
                                alpha = pressProgress,
                            )
                        },
                        layerBlock = {
                            scaleX = if (hasInteractiveMotion) dampedDragAnimation.scaleX else 1f
                            scaleY = if (hasInteractiveMotion) dampedDragAnimation.scaleY else 1f
                            val velocity = if (hasInteractiveMotion) {
                                dampedDragAnimation.velocity / 10f
                            } else {
                                0f
                            }
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            drawRect(
                                color = if (isLightTheme) {
                                    Color.Black.copy(alpha = 0.1f)
                                } else {
                                    Color.White.copy(alpha = 0.1f)
                                },
                                alpha = 1f - pressProgress,
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * pressProgress))
                        },
                    ),
            )

            // The gesture modifiers stay on the parent so these semantic click targets share
            // the same pointer path without becoming a visual overlay above the glass.
            CompositionLocalProvider(
                LocalLiquidBottomTabTransform provides contentTransform,
                LocalLiquidBottomTabClick provides { index ->
                    pendingPointerClickIndex = -1
                    selectTab(index)
                },
                LocalLiquidBottomTabLayer provides LiquidBottomTabLayer.Interaction,
            ) {
                Row(
                    modifier = Modifier
                        .drawWithContent { }
                        .height(BottomTabsHeight)
                        .fillMaxWidth()
                        .padding(BottomTabsInset),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}
