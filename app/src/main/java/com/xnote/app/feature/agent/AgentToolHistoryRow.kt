package com.xnote.app.feature.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.db.AgentToolEventEntity
import com.xnote.app.data.db.NoteEntity
import com.xnote.app.domain.agent.AgentToolStatus

// -- Functions

@Composable
internal fun AgentToolHistoryRow(
    event: AgentToolEventEntity,
    notes: List<NoteEntity>,
    notebookTitles: Map<String, String>,
    onOpenReviews: (String) -> Unit,
    onContinue: (() -> Unit)?,
    onStop: (() -> Unit)?,
) {
    // -- State and Variables

    var expanded by rememberSaveable(event.id) { mutableStateOf(false) }

    // -- Derived Values

    val noteTitles = notes.associate { it.id to it.title }
    val arguments = remember(event.argumentsJson) { agentToolJsonObject(event.argumentsJson) }
    val target = agentToolTarget(event.name, arguments, noteTitles, notebookTitles)
    val statusColor = when (event.status) {
        AgentToolStatus.Failed, AgentToolStatus.Unknown, AgentToolStatus.Denied -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val operationIcon = when (event.name) {
        "note_search", "memory_search" -> R.drawable.ic_keyline_stroke_search
        "create" -> R.drawable.ic_keyline_stroke_square_pen
        "write" -> R.drawable.ic_keyline_stroke_pen_line
        "delete" -> R.drawable.ic_keyline_stroke_bin
        "memory_remember" -> R.drawable.ic_keyline_stroke_user
        "output_file" -> R.drawable.ic_keyline_stroke_arrow_down
        else -> R.drawable.ic_keyline_stroke_file_text
    }

    Column(Modifier.fillMaxWidth().testTag("agent-tool-${event.id}"),
        horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Row(Modifier.widthIn(max = minOf(maxWidth * 0.8f, 360.dp))
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(role = Role.Button, onClickLabel = if (expanded) "收起详情" else "展开详情") { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                .testTag("agent-tool-toggle-${event.id}")
                .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (event.status == AgentToolStatus.Executing) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = statusColor, strokeWidth = 1.5.dp)
                } else Icon(painterResource(operationIcon), null, Modifier.size(16.dp), tint = statusColor)
                Text(agentToolSummary(event.name, target, event.status), Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium, color = statusColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(painterResource(R.drawable.ic_keyline_stroke_chevron_down), null,
                    Modifier.size(16.dp).rotate(if (expanded) 180f else 0f), tint = statusColor)
            }
        }
        if (expanded) AgentToolDetails(event, notes, noteTitles, notebookTitles, onOpenReviews, onContinue, onStop)
    }
}
