package com.xnote.app.feature.agent

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.xnote.app.data.db.AgentMessageEntity
import com.xnote.app.design.LocalXNoteInteractionSettings
import com.xnote.app.design.rememberXNotePopupAnchor
import com.xnote.app.design.xNotePopupAnchor
import com.xnote.app.domain.agent.AgentMessageRole

// -- Type Definitions

private class MessageBubbleShape(private val outgoing: Boolean, private val tail: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // -- Derived Values

        val tailDrop = with(density) { if (tail) BubbleTailDrop.toPx() else 0f }
        val right = size.width
        val bottom = size.height - tailDrop
        val radius = minOf(with(density) { 20.dp.toPx() }, bottom / 2, right / 2)
        val control = radius * 0.5522848f

        // -- Functions

        fun x(value: Float) = if (outgoing == (layoutDirection == LayoutDirection.Ltr)) value else size.width - value

        return Outline.Generic(Path().apply {
            moveTo(x(radius), 0f)
            lineTo(x(right - radius), 0f)
            cubicTo(x(right - radius + control), 0f, x(right), radius - control, x(right), radius)
            lineTo(x(right), bottom - radius)
            if (tail) {
                // The inward tail continues the rounded edge, then dips below the body with a soft tip.
                cubicTo(x(right), bottom - radius * 0.45f,
                    x(right - radius * 0.58f), bottom - radius * 0.28f, x(right - radius * 0.6f), bottom - radius * 0.04f)
                cubicTo(x(right - radius * 0.62f), bottom + radius * 0.2f,
                    x(right - radius * 0.56f), bottom + tailDrop * 0.6f, x(right - radius * 0.42f), bottom + tailDrop * 0.86f)
                cubicTo(x(right - radius * 0.378f), bottom + tailDrop * 0.938f,
                    x(right - radius * 0.36f), bottom + tailDrop, x(right - radius * 0.44f), bottom + tailDrop * 0.95f)
                cubicTo(x(right - radius * 0.552f), bottom + tailDrop * 0.88f,
                    x(right - radius * 0.95f), bottom, x(right - radius * 1.2f), bottom)
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

private val BubbleTailDrop = 6.dp

// -- Functions

@Composable
internal fun AgentMessageBubble(
    message: AgentMessageEntity,
    text: String,
    joinsNext: Boolean,
    onLongPress: (AgentMessageMenu) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    // -- State and Variables

    val anchor = rememberXNotePopupAnchor()

    // -- Derived Values

    val outgoing = message.role == AgentMessageRole.User
    val light = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val highContrast = LocalXNoteInteractionSettings.current.highContrast
    val shape = remember(outgoing, joinsNext) { MessageBubbleShape(outgoing, !joinsNext) }
    val colors = when {
        highContrast -> List(2) { if (outgoing) Color(0xFF0066D9) else Color(0xFF262628) }
        outgoing -> listOf(Color(0xFF2994EF).copy(alpha = 0.9f), Color(0xFF2484EB).copy(alpha = 0.92f))
        light -> listOf(Color(0xFF42443F).copy(alpha = 0.76f), Color(0xFF31332F).copy(alpha = 0.8f))
        else -> listOf(Color(0xFF4C4E49).copy(alpha = 0.68f), Color(0xFF3D3F3B).copy(alpha = 0.72f))
    }
    val tint = remember(colors) { Brush.verticalGradient(colors) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        Text(
            text,
            modifier = Modifier
                .align(if (outgoing) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = minOf(maxWidth * 0.8f, 360.dp))
                .xNotePopupAnchor(anchor)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = { if (!highContrast) blur(8.dp.toPx()) },
                    highlight = null,
                    shadow = null,
                    onDrawSurface = { drawRect(tint) },
                )
                .drawWithCache {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    onDrawWithContent {
                        drawContent()
                        if (!outgoing && !highContrast) {
                            drawOutline(outline, Color.White.copy(alpha = 0.12f), style = Stroke(0.5.dp.toPx()))
                        }
                    }
                }
                .combinedClickable(onClick = {}, onLongClickLabel = "复制消息", onLongClick = {
                    onLongPress(AgentMessageMenu(message, text, anchor))
                })
                .padding(start = 14.dp, end = 14.dp,
                    top = 8.dp, bottom = 8.dp + if (joinsNext) 0.dp else BubbleTailDrop)
                .testTag("agent-message-${message.id}"),
            color = if (highContrast) Color.White else Color(0xFFFAFAFC),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
            ),
        )
    }
}
