package com.xnote.app.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.R
import com.xnote.app.design.liquidglass.LiquidButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// -- Type Definitions

class XNoteToastState internal constructor(private val scope: CoroutineScope) {
    // -- State and Variables

    internal val hostState = SnackbarHostState()

    // -- Functions

    fun show(message: String) {
        // Toast delivery belongs to the app, even when its originating page is removed.
        scope.launch { hostState.showSnackbar(message) }
    }
}

// -- Constants

val LocalXNoteToast = staticCompositionLocalOf<XNoteToastState> {
    error("XNoteToastProvider must wrap the application.")
}

// -- Functions

@Composable
fun XNoteToastProvider(bottomInset: Dp = 0.dp, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val toast = remember(scope) { XNoteToastState(scope) }
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalXNoteToast provides toast) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content() }
            XNoteToastHost(
                hostState = toast.hostState,
                backdrop = backdrop,
                dismissLabel = stringResource(R.string.toast_dismiss),
                modifier = Modifier.align(Alignment.BottomCenter).imePadding().padding(bottom = bottomInset),
            )
        }
    }
}

@Composable
private fun XNoteToastHost(
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
