package com.xnote.app.design.liquidglass

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.kyant.backdrop.Backdrop

// -- Composables

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        modifier = modifier
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.64f
                // Keep the glass shadow outside the button's layout bounds.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .semantics {
                if (!enabled) disabled()
            },
        isInteractive = enabled,
        tint = tint,
        surfaceColor = surfaceColor,
        content = content,
    )
}
