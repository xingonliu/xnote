package com.xnote.app.design.liquidglass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

// -- Functions

@Composable
fun LiquidBottomAction(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // -- State and Variables

    val interaction = rememberXNoteButtonInteraction(enabled = true)

    // -- Derived Values

    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f)
        else Color(0xFF121212).copy(alpha = 0.4f)

    Box(
        modifier.size(56.dp).drawBackdrop(
            backdrop = backdrop,
            shape = { Capsule() },
            effects = {
                vibrancy()
                blur(8.dp.toPx())
                lens(24.dp.toPx(), 24.dp.toPx())
            },
            layerBlock = interaction.layerBlock,
            onDrawSurface = { drawRect(containerColor) },
        ).clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .then(interaction.modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
