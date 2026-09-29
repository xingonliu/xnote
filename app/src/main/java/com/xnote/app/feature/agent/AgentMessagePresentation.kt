package com.xnote.app.feature.agent

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.xnote.app.data.db.AgentMessageEntity
import com.xnote.app.design.*
import com.xnote.app.domain.agent.AgentMessageRole
import kotlinx.coroutines.launch

// -- Type Definitions

internal data class AgentMessageMenu(val message: AgentMessageEntity, val text: String, val anchor: XNotePopupAnchor)

private class MessageBubbleShape(private val outgoing: Boolean, private val tail: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tailWidth = with(density) { 6.dp.toPx() }
        val radius = minOf(with(density) { 18.dp.toPx() }, size.height / 2, (size.width - tailWidth) / 2)
        val right = size.width - tailWidth
        val bottom = size.height
        val control = radius * 0.5522848f
        fun x(value: Float) = if (outgoing) value else size.width - value
        val path = Path().apply {
            moveTo(x(radius), 0f)
            lineTo(x(right - radius), 0f)
            cubicTo(x(right - radius + control), 0f, x(right), radius - control, x(right), radius)
            lineTo(x(right), bottom - radius)
            if (tail) {
                cubicTo(x(right), bottom - tailWidth, x(right), bottom - tailWidth / 2, x(size.width), bottom)
                cubicTo(x(right), bottom, x(right - tailWidth), bottom - tailWidth / 2, x(right - tailWidth), bottom - tailWidth)
                cubicTo(x(right - tailWidth), bottom, x(right - radius), bottom, x(right - radius), bottom)
            } else {
                cubicTo(x(right), bottom - radius + control, x(right - radius + control), bottom, x(right - radius), bottom)
            }
            lineTo(x(radius), bottom)
            cubicTo(x(radius - control), bottom, x(0f), bottom - radius + control, x(0f), bottom - radius)
            lineTo(x(0f), radius)
            cubicTo(x(0f), radius - control, x(radius - control), 0f, x(radius), 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

// -- Functions

@Composable
internal fun AgentMessageBubble(
    message: AgentMessageEntity,
    text: String,
    joinsNext: Boolean,
    onLongPress: (AgentMessageMenu) -> Unit,
) {
    // -- State and Variables

    val anchor = rememberXNotePopupAnchor()

    // -- Derived Values

    val outgoing = message.role == AgentMessageRole.User
    val light = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val shape = remember(outgoing, joinsNext) { MessageBubbleShape(outgoing, !joinsNext) }
    val background = if (outgoing) Color(0xFF007AFF) else if (light) Color(0xFFE9E9EB) else Color(0xFF262628)
    val foreground = if (outgoing || !light) Color.White else Color.Black

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Text(
            text,
            modifier = Modifier
                .align(if (outgoing) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = minOf(maxWidth * 0.8f, 360.dp))
                .xNotePopupAnchor(anchor)
                .clip(shape)
                .background(background)
                .combinedClickable(onClick = {}, onLongClickLabel = "复制消息", onLongClick = {
                    onLongPress(AgentMessageMenu(message, text, anchor))
                })
                .padding(start = if (outgoing) 12.dp else 18.dp, end = if (outgoing) 18.dp else 12.dp, top = 8.dp, bottom = 8.dp)
                .testTag("agent-message-${message.id}"),
            color = foreground,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp),
        )
    }
}

@Composable
internal fun BoxScope.AgentMessageContextMenu(
    menu: AgentMessageMenu?,
    timestamp: String,
    backdrop: Backdrop,
    bottomInset: Dp,
    onDismiss: () -> Unit,
) {
    // -- State and Variables

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    XNotePopup(
        visible = menu != null, onDismissRequest = onDismiss, backdrop = backdrop,
        anchor = menu?.anchor, bottomInset = bottomInset,
        placement = if (menu?.message?.role == AgentMessageRole.User) XNotePopupPlacement.AboveEnd else XNotePopupPlacement.AboveStart,
        modifier = Modifier.testTag("agent-message-menu"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ menu?.let { selected -> scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("消息", selected.text)))
                onDismiss()
            } } }, Modifier.testTag("agent-copy-message")) { Text("复制") }
            Text(timestamp, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
