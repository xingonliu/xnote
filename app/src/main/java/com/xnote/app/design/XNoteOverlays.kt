package com.xnote.app.design

import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.LocalContentColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xnote.app.design.liquidglass.LiquidButton

// -- Type Definitions

@Immutable
data class XNoteDialogAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

enum class XNoteDrawerPlacement {
    Bottom,
    End,
}

enum class XNotePopupPlacement {
    BelowStart,
    BelowEnd,
    AboveStart,
    AboveEnd,
}

@Stable
class XNotePopupAnchor internal constructor() {
    internal var boundsInRoot by mutableStateOf<Rect?>(null)
        private set

    internal fun update(bounds: Rect) {
        boundsInRoot = bounds
    }
}

internal data class XNotePopupSafeInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

@Immutable
data class XNoteDropdownMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val destructive: Boolean = false,
)

// -- State

@Composable
fun rememberXNoteToastHostState(): SnackbarHostState = remember { SnackbarHostState() }

@Composable
fun rememberXNotePopupAnchor(): XNotePopupAnchor = remember { XNotePopupAnchor() }

// -- Composables

@Composable
fun XNoteDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    backdrop: Backdrop,
    confirmAction: XNoteDialogAction,
    modifier: Modifier = Modifier,
    dismissAction: XNoteDialogAction? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismissRequest)
    val settings = LocalXNoteInteractionSettings.current
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val accentColor = MaterialTheme.colorScheme.primary
    val containerColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.6f)
    } else {
        Color(0xFF121212).copy(alpha = 0.4f)
    }
    val dimColor = if (isLightTheme) {
        Color(0xFF29293A).copy(alpha = 0.23f)
    } else {
        Color(0xFF121212).copy(alpha = 0.56f)
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = xNoteFadeIn(settings.reduceMotion),
        exit = xNoteFadeOut(settings.reduceMotion),
    ) {
        XNoteOverlayContainer(
            onDismissRequest = onDismissRequest,
            scrimColor = dimColor,
        ) {
            XNoteLiquidGlassPanel(
                backdrop = backdrop,
                shape = RoundedRectangle(48.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(XNoteSpacingLarge)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .xNoteOverlayInputBarrier()
                    .semantics { paneTitle = title },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = contentColor,
                        modifier = Modifier.padding(
                            start = 28.dp,
                            top = 24.dp,
                            end = 28.dp,
                            bottom = 12.dp,
                        ),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 24.dp,
                                top = 12.dp,
                                end = 24.dp,
                                bottom = 12.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
                        content = content,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 24.dp,
                                top = 12.dp,
                                end = 24.dp,
                                bottom = 24.dp,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(
                            16.dp,
                            Alignment.CenterHorizontally,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissAction != null) {
                            XNoteDialogButton(
                                action = dismissAction,
                                backdrop = backdrop,
                                foreground = contentColor,
                                surfaceColor = containerColor.copy(alpha = 0.2f),
                            )
                        }
                        XNoteDialogButton(
                            action = confirmAction,
                            backdrop = backdrop,
                            foreground = Color.White,
                            surfaceColor = accentColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun XNoteDrawer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    backdrop: Backdrop,
    placement: XNoteDrawerPlacement,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismissRequest)
    val settings = LocalXNoteInteractionSettings.current
    val transition = updateTransition(visible, label = "XNoteDrawer")

    Box(modifier = modifier.fillMaxSize()) {
        transition.AnimatedVisibility(
            visible = { it },
            modifier = Modifier.fillMaxSize(),
            enter = xNoteScrimEnter(settings.reduceMotion),
            exit = xNoteScrimExit(settings.reduceMotion),
        ) {
            XNoteDismissLayer(
                onDismissRequest = onDismissRequest,
                scrimColor = Color.Black.copy(alpha = 0.32f),
            )
        }

        val panelModifier = if (placement == XNoteDrawerPlacement.Bottom) {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
        } else {
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .widthIn(max = 420.dp)
                .fillMaxWidth()
        }

        transition.AnimatedVisibility(
            visible = { it },
            modifier = panelModifier,
            enter = xNoteDrawerPanelEnter(settings.reduceMotion, placement),
            exit = xNoteDrawerPanelExit(settings.reduceMotion, placement),
        ) {
            XNoteLiquidGlassPanel(
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxSize()
                    .xNoteOverlayInputBarrier()
                    .semantics { paneTitle = title },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .verticalScroll(rememberScrollState())
                        .padding(XNoteSpacingLarge),
                    verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    content()
                }
            }
        }
    }
}

@Composable
fun BoxScope.XNotePopup(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    anchor: XNotePopupAnchor? = null,
    placement: XNotePopupPlacement = XNotePopupPlacement.BelowEnd,
    shape: Shape = XNoteSmoothCornerShape(XNotePopupRadius),
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismissRequest)
    val settings = LocalXNoteInteractionSettings.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing
    val safeInsets = XNotePopupSafeInsets(
        left = safeDrawing.getLeft(density, layoutDirection),
        top = safeDrawing.getTop(density),
        right = safeDrawing.getRight(density, layoutDirection),
        bottom = safeDrawing.getBottom(density),
    )
    val popupGap = with(density) { XNoteSpacingSmall.roundToPx() }
    val edgePadding = with(density) { XNoteSpacingSmall.roundToPx() }
    val maxPopupWidth = with(density) { 360.dp.roundToPx() }
    val maxPopupHeight = with(density) { 480.dp.roundToPx() }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var transformOrigin by remember { mutableStateOf(popupTransformOrigin(placement)) }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = EnterTransition.None,
        exit = ExitTransition.None,
    ) {
        val progress = transition.animateFloat(
            transitionSpec = {
                if (settings.reduceMotion) tween(0)
                else if (targetState == EnterExitState.Visible) spring(
                    dampingRatio = XNotePopupSpringDampingRatio,
                    stiffness = XNotePopupSpringStiffness,
                ) else tween(XNotePopupExitDurationMillis, easing = FastOutLinearInEasing)
            },
            label = "PopupExpansion",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val opacity = transition.animateFloat(
            transitionSpec = {
                tween(if (settings.reduceMotion) 0 else if (targetState == EnterExitState.Visible)
                    XNotePopupFadeInDurationMillis else XNotePopupExitDurationMillis)
            },
            label = "PopupOpacity",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        Box(modifier = Modifier.fillMaxSize()) {
            XNoteDismissLayer(onDismissRequest = onDismissRequest)
            Layout(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { hostOrigin = it.positionInRoot() },
                content = {
                    XNoteLiquidGlassPanel(
                        backdrop = backdrop,
                        shape = shape,
                        modifier = modifier
                            .graphicsLayer {
                                val initialX = XNotePopupInitialScale
                                val initialY = XNotePopupInitialScaleY
                                scaleX = initialX + (1f - initialX) * progress.value
                                scaleY = initialY + (1f - initialY) * progress.value
                                alpha = opacity.value
                                this.transformOrigin = transformOrigin
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            .heightIn(max = 480.dp)
                            .xNoteOverlayInputBarrier(),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .padding(XNoteSpacingSmall),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            content = content,
                        )
                    }
                },
            ) { measurables, constraints ->
                val safeWidth = (
                    constraints.maxWidth - safeInsets.left - safeInsets.right - edgePadding * 2
                ).coerceAtLeast(0)
                val safeHeight = (
                    constraints.maxHeight - safeInsets.top - safeInsets.bottom - edgePadding * 2
                ).coerceAtLeast(0)
                val popupConstraints = constraints.copy(
                    minWidth = 0,
                    minHeight = 0,
                    maxWidth = minOf(safeWidth, maxPopupWidth),
                    maxHeight = minOf(safeHeight, maxPopupHeight),
                )
                val popup = measurables.single().measure(popupConstraints)
                val popupOffset = calculatePopupOffset(
                    hostWidth = constraints.maxWidth,
                    hostHeight = constraints.maxHeight,
                    popupWidth = popup.width,
                    popupHeight = popup.height,
                    anchorBoundsInRoot = anchor?.boundsInRoot,
                    hostOriginInRoot = hostOrigin,
                    placement = placement,
                    safeInsets = safeInsets,
                    popupGap = popupGap,
                    edgePadding = edgePadding,
                )
                transformOrigin = calculatePopupTransformOrigin(
                    anchor?.boundsInRoot?.translate(-hostOrigin), popupOffset,
                    popup.width, popup.height, placement,
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    popup.place(popupOffset.x, popupOffset.y)
                }
            }
        }
    }
}

@Composable
fun BoxScope.XNoteDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<XNoteDropdownMenuItem>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    anchor: XNotePopupAnchor? = null,
    placement: XNotePopupPlacement = XNotePopupPlacement.BelowEnd,
    shape: Shape = XNoteSmoothCornerShape(XNotePopupRadius),
) {
    XNotePopup(
        visible = expanded,
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        modifier = modifier,
        anchor = anchor,
        placement = placement,
        shape = shape,
    ) {
        items.forEach { item ->
            val foreground = when {
                item.destructive -> MaterialTheme.colorScheme.error
                item.selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (item.selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        },
                        shape = XNoteSmoothCornerShape(XNoteRadiusSmall),
                    )
                    .clickable(
                        enabled = item.enabled,
                        role = Role.Button,
                        onClick = {
                            item.onClick()
                            onDismissRequest()
                        },
                    )
                    .semantics { selected = item.selected }
                    .heightIn(min = XNoteButtonSize)
                    .padding(horizontal = XNoteSpacingMedium, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = foreground.copy(alpha = if (item.enabled) 1f else 0.48f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun XNoteToastHost(
    hostState: SnackbarHostState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    dismissLabel: String,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )
            .padding(XNoteSpacingMedium),
    ) { data ->
        XNoteLiquidGlassPanel(
            backdrop = backdrop,
            shape = XNoteSmoothCornerShape(XNoteRadiusMedium),
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = XNoteSpacingMedium, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                data.visuals.actionLabel?.let { label ->
                    LiquidButton(
                        onClick = data::performAction,
                        backdrop = backdrop,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalContentColor.current,
                        )
                    }
                }
                if (data.visuals.withDismissAction) {
                    LiquidButton(
                        onClick = data::dismiss,
                        backdrop = backdrop,
                    ) {
                        Text(
                            text = dismissLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalContentColor.current,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun XNoteOverlayContainer(
    onDismissRequest: () -> Unit,
    scrimColor: Color = Color.Black.copy(alpha = 0.32f),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        XNoteDismissLayer(
            onDismissRequest = onDismissRequest,
            scrimColor = scrimColor,
        )
        content()
    }
}

@Composable
private fun XNoteDismissLayer(
    onDismissRequest: () -> Unit,
    scrimColor: Color = Color.Transparent,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor)
            .clearAndSetSemantics { testTag = "xnote-overlay-scrim" }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismissRequest,
            ),
    )
}

@Composable
private fun RowScope.XNoteDialogButton(
    action: XNoteDialogAction,
    backdrop: Backdrop,
    foreground: Color,
    surfaceColor: Color,
) {
    LiquidButton(
        onClick = action.onClick,
        backdrop = backdrop,
        enabled = action.enabled,
        surfaceColor = surfaceColor,
        modifier = Modifier
            .weight(1f),
    ) {
        Text(
            text = action.label,
            style = MaterialTheme.typography.titleMedium,
            color = foreground,
        )
    }
}

// -- Functions

fun Modifier.xNotePopupAnchor(anchor: XNotePopupAnchor): Modifier = onGloballyPositioned {
    anchor.update(it.boundsInRoot())
}

internal fun calculatePopupOffset(
    hostWidth: Int,
    hostHeight: Int,
    popupWidth: Int,
    popupHeight: Int,
    anchorBoundsInRoot: Rect?,
    hostOriginInRoot: Offset,
    placement: XNotePopupPlacement,
    safeInsets: XNotePopupSafeInsets,
    popupGap: Int,
    edgePadding: Int,
): IntOffset {
    val minX = safeInsets.left + edgePadding
    val minY = safeInsets.top + edgePadding
    val maxX = (hostWidth - safeInsets.right - edgePadding - popupWidth).coerceAtLeast(minX)
    val maxY = (hostHeight - safeInsets.bottom - edgePadding - popupHeight).coerceAtLeast(minY)
    val anchor = anchorBoundsInRoot ?: return IntOffset(maxX, minY)
    val anchorLeft = (anchor.left - hostOriginInRoot.x).toInt()
    val anchorTop = (anchor.top - hostOriginInRoot.y).toInt()
    val anchorRight = (anchor.right - hostOriginInRoot.x).toInt()
    val anchorBottom = (anchor.bottom - hostOriginInRoot.y).toInt()
    val alignEnd = placement == XNotePopupPlacement.BelowEnd ||
        placement == XNotePopupPlacement.AboveEnd
    val preferBelow = placement == XNotePopupPlacement.BelowStart ||
        placement == XNotePopupPlacement.BelowEnd
    val preferredX = if (alignEnd) anchorRight - popupWidth else anchorLeft
    val belowY = anchorBottom + popupGap
    val aboveY = anchorTop - popupGap - popupHeight
    val fitsBelow = belowY <= maxY
    val fitsAbove = aboveY >= minY
    val preferredY = when {
        preferBelow && (fitsBelow || !fitsAbove) -> belowY
        preferBelow -> aboveY
        fitsAbove || !fitsBelow -> aboveY
        else -> belowY
    }
    return IntOffset(
        x = preferredX.coerceIn(minX, maxX),
        y = preferredY.coerceIn(minY, maxY),
    )
}

private fun xNoteScrimEnter(reduceMotion: Boolean): EnterTransition = if (reduceMotion) {
    EnterTransition.None
} else {
    fadeIn(tween(XNoteOverlayScrimDurationMillis))
}

private fun xNoteScrimExit(reduceMotion: Boolean): ExitTransition = if (reduceMotion) {
    ExitTransition.None
} else {
    fadeOut(tween(XNoteOverlayScrimDurationMillis))
}

private fun xNoteDrawerPanelEnter(
    reduceMotion: Boolean,
    placement: XNoteDrawerPlacement,
): EnterTransition = when {
    reduceMotion -> EnterTransition.None
    placement == XNoteDrawerPlacement.Bottom -> slideInVertically(
        animationSpec = tween(XNoteOverlayScrimDurationMillis),
        initialOffsetY = { it },
    )
    else -> slideInHorizontally(
        animationSpec = tween(XNoteOverlayScrimDurationMillis),
        initialOffsetX = { it },
    )
}

private fun xNoteDrawerPanelExit(
    reduceMotion: Boolean,
    placement: XNoteDrawerPlacement,
): ExitTransition = when {
    reduceMotion -> ExitTransition.None
    placement == XNoteDrawerPlacement.Bottom -> slideOutVertically(
        animationSpec = tween(XNoteOverlayScrimDurationMillis),
        targetOffsetY = { it },
    )
    else -> slideOutHorizontally(
        animationSpec = tween(XNoteOverlayScrimDurationMillis),
        targetOffsetX = { it },
    )
}

internal fun calculatePopupTransformOrigin(
    anchorBounds: Rect?,
    popupOffset: IntOffset,
    popupWidth: Int,
    popupHeight: Int,
    placement: XNotePopupPlacement,
): TransformOrigin {
    if (anchorBounds == null || popupWidth == 0 || popupHeight == 0) return popupTransformOrigin(placement)
    return TransformOrigin(
        ((anchorBounds.center.x - popupOffset.x) / popupWidth).coerceIn(0f, 1f),
        ((anchorBounds.center.y - popupOffset.y) / popupHeight).coerceIn(0f, 1f),
    )
}

private fun popupTransformOrigin(placement: XNotePopupPlacement): TransformOrigin = when (placement) {
    XNotePopupPlacement.BelowStart -> TransformOrigin(0f, 0f)
    XNotePopupPlacement.BelowEnd -> TransformOrigin(1f, 0f)
    XNotePopupPlacement.AboveStart -> TransformOrigin(0f, 1f)
    XNotePopupPlacement.AboveEnd -> TransformOrigin(1f, 1f)
}

private fun Modifier.xNoteOverlayInputBarrier(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent()
        }
    }
}

private fun xNoteFadeIn(reduceMotion: Boolean): EnterTransition = if (reduceMotion) {
    EnterTransition.None
} else {
    fadeIn(tween(XNoteShortAnimationDurationMillis))
}

private fun xNoteFadeOut(reduceMotion: Boolean): ExitTransition = if (reduceMotion) {
    ExitTransition.None
} else {
    fadeOut(tween(XNoteShortAnimationDurationMillis))
}
