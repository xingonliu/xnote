package com.xnote.app.feature.agent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.data.db.*
import com.xnote.app.design.*
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.decodeNoteDocument
import com.xnote.app.domain.text.extractPlainText
import kotlinx.serialization.json.*

// -- Functions

@Composable
fun AgentAttachNotesDialog(notes: List<NoteEntity>, selected: List<String>, backdrop: Backdrop, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    var selection by remember { mutableStateOf(selected.toSet()) }
    var query by remember { mutableStateOf("") }
    val filtered = notes.filter { it.title.contains(query, ignoreCase = true) }
    XNoteDialog(true, onDismiss, "附加笔记", backdrop,
        confirmAction = XNoteDialogAction("添加 ${selection.size} 篇", { onConfirm(selection.toList()) }, selection.size <= AgentNoteLimits.MaxAttachedNotes),
        dismissAction = XNoteDialogAction("取消", onDismiss)) {
        Text("最多选择 ${AgentNoteLimits.MaxAttachedNotes} 篇，读取内容仍需遵守你选择的权限。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        XNoteTextField(query, { query = it }, placeholder = "搜索笔记标题")
        LazyColumn(Modifier.heightIn(max = 300.dp).testTag("agent-note-picker")) {
            items(filtered, key = { it.id }) { note ->
                val checked = note.id in selection
                Row(Modifier.fillMaxWidth().toggleable(checked,
                    enabled = checked || selection.size < AgentNoteLimits.MaxAttachedNotes, role = Role.Checkbox,
                    onValueChange = { selection = if (it) selection + note.id else selection - note.id })
                    .padding(vertical = 12.dp).testTag("agent-attach-${note.id}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Checkbox(checked, onCheckedChange = null)
                    Text(note.title.ifBlank { "未命名笔记" }, Modifier.weight(1f))
                }
            }
        }
        selection.filter { selectedId -> notes.none { it.id == selectedId } }.forEach { missing ->
            XNoteButton({ selection = selection - missing }) { Text("移除已删除的笔记") }
        }
        if (filtered.isEmpty()) Text(if (query.isBlank()) "暂无可附加的笔记" else "没有找到笔记")
    }
}

@Composable
fun AgentApprovalDialog(event: AgentToolEventEntity, backdrop: Backdrop, noteTitles: Map<String, String>, notebookTitles: Map<String, String>,
    onDismiss: () -> Unit, onAnswer: (Boolean) -> Unit) {
    XNoteDialog(true, onDismiss, "允许这次操作？", backdrop,
        confirmAction = XNoteDialogAction("允许本次", { onAnswer(true) }),
        dismissAction = XNoteDialogAction("不允许", { onAnswer(false) })) {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AgentRequestSummary(event, noteTitles, notebookTitles)
            Text("仅允许这次操作，之后的请求会再次询问。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AgentRequestDetails(event, "agent-approval-arguments")
        }
    }
}

@Composable
private fun AgentRequestSummary(event: AgentToolEventEntity, noteTitles: Map<String, String>, notebookTitles: Map<String, String>) {
    val arguments = remember(event.argumentsJson) { runCatching { Json.parseToJsonElement(event.argumentsJson) as? JsonObject }.getOrNull() }
    Text(agentToolTitle(event.name), style = MaterialTheme.typography.titleMedium)
    if (arguments != null) {
        Text(agentToolDescription(event.name, arguments, noteTitles, notebookTitles))
        val content = (arguments["document_json"] as? JsonPrimitive)?.contentOrNull?.let {
            runCatching { extractPlainText(decodeNoteDocument(it)) }.getOrNull()
        } ?: (arguments["content"] as? JsonPrimitive)?.contentOrNull
        if (!content.isNullOrBlank()) XNoteGroupCard {
            SelectionContainer { Text(content, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
        }
    } else Text("请求暂时无法预览，请查看完整请求。")
}

@Composable
private fun AgentRequestDetails(event: AgentToolEventEntity, tag: String) {
    var expanded by remember(event.id) { mutableStateOf(agentToolTitle(event.name) == event.name) }
    XNoteButton({ expanded = !expanded }) { Text(if (expanded) "收起详情" else "查看完整请求") }
    if (expanded) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(event.argumentsJson, Modifier.testTag(tag), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AgentSnapshotDialog(snapshot: AgentSnapshotEntity, backdrop: Backdrop, onDismiss: () -> Unit) {
    XNoteDialog(true, onDismiss, "发送时的笔记", backdrop, XNoteDialogAction("关闭", onDismiss)) {
        Text(snapshot.title.ifBlank { "未命名笔记" }, style = MaterialTheme.typography.titleMedium)
        Text(extractPlainText(decodeNoteDocument(snapshot.documentJson)).ifBlank { "暂无正文" },
            Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).testTag("agent-snapshot-body"))
    }
}

fun AgentPermissionMode.permissionLabel(): String = when (this) {
    AgentPermissionMode.Private -> "完全隐私"
    AgentPermissionMode.RequestApproval -> "请求批准"
    AgentPermissionMode.FullAccess -> "完全访问"
}

fun AgentToolStatus.toolStatusLabel(): String = when (this) {
    AgentToolStatus.Approved -> "已允许"
    AgentToolStatus.Requested -> "等待允许或执行"
    AgentToolStatus.Denied -> "未获允许"
    AgentToolStatus.Executing -> "正在执行"
    AgentToolStatus.Committed -> "已完成"
    AgentToolStatus.Failed -> "未完成"
    AgentToolStatus.Unknown -> "结果待确认"
}
