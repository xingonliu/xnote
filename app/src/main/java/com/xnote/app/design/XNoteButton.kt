package com.xnote.app.design

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.xnote.app.design.liquidglass.rememberXNoteButtonInteraction

// -- Type Definitions

private object XNoteButtonShape : Shape {
    // -- State and Variables

    private val rectangle = XNoteSmoothCornerShape(XNoteButtonRadius)

    // -- Functions

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        (if (size.width == size.height) CircleShape else rectangle)
            .createOutline(size, layoutDirection, density)
}

// -- Functions

@Composable
fun XNoteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    // -- State and Variables

    val settings = LocalXNoteInteractionSettings.current
    val interaction = rememberXNoteButtonInteraction(enabled, softGlow = true)
    val colors = MaterialTheme.colorScheme

    // -- Derived Values

    val background = if (tint.isSpecified) tint else {
        colors.onSurface.copy(alpha = 0.06f).compositeOver(colors.surface)
    }
    val foreground = if (tint.isSpecified) contentColorFor(tint) else colors.onSurface

    Row(
        modifier = modifier
            .graphicsLayer {
                interaction.layerBlock?.invoke(this)
                alpha = if (enabled) 1f else 0.64f
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .clip(XNoteButtonShape)
            .background(background, XNoteButtonShape)
            .then(
                if (settings.highContrast) Modifier.border(1.dp, colors.outline, XNoteButtonShape)
                else Modifier,
            )
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = if (interaction.enabled) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .then(interaction.modifier)
            .height(XNoteButtonSize)
            .padding(horizontal = XNoteButtonHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(XNoteButtonContentSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground) { content() }
    }
}
