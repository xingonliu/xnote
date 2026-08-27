package com.xnote.app.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.xnote.app.R
import com.xnote.app.design.liquidglass.LiquidButton

// -- Type Definitions

data class XNoteHeaderAction(
    @param:DrawableRes val iconRes: Int,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val tint: Color? = null,
)

// -- Composables

@Composable
fun XNotePageScaffold(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    scrollEdgeState: XNoteScrollEdgeState = XNoteScrollEdgeState(),
    scrollEdges: Set<XNoteScrollEdge> = setOf(XNoteScrollEdge.Top),
    topScrollEdgeStyle: XNoteScrollEdgeStyle = XNoteScrollEdgeStyle.Soft,
    bottomScrollEdgeStyle: XNoteScrollEdgeStyle = XNoteScrollEdgeStyle.Soft,
    topOverlayHeight: Dp = XNoteHeaderHeight,
    bottomOverlayHeight: Dp = 0.dp,
    toastHostState: SnackbarHostState? = null,
    pageState: XNotePageState = XNotePageState.Content,
    onPageStateAction: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .layerBackdrop(backdrop),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                ),
        ) {
            when (val currentState = pageState) {
                XNotePageState.Content -> content()
                is XNotePageState.Loading -> XNoteLoadingState(
                    backdrop = backdrop,
                    message = currentState.message,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(XNoteSpacingMedium)
                        .widthIn(max = 560.dp),
                )
                is XNotePageState.Error -> XNoteErrorState(
                    title = currentState.title,
                    description = currentState.description,
                    actionLabel = currentState.actionLabel,
                    onAction = onPageStateAction,
                    backdrop = backdrop,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(XNoteSpacingMedium)
                        .widthIn(max = 560.dp),
                )
            }
        }

        XNoteScrollEdgeEffect(
            backdrop = backdrop,
            state = scrollEdgeState,
            edges = scrollEdges,
            topStyle = topScrollEdgeStyle,
            bottomStyle = bottomScrollEdgeStyle,
            topInset = statusBarHeight + topOverlayHeight,
            bottomInset = navigationBarHeight + bottomOverlayHeight,
        )

        overlay()

        if (toastHostState != null) {
            XNoteToastHost(
                hostState = toastHostState,
                backdrop = backdrop,
                dismissLabel = stringResource(R.string.toast_dismiss),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomOverlayHeight),
            )
        }
    }
}

@Composable
fun XNoteHeader(
    title: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: List<XNoteHeaderAction> = emptyList(),
    horizontalPadding: Dp = XNoteSpacingMedium,
) {
    require(actions.size <= 2) { "XNoteHeader supports at most two actions." }
    val titlePadding = if (actions.size == 2) 112.dp else 64.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
            )
            .padding(horizontal = horizontalPadding)
            .height(XNoteHeaderHeight),
    ) {
        if (onBack != null) {
            LiquidButton(
                onClick = onBack,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(XNoteMinimumTouchTarget),
                height = XNoteMinimumTouchTarget,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_arrow_left),
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(XNoteMinimumTouchTarget),
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = titlePadding),
        )

        if (actions.isEmpty()) {
            Spacer(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(XNoteMinimumTouchTarget),
            )
        } else {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    LiquidButton(
                        onClick = action.onClick,
                        backdrop = backdrop,
                        enabled = action.enabled,
                        modifier = Modifier.size(XNoteMinimumTouchTarget),
                        height = XNoteMinimumTouchTarget,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            painter = painterResource(action.iconRes),
                            contentDescription = action.contentDescription,
                            tint = action.tint ?: MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
