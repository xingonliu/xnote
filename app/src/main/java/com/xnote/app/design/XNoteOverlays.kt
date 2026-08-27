package com.xnote.app.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
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

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = xNoteFadeIn(settings.reduceMotion),
        exit = xNoteFadeOut(settings.reduceMotion),
    ) {
        XNoteOverlayContainer(onDismissRequest = onDismissRequest) {
            XNoteLiquidGlassPanel(
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(XNoteSpacingLarge)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .xNoteOverlayInputBarrier()
                    .semantics { paneTitle = title },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(XNoteSpacingLarge),
                    verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    content()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            XNoteSpacingSmall,
                            Alignment.End,
                        ),
                    ) {
                        if (dismissAction != null) {
                            XNoteDialogButton(
                                action = dismissAction,
                                backdrop = backdrop,
                            )
                        }
                        XNoteDialogButton(
                            action = confirmAction,
                            backdrop = backdrop,
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
    val enter = when {
        settings.reduceMotion -> EnterTransition.None
        placement == XNoteDrawerPlacement.Bottom -> slideInVertically(
            animationSpec = tween(XNoteShortAnimationDurationMillis),
            initialOffsetY = { it },
        ) + fadeIn(tween(XNoteShortAnimationDurationMillis))
        else -> slideInHorizontally(
            animationSpec = tween(XNoteShortAnimationDurationMillis),
            initialOffsetX = { it },
        ) + fadeIn(tween(XNoteShortAnimationDurationMillis))
    }
    val exit = when {
        settings.reduceMotion -> ExitTransition.None
        placement == XNoteDrawerPlacement.Bottom -> slideOutVertically(
            animationSpec = tween(XNoteShortAnimationDurationMillis),
            targetOffsetY = { it },
        ) + fadeOut(tween(XNoteShortAnimationDurationMillis))
        else -> slideOutHorizontally(
            animationSpec = tween(XNoteShortAnimationDurationMillis),
            targetOffsetX = { it },
        ) + fadeOut(tween(XNoteShortAnimationDurationMillis))
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = enter,
        exit = exit,
    ) {
        XNoteOverlayContainer(onDismissRequest = onDismissRequest) {
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

            XNoteLiquidGlassPanel(
                backdrop = backdrop,
                modifier = panelModifier
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
    alignment: Alignment = Alignment.TopEnd,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismissRequest)
    val settings = LocalXNoteInteractionSettings.current

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = xNoteFadeIn(settings.reduceMotion),
        exit = xNoteFadeOut(settings.reduceMotion),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            XNoteDismissLayer(onDismissRequest = onDismissRequest)
            XNoteLiquidGlassPanel(
                backdrop = backdrop,
                modifier = modifier
                    .align(alignment)
                    .offset(x = offset.x, y = offset.y)
                    .sizeIn(minWidth = 180.dp, maxWidth = 360.dp)
                    .heightIn(max = 480.dp)
                    .xNoteOverlayInputBarrier(),
            ) {
                Column(
                    modifier = Modifier.padding(XNoteSpacingSmall),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    content = content,
                )
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
    alignment: Alignment = Alignment.TopEnd,
    offset: DpOffset = DpOffset.Zero,
) {
    XNotePopup(
        visible = expanded,
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        modifier = modifier,
        alignment = alignment,
        offset = offset,
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
                    .padding(horizontal = XNoteSpacingMedium, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = foreground.copy(alpha = if (item.enabled) 1f else 0.48f),
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
                        height = XNoteMinimumTouchTarget,
                        contentPadding = PaddingValues(horizontal = XNoteSpacingSmall),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (data.visuals.withDismissAction) {
                    LiquidButton(
                        onClick = data::dismiss,
                        backdrop = backdrop,
                        height = XNoteMinimumTouchTarget,
                        contentPadding = PaddingValues(horizontal = XNoteSpacingSmall),
                    ) {
                        Text(
                            text = dismissLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        XNoteDismissLayer(
            onDismissRequest = onDismissRequest,
            scrimColor = Color.Black.copy(alpha = 0.32f),
        )
        content()
    }
}

@Composable
private fun BoxScope.XNoteDismissLayer(
    onDismissRequest: () -> Unit,
    scrimColor: Color = Color.Transparent,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor)
            .clearAndSetSemantics { }
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
) {
    val foreground = if (action.destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    LiquidButton(
        onClick = action.onClick,
        backdrop = backdrop,
        enabled = action.enabled,
        tint = foreground.copy(alpha = 0.14f),
        height = XNoteMinimumTouchTarget,
        contentPadding = PaddingValues(horizontal = XNoteSpacingMedium),
    ) {
        Text(
            text = action.label,
            style = MaterialTheme.typography.titleMedium,
            color = foreground,
        )
    }
}

// -- Functions

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
