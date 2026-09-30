package com.xnote.app.feature.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.data.agent.AgentConversation
import com.xnote.app.design.*

// -- Functions

@Composable
internal fun BoxScope.AgentHistoryPopup(
    visible: Boolean,
    conversations: List<AgentConversation>,
    selectedId: String?,
    anchor: XNotePopupAnchor,
    backdrop: Backdrop,
    bottomInset: Dp,
    formatTime: (Long) -> String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    XNotePopup(visible, onDismiss, backdrop, anchor = anchor, placement = XNotePopupPlacement.BelowStart,
        bottomInset = bottomInset, modifier = Modifier.testTag("agent-history-popup")) {
        Text("历史会话", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.width(304.dp).verticalScroll(rememberScrollState())) {
            if (conversations.isEmpty()) Text("暂无历史会话", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            conversations.forEach { conversation ->
                Column(Modifier.fillMaxWidth()
                    .background(if (conversation.id == selectedId) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else androidx.compose.ui.graphics.Color.Transparent, XNoteSmoothCornerShape(XNoteRadiusSmall))
                    .clickable(role = Role.Button) { onSelect(conversation.id) }
                    .semantics { selected = conversation.id == selectedId }
                    .testTag("agent-history-${conversation.id}").padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(conversation.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatTime(conversation.updatedAtEpochMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
