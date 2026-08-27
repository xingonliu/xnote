package com.xnote.app.design.liquidglass

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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
private const val BottomTabPressedScale = 70f / 48f

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
        val dragDirection = if (isLtr) 1f else -1f
        val tabValueAtPosition: (Float) -> Float = { positionX ->
            val value = if (isLtr) {
                (positionX - tabInset) / tabWidth - 0.5f
            } else {
                (constraints.maxWidth - tabInset - positionX) / tabWidth - 0.5f
            }
            value.fastCoerceIn(0f, (tabsCount - 1).toFloat())
        }
        val offsetAnimation = remember { Animatable(0f) }
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
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        var gestureValue by remember(selectedTabIndex) {
            mutableFloatStateOf(selectedTabIndex().toFloat())
        }
        val dampedDragAnimation = remember(
            animationScope,
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
                    gestureValue = tabValueAtPosition(position.x)
                    snapToValue(gestureValue)
                },
                onDragStopped = {
                    val targetIndex = gestureValue.fastRoundToInt()
                        .fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(1f, 300f, 0.5f),
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    gestureValue = (gestureValue + dragAmount.x / tabWidth * dragDirection)
                        .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    snapToValue(gestureValue)
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }

        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index -> currentIndex = index }
        }
        LaunchedEffect(dampedDragAnimation, hasInteractiveMotion) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    if (hasInteractiveMotion) {
                        dampedDragAnimation.animateToValue(index.toFloat())
                    }
                    onTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        x = if (isLtr) {
                            tabInset +
                                (dampedDragAnimation.value + 0.5f) * tabWidth +
                                panelOffset
                        } else {
                            size.width -
                                tabInset -
                                (dampedDragAnimation.value + 0.5f) * tabWidth +
                                panelOffset
                        },
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
        val tabContentScale: (Int) -> Float = { tabIndex ->
            val proximity = (1f - abs(tabIndex - indicatorValue)).fastCoerceIn(0f, 1f)
            lerp(1f, 1.2f, pressProgress * proximity)
        }

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides tabContentScale,
            LocalLiquidBottomTabClick provides { index ->
                currentIndex = index.fastCoerceIn(0, tabsCount - 1)
            },
        ) {
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
                            val progress = pressProgress
                            val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
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
                            val progress = pressProgress
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(
                                24.dp.toPx() * progress,
                                24.dp.toPx() * progress,
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(
                                alpha = pressProgress,
                            )
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

        Box(
            modifier = Modifier
                .offset {
                    val indicatorX = if (isLtr) {
                        tabInset + indicatorValue * tabWidth + panelOffset
                    } else {
                        constraints.maxWidth -
                            tabInset -
                            (indicatorValue + 1f) * tabWidth +
                            panelOffset
                    }
                    IntOffset(indicatorX.fastRoundToInt(), 0)
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(
                            alpha = pressProgress,
                        )
                    },
                    shadow = {
                        Shadow(alpha = pressProgress)
                    },
                    innerShadow = {
                        val progress = pressProgress
                        InnerShadow(
                            radius = 8.dp * progress,
                            alpha = progress,
                        )
                    },
                    layerBlock = {
                        transformOrigin = TransformOrigin.Center
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
                        val progress = pressProgress
                        drawRect(
                            color = if (isLightTheme) {
                                Color.Black.copy(0.1f)
                            } else {
                                Color.White.copy(0.1f)
                            },
                            alpha = 1f - progress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
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
