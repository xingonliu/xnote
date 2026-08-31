package com.xnote.app.design.liquidglass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule

// Adapted from AndroidLiquidGlass catalog commit 65ab177 under Apache-2.0.

// -- Type Definitions

internal data class LiquidBottomTabTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

internal enum class LiquidBottomTabLayer {
    Base,
    Highlight,
}

// -- State

internal val LocalLiquidBottomTabTransform = staticCompositionLocalOf<(Int) -> LiquidBottomTabTransform> {
    { LiquidBottomTabTransform() }
}
internal val LocalLiquidBottomTabClick = staticCompositionLocalOf<(Int) -> Unit> {
    { }
}
internal val LocalLiquidBottomTabLayer = staticCompositionLocalOf {
    LiquidBottomTabLayer.Base
}

// -- Composables

@Composable
fun RowScope.LiquidBottomTab(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layer = LocalLiquidBottomTabLayer.current
    val transform = LocalLiquidBottomTabTransform.current(index)
    val onClick = LocalLiquidBottomTabClick.current
    val interactionModifier = if (layer == LiquidBottomTabLayer.Base) {
        Modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = { onClick(index) },
            )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .then(interactionModifier)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                scaleX = transform.scaleX
                scaleY = transform.scaleY
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
