package com.xnote.app.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

// -- Type Definitions

enum class XNoteLiquidGlassButtonType {
    Icon,
    Capsule,
    Rect,
    Floating,
}

// -- Functions

@Composable
fun XNoteLiquidGlassButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    type: XNoteLiquidGlassButtonType = XNoteLiquidGlassButtonType.Icon,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = buttonShape(type)
    val surfaceColor = when {
        tint.isSpecified -> tint.copy(alpha = if (selected) 0.48f else 0.28f)
        selected -> XNoteAccentYellow.copy(alpha = 0.42f)
        else -> Color.White.copy(alpha = 0.18f)
    }

    Box(
        modifier = modifier
            .sizeIn(
                minWidth = XNoteMinimumTouchTarget,
                minHeight = XNoteMinimumTouchTarget,
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(
                        refractionHeight = 12.dp.toPx(),
                        refractionAmount = 18.dp.toPx(),
                        chromaticAberration = selected,
                    )
                },
                onDrawSurface = { drawGlassSurface(surfaceColor) },
            )
            .clip(shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.48f),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun XNoteLiquidGlassPanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = XNoteSmoothCornerShape(XNoteRadiusLarge),
    tint: Color = Color.White.copy(alpha = 0.14f),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(12.dp.toPx())
                lens(
                    refractionHeight = 16.dp.toPx(),
                    refractionAmount = 20.dp.toPx(),
                )
            },
            onDrawSurface = { drawGlassSurface(tint) },
        ),
        content = content,
    )
}

private fun buttonShape(type: XNoteLiquidGlassButtonType): Shape = when (type) {
    XNoteLiquidGlassButtonType.Icon,
    XNoteLiquidGlassButtonType.Floating,
    -> Capsule()

    XNoteLiquidGlassButtonType.Capsule -> Capsule()
    XNoteLiquidGlassButtonType.Rect -> XNoteSmoothCornerShape(XNoteRadiusMedium)
}

private fun DrawScope.drawGlassSurface(color: Color) {
    drawRect(color)
}
