package com.xnote.app.feature.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.data.db.*
import com.xnote.app.design.*
import com.xnote.app.design.XNoteButton
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.decodeNoteDocument
import com.xnote.app.domain.text.extractPlainText
import kotlinx.serialization.json.Json

// -- Functions

@Composable
fun AgentAttachNotesDialog(notes: List<NoteEntity>, selected: List<String>, backdrop: Backdrop, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    var selection by remember { mutableStateOf(selected.toSet()) }
    var query by remember { mutableStateOf("") }
    XNoteDialog(true, onDismiss, "附加笔记", backdrop,
        confirmAction = XNoteDialogAction("确认 ${selection.size} 篇", { onConfirm(selection.toList()) }, selection.size <= AgentNoteLimits.MaxAttachedNotes),
        dismissAction = XNoteDialogAction("取消", onDismiss)) {
        Text("发送时保存正文快照，每条最多 8 篇。读取快照同样遵守当前权限；请求批准模式下每次读取都需批准。", style = MaterialTheme.typography.bodySmall)
        XNoteTextField(query, { query = it }, placeholder = "搜索笔记标题")
        LazyColumn(Modifier.heightIn(max = 300.dp).testTag("agent-note-picker")) {
            items(notes.filter { it.title.contains(query, ignoreCase = true) }, key = { it.id }) { note ->
                Text((if (note.id in selection) "✓ " else "○ ") + note.title.ifBlank { "未命名笔记" },
                    Modifier.fillMaxWidth().clickable { selection = if (note.id in selection) selection - note.id else selection + note.id }
                        .padding(vertical = 14.dp).testTag("agent-attach-${note.id}"))
            }
        }
        selection.filter { selectedId -> notes.none { it.id == selectedId } }.forEach { missing ->
            XNoteButton({ selection = selection - missing }) { Text("移除已删除的附加笔记") }
        }
        if (notes.isEmpty()) Text("暂无可附加的笔记")
    }
}

@Composable
fun AgentPermissionDialog(permission: AgentPermission, backdrop: Backdrop, onDismiss: () -> Unit, onSave: (AgentPermission) -> Unit) {
    var choice by remember { mutableStateOf(permission) }
    XNoteDialog(true, onDismiss, "Agent 权限", backdrop,
        confirmAction = XNoteDialogAction("保存", { onSave(choice) }), dismissAction = XNoteDialogAction("取消", onDismiss)) {
        AgentPermissionMode.entries.forEach { mode ->
            XNoteButton({ choice = choice.copy(mode = mode) }, modifier = Modifier.testTag("agent-permission-${mode.name}")) {
                Text((if (choice.mode == mode) "✓ " else "") + mode.permissionLabel())
            }
        }
        Text(when (choice.mode) {
            AgentPermissionMode.Private -> "禁用全部工具与自动记忆读取。你主动发送的文字和文件仍会发送给模型服务。"
            AgentPermissionMode.RequestApproval -> "默认模式。每次工具调用须先批准，批准仅对本次调用及其固定参数有效。"
            AgentPermissionMode.FullAccess -> "工具可自动读取和修改应用数据，无需逐次批准。"
        }, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AgentApprovalDialog(event: AgentToolEventEntity, backdrop: Backdrop, onDismiss: () -> Unit, onAnswer: (Boolean) -> Unit) {
    XNoteDialog(true, onDismiss, "批准工具调用", backdrop,
        confirmAction = XNoteDialogAction("批准本次", { onAnswer(true) }),
        dismissAction = XNoteDialogAction("拒绝本次", { onAnswer(false) })) {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("工具：${event.name}", style = MaterialTheme.typography.titleMedium)
            Text("批准后执行以下固定参数；读取、写入和后续调用均需分别批准。关闭窗口保持等待。")
            Text("完整参数：\n${event.argumentsJson}", modifier = Modifier.testTag("agent-approval-arguments"))
        }
    }
}

@Composable
fun AgentSnapshotDialog(snapshot: AgentSnapshotEntity, backdrop: Backdrop, onDismiss: () -> Unit) {
    XNoteDialog(true, onDismiss, "发送时快照", backdrop, XNoteDialogAction("关闭", onDismiss)) {
        Text(snapshot.title.ifBlank { "未命名笔记" }, style = MaterialTheme.typography.titleMedium)
        Text("版本 ${snapshot.version.take(12)} · 此处保留发送时内容", style = MaterialTheme.typography.bodySmall)
        Text(extractPlainText(decodeNoteDocument(snapshot.documentJson)), Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).testTag("agent-snapshot-body"))
    }
}

@Composable
fun AgentToolDialog(event: AgentToolEventEntity, backdrop: Backdrop, onContinue: (() -> Unit)?, onStop: (() -> Unit)?, onDismiss: () -> Unit) {
    XNoteDialog(true, onDismiss, "工具调用详情", backdrop, XNoteDialogAction("关闭", onDismiss)) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${event.name} · ${event.status.toolStatusLabel()}")
            Text("运行 ${event.runId.take(8)} · 权限版本 ${event.permissionRevision}", style = MaterialTheme.typography.bodySmall)
            val decisions = Json.decodeFromString<List<AgentToolDecision>>(event.decisionsJson)
            if (decisions.isEmpty()) Text("此历史调用未保存授权依据。", style = MaterialTheme.typography.bodySmall)
            decisions.forEach { decision ->
                Text("${java.util.Date(decision.atEpochMs)} · ${decision.reason}")
                Text("当时权限：${decision.permission.mode.permissionLabel()} · 版本 ${decision.permission.revision}")
                Text("当时主动附加：${decision.attachedNoteIds.size} 篇")
            }
            Text("参数：${event.argumentsJson}")
            Text("结果：${event.resultJson ?: "等待执行或授权"}")
            Text("开始：${java.util.Date(event.createdAtEpochMs)}\n结束：${event.committedAtEpochMs?.let { java.util.Date(it) } ?: "尚未结束"}", style = MaterialTheme.typography.bodySmall)
            if (onStop != null) XNoteButton(onStop, modifier = Modifier.testTag("agent-tool-stop")) { Text("停止所属任务") }
            if (onContinue != null) {
                Text("继续所属任务会保留已提交结果，后续执行重新检查当前权限。", style = MaterialTheme.typography.bodySmall)
                XNoteButton(onContinue, modifier = Modifier.testTag("agent-tool-continue")) { Text("继续此任务") }
            }
        }
    }
}

fun AgentPermissionMode.permissionLabel(): String = when (this) {
    AgentPermissionMode.Private -> "完全隐私"
    AgentPermissionMode.RequestApproval -> "请求批准"
    AgentPermissionMode.FullAccess -> "完全访问"
}

fun AgentToolStatus.toolStatusLabel(): String = when (this) {
    AgentToolStatus.Approved -> "已批准本次调用"
    AgentToolStatus.Requested -> "等待授权或执行"
    AgentToolStatus.Denied -> "未获允许"
    AgentToolStatus.Executing -> "正在执行"
    AgentToolStatus.Committed -> "已完成"
    AgentToolStatus.Failed -> "失败"
    AgentToolStatus.Unknown -> "提交结果待核实"
}
