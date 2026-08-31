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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

// Adapted from AndroidLiquidGlass catalog commit 65ab177 under Apache-2.0.

// -- Constants

private val BottomTabsInset = 4.dp
private val BottomTabsHeight = 64.dp
private val BottomTabHeight = 56.dp
private const val BottomTabPressedScale = 78f / 56f

// -- Composables

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    require(tabsCount > 0) { "LiquidBottomTabs requires at least one tab." }

    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accentColor = MaterialTheme.colorScheme.primary
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    val hapticView = LocalView.current
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
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val offsetAnimation = remember { Animatable(0f) }
        var currentIndex by remember(selectedTabIndex, tabsCount) {
            mutableIntStateOf(selectedTabIndex().fastCoerceIn(0, tabsCount - 1))
        }
        var lastHapticTab by remember { mutableIntStateOf(currentIndex) }

        val tabValueAtPosition: (Float) -> Float = { positionX ->
            val value = if (isLtr) {
                (positionX - tabInset) / tabWidth - 0.5f
            } else {
                (constraints.maxWidth - tabInset - positionX) / tabWidth - 0.5f
            }
            value.fastCoerceIn(0f, (tabsCount - 1).toFloat())
        }

        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
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
                    val touchedValue = tabValueAtPosition(position.x)
                    updateValue(touchedValue)
                    lastHapticTab = touchedValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    hapticView.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                },
                onDragStopped = { wasDragged ->
                    val targetIndex = value.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f),
                        )
                    }
                    if (targetIndex != currentIndex) {
                        currentIndex = targetIndex
                        hapticView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onTabSelected(targetIndex)
                    } else {
                        hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                },
                onDragCancelled = {
                    animateToValue(currentIndex.toFloat())
                },
                onDrag = { _, position, dragAmount ->
                    val dragValue = tabValueAtPosition(position.x)
                    updateValue(dragValue)
                    val currentHoverTab = dragValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    if (currentHoverTab != lastHapticTab) {
                        lastHapticTab = currentHoverTab
                        hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }

        LaunchedEffect(selectedTabIndex, tabsCount) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    val safeIndex = index.fastCoerceIn(0, tabsCount - 1)
                    if (safeIndex != currentIndex) {
                        currentIndex = safeIndex
                    }
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                }
        }

        Box(
            modifier = Modifier
                .height(BottomTabsHeight)
                .fillMaxWidth()
                .then(dampedDragAnimation.modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx(), 24.dp.toPx())
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .height(BottomTabsHeight)
                    .fillMaxWidth()
                    .padding(BottomTabsInset),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )

            CompositionLocalProvider(
                LocalLiquidBottomTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                },
            ) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(
                                    24.dp.toPx() * progress,
                                    24.dp.toPx() * progress,
                                )
                            },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Default.copy(alpha = progress)
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
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
                    .padding(horizontal = BottomTabsInset)
                    .graphicsLayer {
                        translationX =
                            if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                            else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    }
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            lens(
                                10.dp.toPx() * progress,
                                14.dp.toPx() * progress,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        shadow = {
                            val progress = dampedDragAnimation.pressProgress
                            Shadow(
                                radius = 10.dp * progress,
                                color = if (isLightTheme) Color.White.copy(0.4f) else Color.White.copy(0.25f),
                                alpha = progress,
                                blendMode = BlendMode.Plus,
                            )
                        },
                        innerShadow = {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 8.dp * progress,
                                alpha = progress,
                            )
                        },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(
                                if (isLightTheme) Color.Black.copy(0.08f)
                                else Color.White.copy(0.08f),
                                alpha = 1f - progress,
                            )
                            drawRect(
                                if (isLightTheme) Color.White.copy(0.08f * progress)
                                else Color.White.copy(0.04f * progress),
                                blendMode = BlendMode.Plus,
                            )
                        },
                    )
                    .height(BottomTabHeight)
                    .fillMaxWidth(1f / tabsCount),
            )
        }
    }
}


