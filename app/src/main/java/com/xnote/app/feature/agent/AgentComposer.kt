package com.xnote.app.feature.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.db.AgentFileCard
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.agent.AgentPermission

// -- Type Definitions

data class AgentComposerNote(val id: String, val title: String, val summary: String, val notebook: String,
    val modified: String, val selected: Boolean)

// -- Functions

@Composable
private fun AgentAttachedNoteChip(
    note: AgentComposerNote,
    backdrop: Backdrop,
    onPreview: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    XNoteLiquidGlassPanel(
        backdrop = backdrop,
        modifier = modifier
            .widthIn(max = 240.dp)
            .height(52.dp),
        shape = XNoteSmoothCornerShape(14.dp),
        shadowEnabled = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f, fill = false)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPreview,
                    )
                    .testTag("agent-draft-note-${note.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(XNoteSmoothCornerShape(9.dp))
                        .background(Color(0xFFE09F3E).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyline_stroke_file_text),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFFE09F3E),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (note.selected) {
                            Surface(
                                shape = XNoteSmoothCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = "所选文字",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "笔记 · ${note.notebook}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(XNoteButtonSize)
                    .testTag("agent-remove-note-${note.id}"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyline_stroke_x),
                    contentDescription = "移除附加笔记：${note.title}",
                    modifier = Modifier.size(XNoteIconSizeMedium),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AgentComposer(
    input: String,
    onInputChange: (String) -> Unit,
    enabled: Boolean,
    running: Boolean,
    canSend: Boolean,
    hasDraftFiles: Boolean,
    permission: AgentPermission,
    notes: List<AgentComposerNote>,
    onRemoveNote: (String) -> Unit,
    onPreviewNote: (String) -> Unit,
    backdrop: Backdrop,
    attachmentAnchor: XNotePopupAnchor,
    permissionAnchor: XNotePopupAnchor,
    onAdd: () -> Unit,
    onPermission: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    files: List<AgentFileCard> = emptyList(),
    onRemoveFile: ((String) -> Unit)? = null,
    onPreviewFile: ((AgentFileCard) -> Unit)? = null,
) {
    // -- Derived Values

    val permissionSummary = permission.mode.permissionLabel()
    val showStop = running && input.isBlank() && !hasDraftFiles

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (notes.isNotEmpty() || files.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                notes.forEach { note ->
                    AgentAttachedNoteChip(
                        note = note,
                        backdrop = backdrop,
                        onPreview = { onPreviewNote(note.id) },
                        onRemove = { onRemoveNote(note.id) },
                    )
                }
                files.forEach { file ->
                    AgentComposerFileCard(
                        card = file,
                        backdrop = backdrop,
                        onPreview = { onPreviewFile?.invoke(file) },
                        onRemove = if (onRemoveFile != null) ({ onRemoveFile(file.id) }) else null,
                    )
                }
            }
        }
        XNoteLiquidGlassPanel(backdrop, Modifier.fillMaxWidth().testTag("agent-composer"), XNoteSmoothCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                BasicTextField(
                    value = input, onValueChange = onInputChange, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp, max = 160.dp)
                        .padding(horizontal = 12.dp, vertical = 4.dp).testTag("agent-input"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box {
                            if (input.isEmpty()) Text("输入消息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    },
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onAdd, Modifier.size(XNoteButtonSize).xNotePopupAnchor(attachmentAnchor).testTag("agent-add-attachment"), enabled) {
                        Icon(painterResource(R.drawable.ic_keyline_stroke_plus), "添加附件", Modifier.size(XNoteIconSizeMedium))
                    }
                    TextButton(onPermission, Modifier.xNotePopupAnchor(permissionAnchor).testTag("agent-permission-settings")
                        .semantics { contentDescription = "Agent 权限" }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(permissionSummary,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(painterResource(R.drawable.ic_keyline_stroke_chevron_down), null, Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    LiquidButton(onClick = if (showStop) onStop else onSend, backdrop = backdrop, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(XNoteButtonSize).testTag(if (showStop) "agent-stop" else "agent-send"), enabled = showStop || canSend) {
                        Icon(painterResource(if (showStop) R.drawable.ic_agent_stop else R.drawable.ic_keyline_fill_send),
                            if (showStop) "停止任务" else if (running) "补充当前任务" else "发送", Modifier.size(XNoteIconSizeMedium))
                    }
                }
            }
        }
    }
}
