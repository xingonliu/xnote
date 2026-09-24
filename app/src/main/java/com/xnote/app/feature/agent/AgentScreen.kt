package com.xnote.app.feature.agent

import android.Manifest
import android.app.NotificationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.data.agent.AgentTimeline
import com.xnote.app.design.XNoteDialog
import com.xnote.app.design.XNoteDialogAction
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteTextField
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.agent.*
import com.xnote.app.data.db.AgentSnapshotEntity
import com.xnote.app.data.db.AgentToolEventEntity
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// -- Functions

@Composable
fun AgentScreen(timeline: AgentTimeline, backdrop: Backdrop, contentPadding: PaddingValues, modifier: Modifier = Modifier,
    onOverlayVisible: (Boolean) -> Unit = {}, onOpenReviews: () -> Unit) {
    val messages by timeline.messages.collectAsState(emptyList())
    val runs by timeline.runs.collectAsState(emptyList())
    val queue by timeline.queue.collectAsState(emptyList())
    val selectedNotes by timeline.draftNotes.collectAsState()
    val availableNotes by timeline.noteStore.availableNotes.collectAsState(emptyList())
    val notebooks by timeline.noteStore.notebooks.collectAsState(emptyList())
    val permission by timeline.noteStore.permission.collectAsState(AgentPermission())
    val snapshots by timeline.noteStore.snapshots.collectAsState(emptyList())
    val tools by timeline.noteStore.toolEvents.collectAsState(emptyList())
    var attachDialog by remember { mutableStateOf(false) }
    var permissionDialog by remember { mutableStateOf(false) }
    var permissionRequest by remember { mutableStateOf<AgentToolEventEntity?>(null) }
    var snapshotPreview by remember { mutableStateOf<AgentSnapshotEntity?>(null) }
    var toolPreview by remember { mutableStateOf<AgentToolEventEntity?>(null) }
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
    val state by timeline.state.collectAsState()
    val savedDraft by timeline.draft.collectAsState()
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf(false) }
    val overlayVisible = attachDialog || permissionDialog || permissionRequest != null || snapshotPreview != null || toolPreview != null || confirmClear
    SideEffect { onOverlayVisible(overlayVisible) }
    DisposableEffect(Unit) { onDispose { onOverlayVisible(false) } }
    val unresolved = runs.any { it.status !in setOf(AgentRunStatus.Complete, AgentRunStatus.Failed, AgentRunStatus.Cancelled) }
    val keyboardVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    LaunchedEffect(state.ready) { if (state.ready && !restored) { input = savedDraft; restored = true } }
    LaunchedEffect(messages.size) {
        if (list.layoutInfo.totalItemsCount > 0) list.animateScrollToItem(list.layoutInfo.totalItemsCount - 1)
    }
    fun action(block: suspend () -> Unit) { scope.launch {
        try { block(); error = null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = when (failure) { is AgentBudgetException, is IllegalArgumentException -> failure.message; else -> safeModelError(failure) } }
    } }
    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().imePadding().padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!keyboardVisible || state.running || unresolved) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.running) LiquidButton({ action { timeline.newTopic() } }, backdrop, enabled = state.ready && !state.running && !unresolved && queue.isEmpty(), modifier = Modifier.testTag("agent-new-topic")) { Text("开始新话题") }
            LiquidButton({ confirmClear = true }, backdrop, enabled = state.ready, modifier = Modifier.testTag("agent-clear")) { Text("清空聊天") }
            LiquidButton({ permissionDialog = true }, backdrop, modifier = Modifier.testTag("agent-permission-settings")) { Text("权限与范围") }
            LiquidButton(onOpenReviews, backdrop, modifier = Modifier.testTag("agent-reviews")) { Text("笔记改动") }
            if (state.running) LiquidButton(timeline::stop, backdrop, modifier = Modifier.testTag("agent-stop")) { Text("停止") }
            if (!state.running && unresolved) {
                val recoverable = runs.lastOrNull { it.status in setOf(AgentRunStatus.Interrupted, AgentRunStatus.PausedBudget) }
                if (recoverable != null) LiquidButton({ action { timeline.continueRun(recoverable.id) } }, backdrop, modifier = Modifier.testTag("agent-continue")) { Text("继续任务") }
            }
            if (!state.running && unresolved) LiquidButton({ action { timeline.finishUnresolved() } }, backdrop) { Text("保留内容并结束任务") }
        }
        if (!state.ready) Text("正在恢复对话…")
        (error ?: state.notice)?.let { Text(it, Modifier.testTag("agent-notice"), style = MaterialTheme.typography.bodySmall) }
        if (messages.isEmpty() && state.ready && !keyboardVisible) Text("在这里与 Agent 对话。模型在“我的 → 模型与服务商”配置。", style = MaterialTheme.typography.bodyMedium)
        if (runs.any { it.status == AgentRunStatus.WaitingPermission }) {
            val request = tools.firstOrNull { it.status == AgentToolStatus.Requested && runs.any { run -> run.id == it.runId && run.status == AgentRunStatus.WaitingPermission } }
            if (request != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidButton({ toolPreview = request }, backdrop) { Text("查看工具请求") }
                LiquidButton({ permissionRequest = request }, backdrop, enabled = !state.running, modifier = Modifier.testTag("agent-authorize")) { Text("${request.name} 需要授权") }
                LiquidButton({ action { timeline.answerPermission(request.runId, request.callId, null) } }, backdrop, enabled = !state.running) { Text("拒绝") }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("agent-timeline"), state = list, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!notificationsEnabled) item(key = "notification-permission") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("通知未开启，后台运行状态和停止入口可能不可见。", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            LiquidButton({ notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }, backdrop) { Text("开启通知") }
        } }
            if (queue.isNotEmpty()) item(key = "queue-header") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("队列 · ${queue.size} 条", Modifier.weight(1f))
                    if (!state.running && !unresolved) LiquidButton({ action { timeline.resumeQueue() } }, backdrop, modifier = Modifier.testTag("agent-resume-queue")) { Text("继续队列") }
                }
            }
            items(queue, key = { "queue-${it.id}" }) { queued ->
                val queuedMessage = messages.find { it.id == queued.messageId }
                if (queuedMessage != null) AgentQueueCard(queued.id, queuedMessage.text, queued.status == AgentQueueStatus.Paused, backdrop,
                    onSave = { value -> action { timeline.editQueued(queued.id, value) } },
                    onMove = { direction -> action { timeline.moveQueued(queued.id, direction) } },
                    onRemove = { action { timeline.removeQueued(queued.id) } })
            }
            items(messages.filter { message -> queue.none { it.messageId == message.id } && !(message.role == AgentMessageRole.Event && message.text == "模型请求") }, key = { it.sequence }) { message ->
                val run = runs.find { it.id == message.runId }
                XNoteGroupCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(when (message.role) { AgentMessageRole.User -> "你"; AgentMessageRole.Assistant -> "Agent"; else -> "执行记录" }, style = MaterialTheme.typography.labelMedium)
                        SelectionContainer { Text((if (message.role == AgentMessageRole.Tool) "工具结果已保存" else message.text).ifEmpty { if (message.status == AgentMessageStatus.Streaming) "正在生成…" else if (message.modelJson != null) "工具调用" else "未生成回复" }) }
                        if (message.role == AgentMessageRole.User) {
                            Json.decodeFromString<List<AgentMessageSource>>(message.sourcesJson).forEach { source ->
                                val snapshot = snapshots.find { it.id == source.snapshotId }
                                LiquidButton({ snapshotPreview = snapshot }, backdrop, enabled = snapshot != null) {
                                    Text(snapshot?.let { "发送快照 · ${it.title.ifBlank { "未命名笔记" }}" } ?: "笔记已永久删除 · 快照不可用")
                                }
                            }
                        }
                        if (message.role == AgentMessageRole.User && message.status == AgentMessageStatus.Pending) Text("将在下一执行边界补充", style = MaterialTheme.typography.bodySmall)
                        if (!state.running && !unresolved && message.role != AgentMessageRole.Event) LiquidButton({ action { timeline.deleteMessage(message.id) } }, backdrop) { Text("删除消息") }
                        if (message.role == AgentMessageRole.Tool) tools.filter { tool -> tool.runId == message.runId && message.modelJson?.let { Json.decodeFromString<ModelMessage>(it).results.any { result -> result.id == tool.callId } } == true }.forEach { tool ->
                            LiquidButton({ toolPreview = tool }, backdrop) { Text("${tool.name} · ${tool.status.toolStatusLabel()}") }
                        }
                        if (message.role == AgentMessageRole.Assistant) {
                            Text(when (message.status) {
                                AgentMessageStatus.Complete -> if (message.modelJson != null) "工具调用已记录" else "已完成"
                                AgentMessageStatus.Streaming, AgentMessageStatus.Pending -> "生成中"
                                AgentMessageStatus.Failed -> "失败 · 已保留内容"
                                AgentMessageStatus.Cancelled -> "已停止"
                                AgentMessageStatus.Interrupted -> if (run?.status == AgentRunStatus.PausedBudget) "达到容量上限" else "已中断"
                            }, style = MaterialTheme.typography.bodySmall)
                            if (!state.running && !unresolved && run?.errorCode != "history_removed" && run?.status in setOf(AgentRunStatus.Failed, AgentRunStatus.Cancelled)) {
                                LiquidButton({ action { timeline.continueRun(checkNotNull(run).id) } }, backdrop) { Text("继续此任务") }
                            }
                            if (run?.inputTokens != null || run?.outputTokens != null) Text(
                                "服务用量：输入 ${run.inputTokens ?: "未知"} / 输出 ${run.outputTokens ?: "未知"} Token", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidButton({ attachDialog = true }, backdrop, enabled = state.ready, modifier = Modifier.testTag("agent-attach-notes")) { Text("附加笔记 · ${selectedNotes.size}") }
            Text(permission.level.permissionLabel() + " · " + permission.scope.scopeLabel(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        }
        XNoteTextField(input, { value -> input = value; action { timeline.saveDraft(value) } },
            Modifier.heightIn(min = 48.dp, max = 160.dp).testTag("agent-input"), placeholder = "输入消息", singleLine = false, enabled = state.ready && restored)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LiquidButton({ val sent = input; action { timeline.send(sent); input = "" } }, backdrop,
            enabled = state.ready && restored && (state.running || (!unresolved && queue.isEmpty())) && input.isNotBlank(), modifier = Modifier.weight(1f).testTag("agent-send")) { Text(if (state.running) "补充当前任务" else "发送") }
            LiquidButton({ val sent = input; action { timeline.enqueue(sent); input = "" } }, backdrop,
                enabled = state.ready && restored && input.isNotBlank(), modifier = Modifier.testTag("agent-enqueue")) { Text("加入队列") }

        }
    }
    if (attachDialog) AgentAttachNotesDialog(availableNotes, selectedNotes, backdrop, { attachDialog = false }) { ids ->
        action { timeline.selectDraftNotes(ids); attachDialog = false }
    }
    if (permissionDialog || permissionRequest != null) AgentPermissionDialog(permission, notebooks, backdrop, permissionRequest != null,
        onDismiss = { permissionDialog = false; permissionRequest = null }) { choice, always ->
        val request = permissionRequest
        action {
            if (request == null) timeline.savePermission(choice) else timeline.answerPermission(request.runId, request.callId, choice, always)
            permissionDialog = false; permissionRequest = null
        }
    }
    snapshotPreview?.let { AgentSnapshotDialog(it, backdrop) { snapshotPreview = null } }
    toolPreview?.let { event ->
        val run = runs.find { it.id == event.runId }
        val canContinue = !state.running && !unresolved && run?.errorCode != "history_removed" && run?.status in setOf(AgentRunStatus.Failed, AgentRunStatus.Cancelled)
        AgentToolDialog(event, backdrop, if (canContinue) ({ action { timeline.continueRun(event.runId); toolPreview = null } }) else null) { toolPreview = null }
    }
    XNoteDialog(confirmClear, { confirmClear = false }, "清空聊天", backdrop,
        confirmAction = XNoteDialogAction("清空", { action { timeline.clearChat(); input = ""; confirmClear = false } }, destructive = true),
        dismissAction = XNoteDialogAction("取消", { confirmClear = false })) {
        Text("将停止当前任务、清空队列和聊天记录。已经应用的笔记改动及待审阅记录会保留。")
    }
    }
}

@Composable
private fun AgentQueueCard(id: String, text: String, paused: Boolean, backdrop: Backdrop,
    onSave: (String) -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    var editing by rememberSaveable(id) { mutableStateOf(false) }
    var input by rememberSaveable(id, text) { mutableStateOf(text) }
    XNoteGroupCard(Modifier.fillMaxWidth().testTag("agent-queue-$id")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (paused) "队列已暂停" else "等待执行", style = MaterialTheme.typography.labelMedium)
            if (editing) XNoteTextField(input, { input = it }, singleLine = false) else Text(text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidButton({ if (editing) { onSave(input); editing = false } else editing = true }, backdrop, enabled = !editing || input.isNotBlank()) { Text(if (editing) "保存" else "编辑") }
                LiquidButton({ onMove(-1) }, backdrop) { Text("上移") }
                LiquidButton({ onMove(1) }, backdrop) { Text("下移") }
                LiquidButton(onRemove, backdrop) { Text("删除") }
            }
        }
    }
}
