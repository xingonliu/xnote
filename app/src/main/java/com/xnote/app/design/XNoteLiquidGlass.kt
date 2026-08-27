package com.xnote.app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

// -- Functions

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

private fun DrawScope.drawGlassSurface(color: Color) {
    drawRect(color)
}
