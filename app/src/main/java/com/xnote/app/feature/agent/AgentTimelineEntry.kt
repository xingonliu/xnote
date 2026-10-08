package com.xnote.app.feature.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.agent.agentVersion
import com.xnote.app.data.db.*
import com.xnote.app.design.*
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.decodeNoteDocument
import com.xnote.app.domain.text.extractPlainText
import kotlinx.serialization.json.Json

// -- Type Definitions

internal data class AgentTimelinePresentation(
    val runs: List<AgentRunEntity>, val notes: List<NoteEntity>, val notebookTitles: Map<String, String>,
    val snapshots: List<AgentSnapshotEntity>, val files: List<AgentFileCard>, val running: Boolean, val unresolved: Boolean,
    val bubbleBackdrop: Backdrop,
    val bubbleViewport: () -> Rect,
    val formatTime: (Long) -> String,
)

internal data class AgentTimelineActions(
    val onLongPress: (AgentMessageMenu) -> Unit, val onPreviewFile: (AgentFileCard) -> Unit,
    val onPreviewSnapshot: (AgentSnapshotEntity?) -> Unit, val onOpenReviews: (String) -> Unit,
    val onContinue: (String) -> Unit, val onStop: () -> Unit,
)

// -- Functions

@Composable
private fun AgentSnapshotCard(
    snapshot: AgentSnapshotEntity?,
    selection: Boolean,
    notebookTitle: String,
    formatTime: (Long) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val highContrast = LocalXNoteInteractionSettings.current.highContrast

    val containerColor = when {
        highContrast -> MaterialTheme.colorScheme.surfaceVariant
        isLight -> Color(0xFFF2F2F7).copy(alpha = 0.95f)
        else -> Color(0xFF2C2C2E).copy(alpha = 0.90f)
    }
    val borderColor = when {
        highContrast -> MaterialTheme.colorScheme.outline
        isLight -> Color.Black.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val shape = XNoteSmoothCornerShape(14.dp)

    Surface(
        onClick = onClick,
        enabled = snapshot != null,
        modifier = modifier.widthIn(min = 200.dp, max = 320.dp),
        shape = shape,
        color = containerColor,
        border = BorderStroke(0.5.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(XNoteSmoothCornerShape(8.dp))
                        .background(Color(0xFFE09F3E).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyline_stroke_file_text),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFE09F3E),
                    )
                }
                Text(
                    text = snapshot?.let { "发送时的笔记 · ${it.title.ifBlank { "未命名笔记" }}" } ?: "笔记已永久删除，无法查看",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (selection) {
                    Surface(
                        shape = XNoteSmoothCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "仅润色所选文字",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            snapshot?.let { saved ->
                val bodyText = extractPlainText(decodeNoteDocument(saved.documentJson)).ifBlank { "暂无正文" }
                Text(
                    text = bodyText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val timeStr = if (saved.noteUpdatedAtEpochMs > 0) " · 修改于 ${formatTime(saved.noteUpdatedAtEpochMs)}" else ""
                Text(
                    text = "$notebookTitle$timeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
internal fun AgentTimelineEntry(item: AgentTimelineItem, joinsNext: Boolean, presentation: AgentTimelinePresentation, actions: AgentTimelineActions) {
    when (item) {
        is AgentTimelineItem.Task -> AgentTaskRow(item, actions.onLongPress, presentation.bubbleBackdrop, presentation.bubbleViewport) { entry -> AgentTimelineEntry(entry, false, presentation, actions) }
        is AgentTimelineItem.Tool -> {
            val event = item.event
            val run = presentation.runs.find { it.id == event.runId }
            val canContinue = !presentation.running && !presentation.unresolved && run?.errorCode != "history_removed" &&
                run?.status in setOf(AgentRunStatus.Failed, AgentRunStatus.Cancelled)
            AgentToolHistoryRow(event, presentation.notes, presentation.notebookTitles, actions.onOpenReviews,
                onContinue = if (canContinue) ({ actions.onContinue(event.runId) }) else null,
                onStop = if (presentation.running && run?.status == AgentRunStatus.Running) actions.onStop else null)
        }
        is AgentTimelineItem.Message -> {
            val message = item.message
            val isUser = message.role == AgentMessageRole.User
            val run = presentation.runs.find { it.id == message.runId }
            val messageFiles = presentation.files.filter { it.ownerType == "message" && it.ownerId == message.id }
            val sources = if (isUser) Json.decodeFromString<List<AgentMessageSource>>(message.sourcesJson) else emptyList()
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (message.role == AgentMessageRole.Event) {
                    Text(message.text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (message.text.isNotBlank()) {
                    if (isUser) AgentMessageBubble(message, message.text,
                        joinsNext || messageFiles.isNotEmpty() || sources.isNotEmpty(), actions.onLongPress,
                        presentation.bubbleBackdrop, presentation.bubbleViewport)
                    else AgentMessageText(message, actions.onLongPress)
                }
                if (messageFiles.isNotEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        AgentFilesStrip(messageFiles, actions.onPreviewFile)
                    }
                }
                if (isUser) {
                    sources.forEach { source ->
                        val snapshot = presentation.snapshots.find { it.id == source.snapshotId }
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            AgentSnapshotCard(
                                snapshot = snapshot,
                                selection = source.selection != null,
                                notebookTitle = snapshot?.notebookId?.let { presentation.notebookTitles[it] }
                                    ?: if (snapshot?.notebookId == null) "未归档" else "原笔记本已不存在",
                                formatTime = presentation.formatTime,
                                onClick = { actions.onPreviewSnapshot(snapshot) },
                            )
                        }
                    }
                    if (message.status == AgentMessageStatus.Pending) {
                        Text(
                            text = "已收到，将继续处理",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
                if (message.role == AgentMessageRole.Assistant) {
                    if (message.status != AgentMessageStatus.Complete) Text(when (message.status) {
                        AgentMessageStatus.Streaming, AgentMessageStatus.Pending -> "生成中"
                        AgentMessageStatus.Failed -> "失败 · 已保留内容"
                        AgentMessageStatus.Cancelled -> "已停止"
                        AgentMessageStatus.Interrupted -> if (run?.status == AgentRunStatus.PausedBudget) "达到容量上限" else "已中断"
                        AgentMessageStatus.Complete -> "已完成"
                    }, style = MaterialTheme.typography.bodySmall)
                    if (!presentation.running && !presentation.unresolved && run?.errorCode != "history_removed" &&
                        run?.status in setOf(AgentRunStatus.Failed, AgentRunStatus.Cancelled)) {
                        XNoteButton({ actions.onContinue(checkNotNull(run).id) }) { Text("继续此任务") }
                    }
                }
            }
        }
    }
}
