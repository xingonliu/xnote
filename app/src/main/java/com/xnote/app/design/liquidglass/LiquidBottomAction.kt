package com.xnote.app.design.liquidglass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import com.kyant.backdrop.Backdrop
import com.xnote.app.design.LocalXNoteInteractionSettings

// -- Functions

@Composable
fun LiquidBottomAction(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // -- State and Variables

    val interactionSettings = LocalXNoteInteractionSettings.current
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            surfaceAlpha = LiquidBottomNavigationSurfaceAlpha,
            fallbackSurfaceAlpha = LiquidBottomNavigationFallbackSurfaceAlpha,
            position = { size, _ -> Offset(size.width / 2f, size.height / 2f) },
        )
    }

    // -- Derived Values

    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val containerColor = liquidBottomNavigationContainerColor(isLightTheme)
    val hasMotion = !interactionSettings.reduceMotion
    val interaction = remember(highlight, hasMotion) {
        XNoteButtonInteraction(enabled = hasMotion, highlight = highlight)
    }

    Box(
        modifier
            .size(LiquidBottomNavigationHeight)
            .liquidBottomNavigationSurface(
                backdrop = backdrop,
                containerColor = containerColor,
                layerBlock = interaction.layerBlock,
            )
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .then(interaction.modifier)
            .padding(LiquidBottomNavigationPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
