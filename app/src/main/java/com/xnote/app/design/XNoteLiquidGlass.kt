package com.xnote.app.design

import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight

// AndroidLiquidGlass dialog material copied from catalog commit 65ab177.

// -- Composables

@Composable
fun XNoteLiquidGlassPanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = XNoteSmoothCornerShape(XNoteRadiusLarge),
    content: @Composable BoxScope.() -> Unit,
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val highContrast = LocalXNoteInteractionSettings.current.highContrast
    val containerColor = if (highContrast) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    } else if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.6f)
    } else {
        Color(0xFF121212).copy(alpha = 0.4f)
    }

    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(
                    brightness = if (isLightTheme) 0.2f else 0f,
                    saturation = 1.5f,
                )
                blur(if (isLightTheme) 16.dp.toPx() else 8.dp.toPx())
                lens(
                    refractionHeight = 24.dp.toPx(),
                    refractionAmount = 48.dp.toPx(),
                    depthEffect = true,
                )
            },
            highlight = { Highlight.Plain },
            onDrawSurface = { drawRect(containerColor) },
        ),
        content = content,
    )
}
