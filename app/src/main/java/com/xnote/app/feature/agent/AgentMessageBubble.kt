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

        val width = size.width
        val height = size.height
        val tailWidth = with(density) { if (tail) TailWidth.toPx() else 0f }
        val rTarget = with(density) { BubbleRadius.toPx() }
        val r = minOf(rTarget, height / 2f / SquircleSmoothLeadRatio, (width - tailWidth) / 2f / SquircleSmoothLeadRatio)
        val d = SquircleSmoothLeadRatio * r
        val rightWall = width - tailWidth
        val bottom = height

        val tailOnRight = outgoing == (layoutDirection == LayoutDirection.Ltr)

        // -- Functions

        fun x(value: Float) = if (tailOnRight) value else width - value

        return Outline.Generic(Path().apply {
            if (tail) {
                val scoopStart = rightWall - with(density) { 18.dp.toPx() }
                moveTo(x(d), bottom)
                lineTo(x(scoopStart), bottom)
                // Inner scoop into tail
                cubicTo(
                    x(scoopStart + with(density) { 6.dp.toPx() }), bottom,
                    x(rightWall - with(density) { 6.dp.toPx() }), bottom - with(density) { 1.5.dp.toPx() },
                    x(width - with(density) { 2.dp.toPx() }), bottom - with(density) { 0.5.dp.toPx() },
                )
                // Soft tip
                cubicTo(
                    x(width), bottom - with(density) { 0.2.dp.toPx() },
                    x(width), bottom,
                    x(width), bottom,
                )
                // Outer sweep up to right wall
                cubicTo(
                    x(width - with(density) { 0.5.dp.toPx() }), bottom - with(density) { 3.dp.toPx() },
                    x(rightWall), bottom - with(density) { 8.dp.toPx() },
                    x(rightWall), bottom - with(density) { 12.dp.toPx() },
                )
            } else {
                moveTo(x(d), bottom)
                lineTo(x(rightWall - d), bottom)
                // Bottom-right continuous squircle corner
                cubicTo(
                    x(rightWall - d + 0.4401f * r), bottom,
                    x(rightWall - d + 0.6602f * r), bottom - 0.0463f * r,
                    x(rightWall - d + 0.8971f * r), bottom - 0.1316f * r,
                )
                cubicTo(
                    x(rightWall - d + 1.1340f * r), bottom - 0.2169f * r,
                    x(rightWall - 0.2169f * r), bottom - d + 1.1340f * r,
                    x(rightWall - 0.1316f * r), bottom - d + 0.8971f * r,
                )
                cubicTo(
                    x(rightWall - 0.0463f * r), bottom - d + 0.6602f * r,
                    x(rightWall), bottom - d + 0.4401f * r,
                    x(rightWall), bottom - d,
                )
            }

            // Right wall up to top-right corner
            lineTo(x(rightWall), d)

            // Top-right continuous squircle corner
            cubicTo(
                x(rightWall), d - 0.4401f * r,
                x(rightWall - 0.0463f * r), d - 0.6602f * r,
                x(rightWall - 0.1316f * r), d - 0.8971f * r,
            )
            cubicTo(
                x(rightWall - 0.2169f * r), d - 1.1340f * r,
                x(rightWall - d + 1.1340f * r), 0.2169f * r,
                x(rightWall - d + 0.8971f * r), 0.1316f * r,
            )
            cubicTo(
                x(rightWall - d + 0.6602f * r), 0.0463f * r,
                x(rightWall - d + 0.4401f * r), 0f,
                x(rightWall - d), 0f,
            )

            // Top wall
            lineTo(x(d), 0f)

            // Top-left continuous squircle corner
            cubicTo(
                x(d - 0.4401f * r), 0f,
                x(d - 0.6602f * r), 0.0463f * r,
                x(d - 0.8971f * r), 0.1316f * r,
            )
            cubicTo(
                x(d - 1.1340f * r), 0.2169f * r,
                x(0.2169f * r), d - 1.1340f * r,
                x(0.1316f * r), d - 0.8971f * r,
            )
            cubicTo(
                x(0.0463f * r), d - 0.6602f * r,
                x(0f), d - 0.4401f * r,
                x(0f), d,
            )

            // Left wall
            lineTo(x(0f), bottom - d)

            // Bottom-left continuous squircle corner
            cubicTo(
                x(0f), bottom - d + 0.4401f * r,
                x(0.0463f * r), bottom - d + 0.6602f * r,
                x(0.1316f * r), bottom - d + 0.8971f * r,
            )
            cubicTo(
                x(0.2169f * r), bottom - d + 1.1340f * r,
                x(d - 1.1340f * r), bottom - 0.2169f * r,
                x(d - 0.8971f * r), bottom - 0.1316f * r,
            )
            cubicTo(
                x(d - 0.6602f * r), bottom - 0.0463f * r,
                x(d - 0.4401f * r), bottom,
                x(d), bottom,
            )

            close()
        })
    }
}

// -- Constants

private val BubbleRadius = 18.dp
private val TailWidth = 4.dp
private const val SquircleSmoothLeadRatio = 1.528665f

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
    val hasTail = !joinsNext
    val shape = remember(outgoing, hasTail) { MessageBubbleShape(outgoing, hasTail) }
    val colors = when {
        highContrast -> when {
            outgoing -> List(2) { Color(0xFF0066D9) }
            light -> List(2) { Color(0xFFE5E5EA) }
            else -> List(2) { Color(0xFF262628) }
        }
        outgoing -> listOf(Color(0xFF2997FF).copy(alpha = 0.92f), Color(0xFF007AFF).copy(alpha = 0.95f))
        light -> listOf(Color(0xFFF0F0F2).copy(alpha = 0.92f), Color(0xFFE5E5EA).copy(alpha = 0.94f))
        else -> listOf(Color(0xFF3A3A3C).copy(alpha = 0.84f), Color(0xFF2C2C2E).copy(alpha = 0.88f))
    }
    val tint = remember(colors) { Brush.verticalGradient(colors) }
    val contentColor = when {
        outgoing -> Color.White
        highContrast -> if (light) Color.Black else Color.White
        light -> Color(0xFF1C1C1E)
        else -> Color(0xFFFAFAFC)
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        Text(
            text,
            modifier = Modifier
                .align(if (outgoing) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(
                    start = if (!outgoing && joinsNext) TailWidth else 0.dp,
                    end = if (outgoing && joinsNext) TailWidth else 0.dp,
                )
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
                        if (!outgoing && !light && !highContrast) {
                            drawOutline(outline, Color.White.copy(alpha = 0.12f), style = Stroke(0.5.dp.toPx()))
                        }
                    }
                }
                .combinedClickable(onClick = {}, onLongClickLabel = "复制消息", onLongClick = {
                    onLongPress(AgentMessageMenu(message, text, anchor))
                })
                .padding(
                    start = 14.dp + if (!outgoing && hasTail) TailWidth else 0.dp,
                    end = 14.dp + if (outgoing && hasTail) TailWidth else 0.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                )
                .testTag("agent-message-${message.id}"),
            color = contentColor,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
            ),
        )
    }
}
