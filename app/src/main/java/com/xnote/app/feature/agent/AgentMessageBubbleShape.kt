package com.xnote.app.feature.agent

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// -- Type Definitions

internal class AgentMessageBubbleShape(private val outgoing: Boolean, private val tail: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // -- Derived Values

        val tailWidth = with(density) { if (tail) AgentMessageTailWidth.toPx() else 0f }
        val tailHeight = with(density) { if (tail) AgentMessageTailHeight.toPx() else 0f }
        val right = size.width - tailWidth
        val bottom = size.height - tailHeight
        val radius = minOf(with(density) { BubbleRadius.toPx() }, right / 2f, bottom / 2f)
        val control = radius * CircleControlRatio
        val tailOnRight = outgoing == (layoutDirection == LayoutDirection.Ltr)

        // -- Functions

        fun x(value: Float) = if (tailOnRight) value else size.width - value

        return Outline.Generic(Path().apply {
            moveTo(x(radius), 0f)
            lineTo(x(right - radius), 0f)
            cubicTo(x(right - radius + control), 0f, x(right), radius - control, x(right), radius)
            lineTo(x(right), bottom - radius)
            if (tail) {
                // The outer sweep and inner scoop share the body outline; the tip drops below the body.
                cubicTo(x(right), bottom - radius * 0.35f, x(right + tailWidth * 0.2f), bottom,
                    x(size.width), size.height)
                cubicTo(x(right - radius * 0.15f), size.height - tailHeight * 0.15f,
                    x(right - radius * 0.4f), bottom + tailHeight * 0.15f, x(right - radius * 0.5f), bottom - radius * 0.1f)
                cubicTo(x(right - radius * 0.65f), bottom, x(right - radius * 0.8f), bottom, x(right - radius), bottom)
            } else {
                cubicTo(x(right), bottom - radius + control, x(right - radius + control), bottom, x(right - radius), bottom)
            }
            lineTo(x(radius), bottom)
            cubicTo(x(radius - control), bottom, x(0f), bottom - radius + control, x(0f), bottom - radius)
            lineTo(x(0f), radius)
            cubicTo(x(0f), radius - control, x(radius - control), 0f, x(radius), 0f)
            close()
        })
    }
}

// -- Constants

internal val AgentMessageTailWidth = 6.dp
internal val AgentMessageTailHeight = 4.dp
private val BubbleRadius = 20.dp
private const val CircleControlRatio = 0.55228475f
