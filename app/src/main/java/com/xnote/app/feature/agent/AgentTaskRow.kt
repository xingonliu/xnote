package com.xnote.app.feature.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.domain.agent.agentFinalBubbleTexts

// -- Functions

@Composable
internal fun AgentTaskRow(
    task: AgentTimelineItem.Task,
    onLongPress: (AgentMessageMenu) -> Unit,
    backdrop: Backdrop,
    viewport: () -> Rect,
    content: @Composable (AgentTimelineItem) -> Unit,
) {
    // -- State and Variables

    var expanded by rememberSaveable(task.run.id, task.finished) { mutableStateOf(false) }

    // -- Derived Values

    val ending = task.ending
    val bubbles = remember(ending?.text) { agentFinalBubbleTexts(ending?.text.orEmpty()) }

    Column(Modifier.fillMaxWidth().testTag("agent-task-${task.run.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (task.finished) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "收起任务过程" else "展开任务过程") { expanded = !expanded }
                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                    .testTag("agent-task-toggle-${task.run.id}").padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(agentTaskElapsedText(task.run), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(painterResource(R.drawable.ic_keyline_stroke_chevron_down), null,
                        Modifier.size(16.dp).rotate(if (expanded) 180f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.fillMaxWidth().testTag("agent-task-divider-${task.run.id}"), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (!task.finished || expanded) {
            Column(Modifier.fillMaxWidth().testTag("agent-task-process-${task.run.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                task.process.forEach { item -> key(item.key) { content(item) } }
            }
        }
        if (ending != null) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AgentMessageGroupSpacing)) {
            bubbles.forEachIndexed { index, text ->
                AgentMessageBubble(ending, text, joinsNext = index < bubbles.lastIndex, onLongPress = onLongPress,
                    backdrop = backdrop, viewport = viewport,
                    modifier = Modifier.testTag("agent-final-${task.run.id}-$index"))
            }
        }
    }
}
