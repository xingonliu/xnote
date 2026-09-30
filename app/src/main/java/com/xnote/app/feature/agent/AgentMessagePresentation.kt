package com.xnote.app.feature.agent

import android.content.ClipData
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.xnote.app.data.db.AgentMessageEntity
import com.xnote.app.design.*
import com.xnote.app.domain.agent.AgentMessageRole
import kotlinx.coroutines.launch

// -- Type Definitions

internal data class AgentMessageMenu(val message: AgentMessageEntity, val text: String, val anchor: XNotePopupAnchor)

// -- Functions

@Composable
internal fun AgentMessageText(message: AgentMessageEntity, onLongPress: (AgentMessageMenu) -> Unit) {
    // -- State and Variables

    val anchor = rememberXNotePopupAnchor()

    Text(message.text, Modifier.fillMaxWidth().xNotePopupAnchor(anchor)
        .combinedClickable(onClick = {}, onLongClickLabel = "复制消息", onLongClick = {
            onLongPress(AgentMessageMenu(message, message.text, anchor))
        }).testTag("agent-message-${message.id}"),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp), color = MaterialTheme.colorScheme.onSurface)
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
