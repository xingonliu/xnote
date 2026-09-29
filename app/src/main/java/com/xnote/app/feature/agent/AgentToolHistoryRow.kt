package com.xnote.app.feature.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
    onPreview: () -> Unit,
    onOpenReviews: (String) -> Unit,
) {
    // -- Derived Values

    val noteId = if (event.name in setOf("create", "delete") && event.status == AgentToolStatus.Committed)
        event.resultJson?.let { Json.parseToJsonElement(it).jsonObject["note_id"]?.jsonPrimitive?.content } else null
    val note = notes.find { it.id == noteId }

    Column(Modifier.fillMaxWidth().testTag("agent-tool-${event.id}")) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onPreview).padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (event.status == AgentToolStatus.Executing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(painterResource(R.drawable.ic_keyline_stroke_file_text), null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${event.name} · ${event.status.toolStatusLabel()}", Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(painterResource(R.drawable.ic_keyline_stroke_chevron_down), "工具调用详情", Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (noteId != null) {
            Text(when {
                note == null -> "笔记已永久删除 · 无法恢复"
                event.name == "create" -> "已新建 · ${note.title.ifBlank { "未命名笔记" }}"
                note.deletedAtEpochMs != null -> "已移入回收站 · ${note.title.ifBlank { "未命名笔记" }}"
                else -> "笔记已恢复 · ${note.title.ifBlank { "未命名笔记" }}"
            }, Modifier.padding(horizontal = 8.dp).testTag("agent-note-receipt-$noteId"), style = MaterialTheme.typography.bodySmall)
            TextButton({ onOpenReviews(noteId) }, Modifier.testTag("agent-review-receipt-$noteId")) { Text("查看单篇改动") }
        }
    }
}
