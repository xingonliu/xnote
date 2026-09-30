package com.xnote.app.feature.agent

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.data.agent.agentVersion
import com.xnote.app.data.db.*
import com.xnote.app.design.XNoteButton
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.decodeNoteDocument
import com.xnote.app.domain.text.extractPlainText
import kotlinx.serialization.json.Json

// -- Type Definitions

internal data class AgentTimelinePresentation(
    val runs: List<AgentRunEntity>, val notes: List<NoteEntity>, val notebookTitles: Map<String, String>,
    val snapshots: List<AgentSnapshotEntity>, val files: List<AgentFileCard>, val running: Boolean, val unresolved: Boolean,
    val bubbleBackdrop: Backdrop,
    val formatTime: (Long) -> String,
)

internal data class AgentTimelineActions(
    val onLongPress: (AgentMessageMenu) -> Unit, val onPreviewFile: (AgentFileCard) -> Unit,
    val onPreviewSnapshot: (AgentSnapshotEntity?) -> Unit, val onOpenReviews: (String) -> Unit,
    val onContinue: (String) -> Unit, val onStop: () -> Unit,
)

// -- Functions

@Composable
internal fun AgentTimelineEntry(item: AgentTimelineItem, joinsNext: Boolean, presentation: AgentTimelinePresentation, actions: AgentTimelineActions) {
    when (item) {
        is AgentTimelineItem.Task -> AgentTaskRow(item, actions.onLongPress, presentation.bubbleBackdrop) { entry -> AgentTimelineEntry(entry, false, presentation, actions) }
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
            val run = presentation.runs.find { it.id == message.runId }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (message.role == AgentMessageRole.Event) {
                    Text(message.text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (message.text.isNotBlank()) {
                    if (message.role == AgentMessageRole.User) AgentMessageBubble(message, message.text, joinsNext, actions.onLongPress, presentation.bubbleBackdrop)
                    else AgentMessageText(message, actions.onLongPress)
                }
                AgentFilesStrip(presentation.files.filter { it.ownerType == "message" && it.ownerId == message.id }, actions.onPreviewFile)
                if (message.role == AgentMessageRole.User) {
                    Json.decodeFromString<List<AgentMessageSource>>(message.sourcesJson).forEach { source ->
                        val snapshot = presentation.snapshots.find { it.id == source.snapshotId }
                        XNoteButton({ actions.onPreviewSnapshot(snapshot) }, enabled = snapshot != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(snapshot?.let { "发送时的笔记 · ${it.title.ifBlank { "未命名笔记" }}" } ?: "笔记已永久删除，无法查看")
                                snapshot?.let { saved ->
                                    Text(extractPlainText(decodeNoteDocument(saved.documentJson)).ifBlank { "暂无正文" }, maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    Text(presentation.notebookTitles[saved.notebookId] ?: if (saved.notebookId == null) "未归档" else "原笔记本已不存在", style = MaterialTheme.typography.labelSmall)
                                    if (saved.noteUpdatedAtEpochMs > 0) Text("修改于 ${presentation.formatTime(saved.noteUpdatedAtEpochMs)}", style = MaterialTheme.typography.labelSmall)
                                    if (source.selection != null) Text("仅润色所选文字", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (message.status == AgentMessageStatus.Pending) Text("已收到，将继续处理", style = MaterialTheme.typography.bodySmall)
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
