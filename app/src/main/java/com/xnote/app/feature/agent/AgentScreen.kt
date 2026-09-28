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
import com.xnote.app.design.*
import com.xnote.app.R
import com.xnote.app.data.repository.NoteLibrary
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.xnote.app.domain.document.decodeNoteDocument
import com.xnote.app.domain.text.extractPlainText
import androidx.compose.material3.TextButton
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.agent.*
import com.xnote.app.data.db.AgentSnapshotEntity
import com.xnote.app.data.db.AgentToolEventEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// -- Functions

@Composable
fun AgentScreen(timeline: AgentTimeline, library: NoteLibrary, backdrop: Backdrop, contentPadding: PaddingValues, modifier: Modifier = Modifier,
    onOverlayVisible: (Boolean) -> Unit = {}, onOpenModels: () -> Unit) {
    // -- State

    val messages by timeline.messages.collectAsState(emptyList())
    val runs by timeline.runs.collectAsState(emptyList())
    val queue by timeline.queue.collectAsState(emptyList())
    val selectedNotes by timeline.draftNotes.collectAsState()
    val availableNotes by timeline.noteStore.availableNotes.collectAsState(emptyList())
    val trashedNotes by timeline.noteStore.trashedNotes.collectAsState(emptyList())
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
    var attachmentMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var queueDrawer by remember { mutableStateOf(false) }
    var reviewsOpen by remember { mutableStateOf(false) }
    var reviewNoteId by remember { mutableStateOf<String?>(null) }
    var draftPreviewId by remember { mutableStateOf<String?>(null) }
    val attachmentAnchor = rememberXNotePopupAnchor()
    val moreAnchor = rememberXNotePopupAnchor()
    val keyboard = LocalSoftwareKeyboardController.current
    val direction = LocalLayoutDirection.current
    var restored by remember { mutableStateOf(false) }
    // -- Derived Values

    val overlayVisible = attachDialog || permissionDialog || permissionRequest != null || snapshotPreview != null || toolPreview != null || attachmentMenu || moreMenu || queueDrawer || reviewsOpen || draftPreviewId != null
    val unresolved = runs.any { it.status !in setOf(AgentRunStatus.Complete, AgentRunStatus.Failed, AgentRunStatus.Cancelled) }
    val waitingConflict = runs.firstOrNull { it.status == AgentRunStatus.WaitingConflict }
    val keyboardVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    // -- Functions

    fun action(block: suspend () -> Unit) { scope.launch {
        try { block(); error = null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = when (failure) { is AgentBudgetException, is IllegalArgumentException -> failure.message; else -> safeModelError(failure) } }
    } }
    fun openReviews(noteId: String?) {
        keyboard?.hide()
        reviewNoteId = noteId
        reviewsOpen = true
    }

    // -- Lifecycle Hooks

    SideEffect { onOverlayVisible(overlayVisible) }
    DisposableEffect(Unit) { onDispose { onOverlayVisible(false) } }
    LaunchedEffect(state.ready) { if (state.ready && !restored) { input = savedDraft; restored = true } }
    LaunchedEffect(messages.size) {
        if (list.layoutInfo.totalItemsCount > 0) list.animateScrollToItem(list.layoutInfo.totalItemsCount - 1)
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding().padding(
            start = contentPadding.calculateStartPadding(direction),
            end = contentPadding.calculateEndPadding(direction),
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = if (keyboardVisible) 8.dp else contentPadding.calculateBottomPadding(),
        ), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("agent-header"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorGlassIconButton(R.drawable.ic_keyline_stroke_file_text, "全部笔记改动", backdrop,
                    { openReviews(null) }, Modifier.size(48.dp).testTag("agent-reviews"))
                Spacer(Modifier.weight(1f))
                EditorGlassIconButton(R.drawable.ic_keyline_stroke_square_pen, "开始新话题", backdrop,
                    { action { timeline.newTopic() } }, Modifier.size(48.dp).testTag("agent-new-topic"),
                    enabled = state.ready && !state.running && !unresolved && queue.isEmpty())
                EditorGlassIconButton(R.drawable.ic_keyline_stroke_more_horizontal, "更多", backdrop,
                    { moreMenu = true }, Modifier.size(48.dp).xNotePopupAnchor(moreAnchor).testTag("agent-more"))
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("agent-timeline"), state = list,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (messages.isEmpty() && state.ready && !keyboardVisible) item(key = "welcome") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("想对笔记做些什么？", style = MaterialTheme.typography.headlineSmall)
                        Text("附加笔记，开始总结、整理或继续你的想法。", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(messages.filter { message -> queue.none { it.messageId == message.id } && !(message.role == AgentMessageRole.Event && message.text == "模型请求") }, key = { it.sequence }) { message ->
                    val run = runs.find { it.id == message.runId }
                    AgentMessageSurface(message.role) {
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
                            if (message.role == AgentMessageRole.Tool) tools.filter { tool -> tool.runId == message.runId && message.modelJson?.let { Json.decodeFromString<ModelMessage>(it).results.any { result -> result.id == tool.callId } } == true }.forEach { tool ->
                                if (tool.name in setOf("create", "delete") && tool.status == AgentToolStatus.Committed) {
                                    val noteId = tool.resultJson?.let { Json.parseToJsonElement(it).jsonObject["note_id"]?.jsonPrimitive?.content }
                                    if (noteId != null) {
                                        val note = (availableNotes + trashedNotes).find { it.id == noteId }
                                        Text(when { note == null -> "笔记已永久删除 · 无法恢复"
                                            tool.name == "create" -> "已新建 · ${note.title.ifBlank { "未命名笔记" }}"
                                            note.deletedAtEpochMs != null -> "已移入回收站 · ${note.title.ifBlank { "未命名笔记" }}"
                                            else -> "笔记已恢复 · ${note.title.ifBlank { "未命名笔记" }}" }, Modifier.testTag("agent-note-receipt-$noteId"))
                                        LiquidButton({ openReviews(noteId) }, backdrop, modifier = Modifier.testTag("agent-review-receipt-$noteId")) { Text("查看单篇改动") }
                                    }
                                }
                                LiquidButton({ toolPreview = tool }, backdrop) { Text("${tool.name} · ${tool.status.toolStatusLabel()}") }
                            }
                            if (message.role == AgentMessageRole.Assistant) {
                                if (message.status != AgentMessageStatus.Complete) Text(when (message.status) {
                                    AgentMessageStatus.Complete -> if (message.modelJson != null) "工具调用已记录" else "已完成"
                                    AgentMessageStatus.Streaming, AgentMessageStatus.Pending -> "生成中"
                                    AgentMessageStatus.Failed -> "失败 · 已保留内容"
                                    AgentMessageStatus.Cancelled -> "已停止"
                                    AgentMessageStatus.Interrupted -> if (run?.status == AgentRunStatus.PausedBudget) "达到容量上限" else "已中断"
                                }, style = MaterialTheme.typography.bodySmall)
                                if (!state.running && !unresolved && run?.errorCode != "history_removed" && run?.status in setOf(AgentRunStatus.Failed, AgentRunStatus.Cancelled)) {
                                    LiquidButton({ action { timeline.continueRun(checkNotNull(run).id) } }, backdrop) { Text("继续此任务") }
                                }
                            }
                        AgentMessageActions(
                            message = message, run = run,
                            canDelete = !state.running && !unresolved && message.role != AgentMessageRole.Event,
                            onDelete = { action { timeline.deleteMessage(message.id) } },
                        )
                    }
                }
                if (!state.ready) item(key = "restoring") { Text("正在恢复对话…") }
                (error ?: state.notice.takeIf { waitingConflict == null })?.let { notice ->
                    item(key = "notice") { Text(notice, Modifier.testTag("agent-notice"), style = MaterialTheme.typography.bodySmall) }
                }
                waitingConflict?.let { run ->
                    val conflict = tools.firstOrNull { it.runId == run.id && it.status == AgentToolStatus.Requested }
                    if (conflict != null) item(key = "conflict") {
                        XNoteGroupCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(state.notice ?: "笔记内容发生变化，任务已暂停。", Modifier.testTag("agent-notice"))
                                LiquidButton({ action { timeline.replanConflict(run.id, conflict.callId) } }, backdrop,
                                    enabled = !state.running, modifier = Modifier.testTag("agent-replan-conflict")) { Text("重新读取并调整") }
                            }
                        }
                    }
                }
                val request = tools.firstOrNull { tool -> tool.status == AgentToolStatus.Requested &&
                    runs.any { it.id == tool.runId && it.status == AgentRunStatus.WaitingPermission } }
                if (request != null) item(key = "authorization") {
                    XNoteGroupCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("此任务需要你的授权", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton({ toolPreview = request }) { Text("查看请求") }
                                LiquidButton({ permissionRequest = request }, backdrop, enabled = !state.running,
                                    modifier = Modifier.testTag("agent-authorize")) { Text(if (request.name == "create") "选择归属并授权创建" else "授权本次操作") }
                                TextButton({ action { timeline.answerPermission(request.runId, request.callId, null) } }, enabled = !state.running) { Text("拒绝") }
                            }
                        }
                    }
                }
            }
            if (state.running || unresolved || queue.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.running) "正在执行" else if (unresolved) "任务已暂停" else "队列已暂停",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (queue.isNotEmpty()) TextButton({ keyboard?.hide(); queueDrawer = true }) { Text("待执行 ${queue.size} 条") }
                    if (state.running) TextButton(timeline::stop, Modifier.testTag("agent-stop")) { Text("停止") }
                    else if (unresolved) {
                        val recoverable = runs.lastOrNull { it.status in setOf(AgentRunStatus.Interrupted, AgentRunStatus.PausedBudget) }
                        if (recoverable != null) TextButton({ action { timeline.continueRun(recoverable.id) } }, Modifier.testTag("agent-continue")) { Text("继续任务") }
                        TextButton({ action { timeline.finishUnresolved() } }) { Text("结束任务") }
                    }
                }
            }
            AgentComposer(
                input = input, onInputChange = { value -> input = value; action { timeline.saveDraft(value) } },
                enabled = state.ready && restored, running = state.running,
                canSend = state.ready && restored && (state.running || (!unresolved && queue.isEmpty())) && input.isNotBlank(),
                permission = permission, notes = selectedNotes.map { id -> id to
                    (availableNotes.find { it.id == id }?.title?.ifBlank { "未命名笔记" } ?: "笔记不可用") },
                onRemoveNote = { id -> action { timeline.selectDraftNotes(selectedNotes - id) } },
                onPreviewNote = { draftPreviewId = it },
                attachmentAnchor = attachmentAnchor,
                onAdd = { attachmentMenu = true }, onPermission = { permissionDialog = true },
                onSend = { val sent = input; action { timeline.send(sent); input = "" } },
            )
        }
        XNoteDropdownMenu(attachmentMenu, { attachmentMenu = false },
            listOf(XNoteDropdownMenuItem("笔记", { keyboard?.hide(); attachDialog = true })), backdrop,
            anchor = attachmentAnchor, placement = XNotePopupPlacement.BelowStart)
        XNoteDropdownMenu(moreMenu, { moreMenu = false }, listOf(
            XNoteDropdownMenuItem("任务队列 · ${queue.size}", { keyboard?.hide(); queueDrawer = true }),
            XNoteDropdownMenuItem("模型与服务商", { keyboard?.hide(); onOpenModels() }),
            XNoteDropdownMenuItem("开启后台通知", { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }, enabled = !notificationsEnabled),
        ), backdrop, anchor = moreAnchor)
        XNoteDrawer(queueDrawer, { queueDrawer = false }, "任务队列", backdrop, XNoteDrawerPlacement.Bottom,
            Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
            Text("队列中的消息会依次执行；运行中可在输入框补充当前任务。")
            if (queue.isEmpty()) Text("暂无待执行任务")
            if (input.isNotBlank()) LiquidButton({ val sent = input; action { timeline.enqueue(sent); input = "" } }, backdrop,
                enabled = state.ready && restored, modifier = Modifier.testTag("agent-enqueue")) { Text("将输入内容加入队列") }
            if (queue.isNotEmpty() && !state.running && !unresolved) LiquidButton({ action { timeline.resumeQueue(); queueDrawer = false } },
                backdrop, modifier = Modifier.testTag("agent-resume-queue")) { Text("继续队列") }
            queue.forEach { queued ->
                messages.find { it.id == queued.messageId }?.let { message ->
                    AgentQueueCard(queued.id, message.text, queued.status == AgentQueueStatus.Paused, backdrop,
                        onSave = { value -> action { timeline.editQueued(queued.id, value) } },
                        onMove = { direction -> action { timeline.moveQueued(queued.id, direction) } },
                        onRemove = { action { timeline.removeQueued(queued.id) } })
                }
            }
            TextButton({ queueDrawer = false }) { Text("关闭") }
        }
        AgentReviewDrawer(reviewsOpen, timeline, library, backdrop, reviewNoteId) {
            input = timeline.draft.value
            reviewsOpen = false
        }
        if (draftPreviewId != null) {
            val note = availableNotes.find { it.id == draftPreviewId }
            XNoteDialog(true, { draftPreviewId = null }, note?.title?.ifBlank { "未命名笔记" } ?: "笔记不可用", backdrop,
                XNoteDialogAction("关闭", { draftPreviewId = null })) {
                Text(note?.let { extractPlainText(decodeNoteDocument(it.documentJson)) } ?: "该笔记已删除或不再可用，可从输入框移除。",
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
            }
        }
        if (attachDialog) AgentAttachNotesDialog(availableNotes, selectedNotes, backdrop, { attachDialog = false }) { ids ->
            action { timeline.selectDraftNotes(ids); attachDialog = false }
        }
        if (permissionRequest?.name == "create") AgentCreationDialog(notebooks, backdrop, onDismiss = { permissionRequest = null }) { target ->
            val request = requireNotNull(permissionRequest)
            action { timeline.answerCreation(request.runId, request.callId, target); permissionRequest = null }
        }
        if (permissionDialog || (permissionRequest != null && permissionRequest?.name != "create")) AgentPermissionDialog(permission, notebooks, backdrop, permissionRequest != null,
            requiredLevel = if (permissionRequest?.name in setOf("write", "delete")) AgentPermissionLevel.Edit else AgentPermissionLevel.Read,
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
