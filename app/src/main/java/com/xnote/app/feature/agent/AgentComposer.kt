package com.xnote.app.feature.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.design.*
import com.xnote.app.domain.agent.AgentPermission
import com.xnote.app.domain.agent.AgentPermissionLevel
import com.xnote.app.domain.agent.AgentScope

// -- Type Definitions

data class AgentComposerNote(val id: String, val title: String, val summary: String, val notebook: String,
    val modified: String, val selected: Boolean)

// -- Functions

@Composable
fun AgentComposer(
    input: String,
    onInputChange: (String) -> Unit,
    enabled: Boolean,
    running: Boolean,
    canSend: Boolean,
    permission: AgentPermission,
    notes: List<AgentComposerNote>,
    onRemoveNote: (String) -> Unit,
    onPreviewNote: (String) -> Unit,
    attachmentAnchor: XNotePopupAnchor,
    onAdd: () -> Unit,
    onPermission: () -> Unit,
    onSend: () -> Unit,
) {
    // -- Derived Values

    val permissionSummary = when (permission.level) {
        AgentPermissionLevel.None -> "不可查看"
        AgentPermissionLevel.Read -> "可查看"
        AgentPermissionLevel.Edit -> "可编辑"
    } + " · " + when (permission.scope) {
        AgentScope.Attached -> "附加笔记"
        AgentScope.Unfiled -> "未归档"
        AgentScope.Notebooks -> "指定笔记本"
        AgentScope.All -> "全部笔记"
    }

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        XNoteSmoothCornerShape(24.dp)).padding(8.dp).testTag("agent-composer")) {
        if (notes.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEach { note ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ onPreviewNote(note.id) }, Modifier.widthIn(max = 232.dp).testTag("agent-draft-note-${note.id}")) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(note.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(note.summary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text("${note.notebook} · ${note.modified}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            if (note.selected) Text("仅润色所选文字", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton({ onRemoveNote(note.id) }, Modifier.testTag("agent-remove-note-${note.id}")) {
                        Icon(painterResource(R.drawable.ic_keyline_stroke_bin), "移除附加笔记：${note.title}", Modifier.size(18.dp))
                    }
                }
            }
        }
        BasicTextField(
            value = input, onValueChange = onInputChange, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 160.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp).testTag("agent-input"),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Box {
                    if (input.isEmpty()) Text("输入消息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
            },
        )
        if (running && input.isNotBlank()) Text(if (notes.any { it.selected }) "选区润色为独立任务，可从任务队列加入" else "发送后补充当前任务", Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onAdd, Modifier.size(48.dp).xNotePopupAnchor(attachmentAnchor).testTag("agent-add-attachment"), enabled) {
                Icon(painterResource(R.drawable.ic_keyline_stroke_plus), "添加附件", Modifier.size(22.dp))
            }
            TextButton(onPermission, Modifier.weight(1f).testTag("agent-permission-settings")
                .semantics { contentDescription = "权限与范围" }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(permissionSummary,
                        Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(painterResource(R.drawable.ic_keyline_stroke_chevron_down), null, Modifier.size(16.dp))
                }
            }
            FilledIconButton(onSend, Modifier.size(48.dp).testTag("agent-send"), enabled = canSend) {
                Icon(painterResource(R.drawable.ic_keyline_stroke_arrow_up), if (running) "补充当前任务" else "发送", Modifier.size(22.dp))
            }
        }
    }
}
