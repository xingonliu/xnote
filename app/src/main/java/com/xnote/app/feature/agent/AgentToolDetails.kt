package com.xnote.app.feature.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnote.app.data.db.AgentToolEventEntity
import com.xnote.app.data.db.NoteEntity
import com.xnote.app.design.XNoteButton
import com.xnote.app.domain.agent.AgentToolDecision
import com.xnote.app.domain.agent.AgentToolStatus
import kotlinx.serialization.json.*
import java.text.DateFormat
import java.util.Date

// -- Functions

@Composable
internal fun AgentToolDetails(
    event: AgentToolEventEntity,
    notes: List<NoteEntity>,
    noteTitles: Map<String, String>,
    notebookTitles: Map<String, String>,
    onOpenReviews: (String) -> Unit,
    onContinue: (() -> Unit)?,
    onStop: (() -> Unit)?,
) {
    // -- State and Variables

    var rawExpanded by rememberSaveable(event.id) { mutableStateOf(false) }

    // -- Derived Values

    val arguments = remember(event.argumentsJson, noteTitles, notebookTitles) {
        agentToolPayload(event.argumentsJson, noteTitles, notebookTitles)
    }
    val result = remember(event.resultJson, noteTitles, notebookTitles) {
        event.resultJson?.let { agentToolPayload(it, noteTitles, notebookTitles) }
    }
    val decisions = remember(event.decisionsJson) {
        runCatching { Json.decodeFromString<List<AgentToolDecision>>(event.decisionsJson) }.getOrDefault(emptyList())
    }
    val noteId = if (event.status == AgentToolStatus.Committed && event.name in setOf("create", "write", "delete"))
        (event.resultJson?.let(::agentToolJsonObject)?.get("note_id") as? JsonPrimitive)?.contentOrNull else null
    val note = notes.find { it.id == noteId }
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .padding(16.dp).testTag("agent-tool-details-${event.id}"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(agentToolTitle(event.name), style = MaterialTheme.typography.titleSmall)
            Text(event.status.toolStatusLabel(), style = MaterialTheme.typography.labelMedium,
                color = if (event.status in setOf(AgentToolStatus.Failed, AgentToolStatus.Denied, AgentToolStatus.Unknown))
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("发起于 ${dateFormat.format(Date(event.createdAtEpochMs))}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            event.committedAtEpochMs?.let {
                Text("结束于 ${dateFormat.format(Date(it))}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AgentToolDetailSection("调用参数") {
            AgentToolPayloadView(arguments, "此操作无需参数。")
        }
        AgentToolDetailSection("执行结果") {
            if (result != null) AgentToolPayloadView(result, "返回结果为空。")
            else Text(when (event.status) {
                AgentToolStatus.Executing -> "正在执行，结果将在这里显示。"
                AgentToolStatus.Requested -> "等待授权或执行，暂未返回结果。"
                AgentToolStatus.Denied -> "本次操作未获允许。"
                AgentToolStatus.Failed -> "本次操作未完成，暂未返回结果。"
                AgentToolStatus.Unknown -> "结果尚未确认。"
                else -> "暂未返回结果。"
            }, style = MaterialTheme.typography.bodyMedium)
            if (noteId != null) {
                Text(when {
                    note == null -> "笔记已永久删除 · 无法恢复"
                    event.name == "delete" && note.deletedAtEpochMs == null -> "笔记已恢复 · ${note.title.ifBlank { "未命名笔记" }}"
                    note.deletedAtEpochMs != null -> "已移入回收站 · ${note.title.ifBlank { "未命名笔记" }}"
                    else -> "${note.title.ifBlank { "未命名笔记" }} · 可查看单篇改动"
                }, Modifier.testTag("agent-note-receipt-$noteId"), style = MaterialTheme.typography.bodyMedium)
                TextButton({ onOpenReviews(noteId) }, Modifier.testTag("agent-review-receipt-$noteId")) { Text("查看单篇改动") }
            }
        }
        if (decisions.isNotEmpty()) AgentToolDetailSection("授权记录") {
            decisions.forEach { decision ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("当时的权限：${decision.permission.mode.permissionLabel()}", style = MaterialTheme.typography.bodyMedium)
                    Text(agentToolDecisionReason(decision.reason), style = MaterialTheme.typography.bodySmall)
                    Text(dateFormat.format(Date(decision.atEpochMs)), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton({ rawExpanded = !rawExpanded }, Modifier.testTag("agent-tool-raw-toggle-${event.id}")) {
            Text(if (rawExpanded) "收起原始数据" else "查看原始数据")
        }
        if (rawExpanded) AgentToolDetailSection("原始数据") {
            Text("请求参数", style = MaterialTheme.typography.labelMedium)
            SelectionContainer { Text(arguments.rawJson, Modifier.testTag("agent-tool-arguments"),
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            result?.let {
                Text("返回结果", style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(it.rawJson, Modifier.testTag("agent-tool-result"),
                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
        }
        if (onStop != null || onContinue != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onStop?.let { XNoteButton(it, modifier = Modifier.testTag("agent-tool-stop")) { Text("停止任务") } }
            onContinue?.let { XNoteButton(it, modifier = Modifier.testTag("agent-tool-continue")) { Text("继续任务") } }
        }
    }
}

@Composable
private fun AgentToolDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun AgentToolPayloadView(payload: AgentToolPayload, emptyLabel: String) {
    // -- State and Variables

    var fullContent by rememberSaveable(payload.content) { mutableStateOf(false) }
    var contentOverflows by remember(payload.content) { mutableStateOf(false) }

    SelectionContainer {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            payload.fields.forEach { field ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(field.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(field.value, style = MaterialTheme.typography.bodyMedium)
                }
            }
            payload.items.forEachIndexed { index, item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${index + 1}. ${item.label}", style = MaterialTheme.typography.labelLarge)
                    if (item.value.isNotBlank()) Text(item.value, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (payload.fields.isEmpty() && payload.items.isEmpty() && payload.content == null) {
                Text(emptyLabel, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    payload.content?.let { content ->
        SelectionContainer {
            Text(content.ifBlank { "暂无正文" }, style = MaterialTheme.typography.bodyMedium,
                maxLines = if (fullContent) Int.MAX_VALUE else 8, overflow = TextOverflow.Ellipsis,
                onTextLayout = { contentOverflows = it.hasVisualOverflow })
        }
        if (contentOverflows || fullContent) {
            TextButton({ fullContent = !fullContent }) { Text(if (fullContent) "收起正文" else "展开全文") }
        }
    }
}
