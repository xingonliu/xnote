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
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.decodeNoteDocument
import com.xnote.app.domain.text.extractPlainText

// -- Functions

@Composable
fun AgentAttachNotesDialog(notes: List<NoteEntity>, selected: List<String>, backdrop: Backdrop, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    var selection by remember { mutableStateOf(selected.toSet()) }
    var query by remember { mutableStateOf("") }
    XNoteDialog(true, onDismiss, "附加笔记", backdrop,
        confirmAction = XNoteDialogAction("确认 ${selection.size} 篇", { onConfirm(selection.toList()) }, selection.size <= AgentNoteLimits.MaxAttachedNotes),
        dismissAction = XNoteDialogAction("取消", onDismiss)) {
        Text("发送时保存正文快照，每条最多 8 篇。一级权限仅可临时读取该快照。", style = MaterialTheme.typography.bodySmall)
        XNoteTextField(query, { query = it }, placeholder = "搜索笔记标题")
        LazyColumn(Modifier.heightIn(max = 300.dp).testTag("agent-note-picker")) {
            items(notes.filter { it.title.contains(query, ignoreCase = true) }, key = { it.id }) { note ->
                Text((if (note.id in selection) "✓ " else "○ ") + note.title.ifBlank { "未命名笔记" },
                    Modifier.fillMaxWidth().clickable { selection = if (note.id in selection) selection - note.id else selection + note.id }
                        .padding(vertical = 14.dp).testTag("agent-attach-${note.id}"))
            }
        }
        selection.filter { selectedId -> notes.none { it.id == selectedId } }.forEach { missing ->
            LiquidButton({ selection = selection - missing }, backdrop) { Text("移除已删除的附加笔记") }
        }
        if (notes.isEmpty()) Text("暂无可附加的笔记")
    }
}

@Composable
fun AgentPermissionDialog(permission: AgentPermission, notebooks: List<NotebookEntity>, backdrop: Backdrop, authorizing: Boolean,
    onDismiss: () -> Unit, onSave: (AgentPermission, Boolean) -> Unit) {
    var choice by remember { mutableStateOf(permission.copy(level = if (authorizing && permission.level == AgentPermissionLevel.None) AgentPermissionLevel.Read else permission.level)) }
    var always by remember { mutableStateOf(false) }
    XNoteDialog(true, onDismiss, if (authorizing) "授权本次工具调用" else "Agent 权限与范围", backdrop,
        confirmAction = XNoteDialogAction(if (authorizing) "允许并继续" else "保存", { onSave(choice, always) }, !authorizing || choice.level >= AgentPermissionLevel.Read),
        dismissAction = XNoteDialogAction("取消", onDismiss)) {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("操作权限", style = MaterialTheme.typography.labelLarge)
            AgentPermissionLevel.entries.forEach { level ->
                LiquidButton({ choice = choice.copy(level = level) }, backdrop, modifier = Modifier.testTag("agent-permission-${level.name}")) {
                    Text((if (choice.level == level) "✓ " else "") + level.permissionLabel())
                }
            }
            Text("数据范围（提高等级不会改变范围）", style = MaterialTheme.typography.labelLarge)
            AgentScope.entries.forEach { scope ->
                LiquidButton({ choice = choice.copy(scope = scope) }, backdrop, modifier = Modifier.testTag("agent-scope-${scope.name}")) {
                    Text((if (choice.scope == scope) "✓ " else "") + scope.scopeLabel())
                }
            }
            if (choice.scope == AgentScope.Notebooks) notebooks.forEach { book ->
                Text((if (book.id in choice.notebookIds) "✓ " else "○ ") + book.name,
                    Modifier.fillMaxWidth().clickable { choice = choice.copy(notebookIds = if (book.id in choice.notebookIds) choice.notebookIds - book.id else choice.notebookIds + book.id) }.padding(12.dp))
            }
            if (authorizing) {
                LiquidButton({ always = !always }, backdrop) { Text(if (always) "✓ 始终允许：保存为全局权限" else "仅本次运行允许") }
                Text("缩小全局范围或降低权限后，本次授权也会失效。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AgentSnapshotDialog(snapshot: AgentSnapshotEntity, backdrop: Backdrop, onDismiss: () -> Unit) {
    XNoteDialog(true, onDismiss, "发送时快照", backdrop, XNoteDialogAction("关闭", onDismiss)) {
        Text(snapshot.title.ifBlank { "未命名笔记" }, style = MaterialTheme.typography.titleMedium)
        Text("版本 ${snapshot.version.take(12)} · 此处保留发送时内容", style = MaterialTheme.typography.bodySmall)
        Text(extractPlainText(decodeNoteDocument(snapshot.documentJson)), Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
    }
}

@Composable
fun AgentToolDialog(event: AgentToolEventEntity, backdrop: Backdrop, onDismiss: () -> Unit) {
    XNoteDialog(true, onDismiss, "工具调用详情", backdrop, XNoteDialogAction("关闭", onDismiss)) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${event.name} · ${event.status.toolStatusLabel()}")
            Text("运行 ${event.runId.take(8)} · 权限版本 ${event.permissionRevision}", style = MaterialTheme.typography.bodySmall)
            Text("参数：${event.argumentsJson}")
            Text("结果：${event.resultJson ?: "等待执行或授权"}")
            Text("开始：${java.util.Date(event.createdAtEpochMs)}\n结束：${event.committedAtEpochMs?.let { java.util.Date(it) } ?: "尚未结束"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun AgentPermissionLevel.permissionLabel(): String = when (this) {
    AgentPermissionLevel.None -> "一级 · 不可查看"
    AgentPermissionLevel.Read -> "二级 · 可查看"
    AgentPermissionLevel.Edit -> "三级 · 可编辑"
}

fun AgentScope.scopeLabel(): String = when (this) {
    AgentScope.Attached -> "仅主动附加笔记"
    AgentScope.Unfiled -> "未归档笔记"
    AgentScope.Notebooks -> "指定笔记本"
    AgentScope.All -> "全部笔记"
}

fun AgentToolStatus.toolStatusLabel(): String = when (this) {
    AgentToolStatus.Requested -> "等待授权或执行"
    AgentToolStatus.Denied -> "用户已拒绝"
    AgentToolStatus.Executing -> "正在执行"
    AgentToolStatus.Committed -> "已完成"
    AgentToolStatus.Failed -> "失败"
    AgentToolStatus.Unknown -> "提交结果待核实"
}
