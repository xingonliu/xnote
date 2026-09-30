package com.xnote.app.design.liquidglass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.xnote.app.design.XNoteButtonContentSpacing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.xnote.app.design.LocalXNoteInteractionSettings
import com.xnote.app.design.XNoteButtonHorizontalPadding
import com.xnote.app.design.XNoteButtonSize

// Copied from AndroidLiquidGlass catalog commit 65ab177 under Apache-2.0.
// Geometry uses XNote's compact 40 dp height and 8 dp horizontal padding.
// Reduced motion still gates interaction feedback.

// -- Composables

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    LiquidButtonSurface(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        isInteractive = isInteractive,
        enabled = enabled,
        tint = tint,
        surfaceColor = surfaceColor,
        horizontalPadding = XNoteButtonHorizontalPadding,
        contentSpacing = XNoteButtonContentSpacing,
        content = content,
    )
}

@Composable
fun LiquidButton(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    LiquidButtonSurface(
        onClick = null,
        backdrop = backdrop,
        modifier = modifier,
        isInteractive = true,
        enabled = true,
        tint = Color.Unspecified,
        surfaceColor = Color.Unspecified,
        horizontalPadding = 0.dp,
        contentSpacing = 0.dp,
        content = content,
    )
}

// A group shares the button's material and motion; its children own click semantics.
@Composable
private fun LiquidButtonSurface(
    onClick: (() -> Unit)?,
    backdrop: Backdrop,
    modifier: Modifier,
    isInteractive: Boolean,
    enabled: Boolean,
    tint: Color,
    surfaceColor: Color,
    horizontalPadding: Dp,
    contentSpacing: Dp,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSettings = LocalXNoteInteractionSettings.current
    val contrastSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val interaction = rememberXNoteButtonInteraction(enabled && isInteractive)

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                layerBlock = interaction.layerBlock,
                onDrawSurface = {
                    if (interactionSettings.highContrast) drawRect(contrastSurface)
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = if (interactionSettings.highContrast) 1f else 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .then(interaction.modifier)
            .heightIn(min = XNoteButtonSize)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(contentSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = {
            CompositionLocalProvider(
                LocalContentColor provides if (tint.isSpecified) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) { content() }
        },
    )
}
