package com.xnote.app.design.liquidglass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

// -- Constants

internal val LiquidBottomNavigationHeight = 64.dp
internal val LiquidBottomNavigationContentHeight = 56.dp
internal val LiquidBottomNavigationPadding = 4.dp
internal const val LiquidBottomNavigationSurfaceAlpha = 0.08f
internal const val LiquidBottomNavigationFallbackSurfaceAlpha = 0.25f

// -- Functions

internal fun liquidBottomNavigationContainerColor(isLightTheme: Boolean): Color =
    if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f)
    else Color(0xFF121212).copy(alpha = 0.4f)

internal fun Modifier.liquidBottomNavigationSurface(
    backdrop: Backdrop,
    containerColor: Color,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier = clip(Capsule()).drawBackdrop(
    backdrop = backdrop,
    shape = { Capsule() },
    effects = {
        vibrancy()
        blur(8.dp.toPx())
        lens(24.dp.toPx(), 24.dp.toPx())
    },
    layerBlock = layerBlock,
    onDrawSurface = { drawRect(containerColor) },
)
