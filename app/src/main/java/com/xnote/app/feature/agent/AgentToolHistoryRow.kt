package com.xnote.app.feature.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.db.AgentToolEventEntity
import com.xnote.app.data.db.NoteEntity
import com.xnote.app.domain.agent.AgentToolStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// -- Functions

@Composable
internal fun AgentToolHistoryRow(
    event: AgentToolEventEntity,
    notes: List<NoteEntity>,
    notebookTitles: Map<String, String>,
    onPreview: () -> Unit,
    onOpenReviews: (String) -> Unit,
) {
    // -- Derived Values

    val noteId = if (event.name in setOf("create", "delete") && event.status == AgentToolStatus.Committed)
        event.resultJson?.let { Json.parseToJsonElement(it).jsonObject["note_id"]?.jsonPrimitive?.content } else null
    val note = notes.find { it.id == noteId }
    val target = agentToolTarget(event.name, Json.parseToJsonElement(event.argumentsJson).jsonObject,
        notes.associate { it.id to it.title }, notebookTitles)
    val statusColor = when (event.status) {
        AgentToolStatus.Failed, AgentToolStatus.Unknown -> MaterialTheme.colorScheme.error
        AgentToolStatus.Requested, AgentToolStatus.Approved, AgentToolStatus.Executing -> MaterialTheme.colorScheme.primary
        AgentToolStatus.Committed, AgentToolStatus.Denied -> MaterialTheme.colorScheme.onSurfaceVariant
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

    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLow).testTag("agent-tool-${event.id}")) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onPreview).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).background(statusColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                if (event.status == AgentToolStatus.Executing) CircularProgressIndicator(Modifier.size(16.dp), color = statusColor, strokeWidth = 2.dp)
                else Icon(painterResource(if (event.status == AgentToolStatus.Committed) R.drawable.ic_keyline_stroke_check else operationIcon),
                    null, Modifier.size(18.dp), tint = statusColor)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(agentToolTitle(event.name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                if (target.isNotBlank()) Text(target, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(event.status.toolStatusLabel(), style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
            Icon(painterResource(R.drawable.ic_keyline_stroke_chevron_right), "操作详情", Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (noteId != null) {
            Text(when {
                note == null -> "笔记已永久删除 · 无法恢复"
                event.name == "create" -> "已新建 · ${note.title.ifBlank { "未命名笔记" }}"
                note.deletedAtEpochMs != null -> "已移入回收站 · ${note.title.ifBlank { "未命名笔记" }}"
                else -> "笔记已恢复 · ${note.title.ifBlank { "未命名笔记" }}"
            }, Modifier.padding(horizontal = 12.dp).testTag("agent-note-receipt-$noteId"), style = MaterialTheme.typography.bodySmall)
            TextButton({ onOpenReviews(noteId) }, Modifier.padding(horizontal = 4.dp).testTag("agent-review-receipt-$noteId")) { Text("查看单篇改动") }
        }
    }
}
