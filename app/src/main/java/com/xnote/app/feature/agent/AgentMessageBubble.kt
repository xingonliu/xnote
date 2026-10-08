package com.xnote.app.feature.agent

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

// -- Constants

internal val AgentMessageGroupSpacing = 3.dp
internal val AgentMessageSpacing = 10.dp
private val AgentMessageFontSize = 14.sp
private val OutgoingBlue = listOf(Color(0xFF32A5FF), Color(0xFF087AFF))
private val HighContrastBlue = Color(0xFF0066D9)

// -- Functions

@Composable
internal fun AgentMessageBubble(
    message: AgentMessageEntity,
    text: String,
    joinsNext: Boolean,
    onLongPress: (AgentMessageMenu) -> Unit,
    backdrop: Backdrop,
    viewport: () -> Rect,
    modifier: Modifier = Modifier,
) {
    // -- State and Variables

    val anchor = rememberXNotePopupAnchor()
    val topInWindow = remember { mutableFloatStateOf(0f) }

    // -- Derived Values

    val outgoing = message.role == AgentMessageRole.User
    val light = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val highContrast = LocalXNoteInteractionSettings.current.highContrast
    val hasTail = !joinsNext
    val shape = remember(outgoing, hasTail) { AgentMessageBubbleShape(outgoing, hasTail) }
    val incomingTint = when {
        highContrast && light -> Color(0xFFE5E5EA)
        highContrast -> Color(0xFF2C2C2E)
        light -> Color(0xFFD1D1D6).copy(alpha = 0.56f)
        else -> Color(0xFF48484A).copy(alpha = 0.62f)
    }
    val contentColor = if (outgoing || !light) Color.White else Color.Black

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxBubbleWidth = minOf(maxWidth * 0.8f, 360.dp)
        val surface = if (outgoing) {
            Modifier
                .onGloballyPositioned { topInWindow.floatValue = it.positionInWindow().y }
                .drawWithCache {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val bounds = viewport()
                    // All sent bubbles share one viewport gradient, including while scrolling.
                    val top = if (bounds.height > 0f) bounds.top - topInWindow.floatValue else 0f
                    val bottom = if (bounds.height > 0f) bounds.bottom - topInWindow.floatValue else size.height
                    val tint = Brush.verticalGradient(OutgoingBlue, startY = top, endY = bottom)
                    onDrawBehind {
                        if (highContrast) drawOutline(outline, HighContrastBlue)
                        else drawOutline(outline, tint)
                    }
                }
        } else {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = { if (!highContrast) blur(12.dp.toPx()) },
                highlight = null,
                shadow = null,
                onDrawSurface = { drawRect(incomingTint) },
                onDrawFront = {
                    if (!light && !highContrast) {
                        drawOutline(shape.createOutline(size, layoutDirection, this),
                            Color.White.copy(alpha = 0.10f), style = Stroke(0.5.dp.toPx()))
                    }
                },
            )
        }
        Text(
            text,
            modifier = Modifier
                .align(if (outgoing) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(
                    start = if (!outgoing && joinsNext) AgentMessageTailWidth else 0.dp,
                    end = if (outgoing && joinsNext) AgentMessageTailWidth else 0.dp,
                )
                .widthIn(max = maxBubbleWidth - if (joinsNext) AgentMessageTailWidth else 0.dp)
                .xNotePopupAnchor(anchor)
                .then(surface)
                .combinedClickable(onClick = {}, onLongClickLabel = "复制消息", onLongClick = {
                    onLongPress(AgentMessageMenu(message, text, anchor))
                })
                .padding(
                    start = 12.dp + if (!outgoing && hasTail) AgentMessageTailWidth else 0.dp,
                    end = 12.dp + if (outgoing && hasTail) AgentMessageTailWidth else 0.dp,
                    top = 7.dp,
                    bottom = 7.dp + if (hasTail) AgentMessageTailHeight else 0.dp,
                )
                .testTag("agent-message-${message.id}"),
            color = contentColor,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = AgentMessageFontSize, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
            ),
        )
    }
}
