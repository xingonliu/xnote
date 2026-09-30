package com.xnote.app.feature.agent

import android.Manifest
import android.app.NotificationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.data.agent.AgentTimeline
import com.xnote.app.data.agent.agentVersion
import com.xnote.app.domain.document.plainText
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
import com.xnote.app.design.XNoteButton
import com.xnote.app.domain.agent.*
import com.xnote.app.data.db.AgentSnapshotEntity
import com.xnote.app.data.db.AgentToolEventEntity
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

// -- Functions

@Composable
fun AgentScreen(timeline: AgentTimeline, library: NoteLibrary, contentPadding: PaddingValues, bottomInset: Dp, toastHostState: com.xnote.app.design.XNoteToastState, modifier: Modifier = Modifier,
    onModalVisible: (Boolean) -> Unit = {}, onOpenModels: () -> Unit) {
    // -- State

    val messages by timeline.messages.collectAsState(emptyList())
    val runs by timeline.runs.collectAsState(emptyList())
    val queue by timeline.queue.collectAsState(emptyList())
    val selectedNotes by timeline.draftNotes.collectAsState()
    val draftSelection by timeline.draftSelection.collectAsState()
    val availableNotes by timeline.noteStore.availableNotes.collectAsState(emptyList())
    val trashedNotes by timeline.noteStore.trashedNotes.collectAsState(emptyList())
    val notebooks by timeline.noteStore.notebooks.collectAsState(emptyList())
    val permission by timeline.noteStore.permission.collectAsState(AgentPermission())
    val snapshots by timeline.noteStore.snapshots.collectAsState(emptyList())
    val tools by timeline.noteStore.toolEvents.collectAsState(emptyList())
    val fileCards by (timeline.fileStore?.cards ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(emptyList())
    var filePreview by remember { mutableStateOf<com.xnote.app.data.db.AgentFileCard?>(null) }
    var importingFile by remember { mutableStateOf(false) }
    var attachDialog by remember { mutableStateOf(false) }
    var permissionMenu by remember { mutableStateOf(false) }
    var messageMenu by remember { mutableStateOf<AgentMessageMenu?>(null) }
    var composerHeight by remember { mutableStateOf(0.dp) }
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
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
    var attachmentMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var queueDrawer by remember { mutableStateOf(false) }
    var reviewsOpen by remember { mutableStateOf(false) }
    var memoryOpen by remember { mutableStateOf(false) }
    var reviewNoteId by remember { mutableStateOf<String?>(null) }
    var draftPreviewId by remember { mutableStateOf<String?>(null) }
    val attachmentAnchor = rememberXNotePopupAnchor()
    val moreAnchor = rememberXNotePopupAnchor()
    val permissionAnchor = rememberXNotePopupAnchor()
    val keyboard = LocalSoftwareKeyboardController.current
    val direction = LocalLayoutDirection.current
    var restored by remember { mutableStateOf(false) }
    var timelinePositionRestored by remember { mutableStateOf(false) }
    // -- Derived Values

    val modalVisible = attachDialog || permissionRequest != null || snapshotPreview != null || toolPreview != null || queueDrawer || reviewsOpen || draftPreviewId != null || memoryOpen || filePreview != null
    val draftFiles = fileCards.filter { it.ownerType == "draft" }
    val unresolved = runs.any { it.status !in setOf(AgentRunStatus.Complete, AgentRunStatus.Failed, AgentRunStatus.Cancelled) }
    val waitingConflict = runs.firstOrNull { it.status == AgentRunStatus.WaitingConflict }
    val selectionConflict = waitingConflict?.let { run ->
        messages.find { it.id == run.userMessageId }?.let {
            Json.decodeFromString<List<AgentMessageSource>>(it.sourcesJson).any { source -> source.selection != null }
        }
    } == true
    val keyboardInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val keyboardVisible = keyboardInset > 0.dp
    val composerBottom = maxOf(bottomInset, keyboardInset) + 8.dp
    val timelineItems = remember(messages, tools, queue, fileCards) {
        agentTimelineItems(messages, tools, queue.map { it.messageId }.toSet(),
            fileCards.filter { it.ownerType == "message" }.map { it.ownerId }.toSet())
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", locale) }
    // -- Functions

    fun showNotice(message: String) {
        toastHostState.show(message)
    }
    fun action(block: suspend () -> Unit) { scope.launch {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { showNotice(when (failure) {
            is AgentBudgetException, is IllegalArgumentException -> failure.message ?: "操作未完成，请重试。"
            else -> safeModelError(failure)
        }) }
    } }
    fun openReviews(noteId: String?) {
        keyboard?.hide()
        reviewNoteId = noteId
        reviewsOpen = true
    }

    // -- Lifecycle Hooks

    LaunchedEffect(state.notice) { state.notice?.let(::showNotice) }
    LaunchedEffect(waitingConflict?.id, selectionConflict) {
        if (selectionConflict) showNotice("请结束当前任务，再回到笔记编辑器重新选择文字。")
    }
    LaunchedEffect(importingFile) { if (importingFile) showNotice("正在导入文件…") }
    LaunchedEffect(state.running && input.isNotBlank(), draftSelection) {
        if (state.running && input.isNotBlank()) showNotice(
            if (draftSelection != null) "选区润色为独立任务，可从任务队列加入" else "发送后补充当前任务")
    }
    SideEffect { onModalVisible(modalVisible) }
    DisposableEffect(Unit) { onDispose { onModalVisible(false) } }
    LaunchedEffect(state.ready) { if (state.ready && !restored) { input = savedDraft; restored = true } }
    LaunchedEffect(state.ready, timelineItems.size, messages.lastOrNull()?.text, composerHeight, keyboardVisible) {
        if (!state.ready) return@LaunchedEffect
        snapshotFlow { list.layoutInfo.totalItemsCount }.first { it > 0 }
        val lastVisible = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (!list.isScrollInProgress && (!timelinePositionRestored || lastVisible >= list.layoutInfo.totalItemsCount - 3)) {
            list.scrollToItem(list.layoutInfo.totalItemsCount - 1)
            if (messages.isNotEmpty()) timelinePositionRestored = true
        }
    }

    if (memoryOpen) {
        AgentMemoryScreen(timeline, onBack = { memoryOpen = false })
        return
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) action {
            importingFile = true
            try { timeline.importFile(uri) } finally { importingFile = false }
        }
    }
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MaterialTheme.colorScheme.background)) {
            LazyColumn(Modifier.fillMaxSize().testTag("agent-timeline"), state = list,
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(direction),
                    end = contentPadding.calculateEndPadding(direction),
                    top = xNoteScrollEdgePadding(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + XNoteHeaderHeight),
                    bottom = composerBottom + composerHeight + 12.dp,
                )) {
                if (messages.isEmpty() && state.ready && !keyboardVisible) item(key = "welcome") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("想对笔记做些什么？", style = MaterialTheme.typography.headlineSmall)
                        Text("附加笔记，开始总结、整理或继续你的想法。", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                itemsIndexed(timelineItems, key = { _, item -> item.key }) { index, item ->
                    Column(Modifier.fillMaxWidth().padding(top = if (joinsMessageGroup(timelineItems.getOrNull(index - 1), item)) 3.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (item) {
                            is AgentTimelineItem.Tool -> AgentToolHistoryRow(item.event, availableNotes + trashedNotes,
                                onPreview = { toolPreview = item.event }, onOpenReviews = ::openReviews)
                            is AgentTimelineItem.Message -> {
                                val message = item.message
                                val run = runs.find { it.id == message.runId }
                                if (message.role == AgentMessageRole.Event) {
                                    Text(message.text, Modifier.align(Alignment.CenterHorizontally),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (message.text.isNotBlank()) {
                                    AgentMessageBubble(message, message.text,
                                        joinsNext = joinsMessageGroup(item, timelineItems.getOrNull(index + 1)),
                                        onLongPress = { messageMenu = it; attachmentMenu = false; permissionMenu = false; moreMenu = false })
                                }
                                AgentFilesStrip(fileCards.filter { it.ownerType == "message" && it.ownerId == message.id }, { filePreview = it })
                                if (message.role == AgentMessageRole.User) {
                                    Json.decodeFromString<List<AgentMessageSource>>(message.sourcesJson).forEach { source ->
                                        val snapshot = snapshots.find { it.id == source.snapshotId }
                                        XNoteButton({ snapshotPreview = snapshot }, enabled = snapshot != null) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(snapshot?.let { "发送时的笔记 · ${it.title.ifBlank { "未命名笔记" }}" } ?: "笔记已永久删除，无法查看")
                                                snapshot?.let { saved ->
                                                    Text(extractPlainText(decodeNoteDocument(saved.documentJson)).ifBlank { "暂无正文" },
                                                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                                    Text(notebooks.find { it.id == saved.notebookId }?.name ?: if (saved.notebookId == null) "未归档" else "原笔记本已不存在", style = MaterialTheme.typography.labelSmall)
                                                    if (saved.noteUpdatedAtEpochMs > 0) Text("修改于 ${dateFormat.format(java.util.Date(saved.noteUpdatedAtEpochMs))}", style = MaterialTheme.typography.labelSmall)
                                                    if (source.selection != null) Text("仅润色所选文字", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                                if (message.role == AgentMessageRole.User && message.status == AgentMessageStatus.Pending) Text("已收到，将继续处理", style = MaterialTheme.typography.bodySmall)
                                if (message.role == AgentMessageRole.Assistant) {
                                    if (message.status != AgentMessageStatus.Complete) Text(when (message.status) {
                                        AgentMessageStatus.Complete -> if (message.modelJson != null) "工具调用已记录" else "已完成"
                                        AgentMessageStatus.Streaming, AgentMessageStatus.Pending -> "生成中"
                                        AgentMessageStatus.Failed -> "失败 · 已保留内容"
                                        AgentMessageStatus.Cancelled -> "已停止"
                                        AgentMessageStatus.Interrupted -> if (run?.status == AgentRunStatus.PausedBudget) "达到容量上限" else "已中断"
                                    }, style = MaterialTheme.typography.bodySmall)
                                    if (!state.running && !unresolved && run?.errorCode != "history_removed" && run?.status in setOf(AgentRunStatus.Failed, AgentRunStatus.Cancelled)) {
                                        XNoteButton({ action { timeline.continueRun(checkNotNull(run).id) } }) { Text("继续此任务") }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!state.ready) item(key = "restoring") { Text("正在恢复对话…") }
                waitingConflict?.let { run ->
                    val conflict = tools.firstOrNull { it.runId == run.id && it.status == AgentToolStatus.Requested }
                    if (conflict != null && !selectionConflict) item(key = "conflict") {
                        XNoteGroupCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                XNoteButton({ action { timeline.replanConflict(run.id, conflict.callId) } },
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
                                XNoteButton({ permissionRequest = request }, enabled = !state.running,
                                    modifier = Modifier.testTag("agent-authorize")) { Text("查看并允许") }
                                TextButton({ action { timeline.answerPermission(request.runId, request.callId, false) } }, enabled = !state.running) { Text("拒绝") }
                            }
                        }
                    }
                }
            }
        }
        XNoteProgressiveBlur(
            backdrop = backdrop,
            state = rememberXNoteScrollEdgeState(list),
            edges = setOf(XNoteScrollEdge.Top),
        )
        XNoteHeader(
            title = "", backdrop = backdrop,
            actions = listOf(
                XNoteHeaderAction(R.drawable.ic_keyline_stroke_square_pen, "开始新话题", { action { timeline.newTopic() } },
                    enabled = state.ready && !state.running && !unresolved && queue.isEmpty()),
                XNoteHeaderAction(R.drawable.ic_keyline_stroke_more_horizontal, "更多", {
                    moreMenu = true; attachmentMenu = false; permissionMenu = false; messageMenu = null
                }, popupAnchor = moreAnchor),
            ),
            horizontalPadding = contentPadding.calculateEndPadding(direction),
            modifier = Modifier.align(Alignment.TopCenter).testTag("agent-header"),
        )
        Column(Modifier.align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets(bottom = bottomInset))).padding(
            start = contentPadding.calculateStartPadding(direction), end = contentPadding.calculateEndPadding(direction),
            bottom = 8.dp,
        ).onSizeChanged { composerHeight = with(density) { it.height.toDp() } },
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            if (draftFiles.isNotEmpty()) AgentFilesStrip(draftFiles, { filePreview = it }, { id -> action { timeline.removeDraftFile(id) } })
            AgentComposer(
                input = input, onInputChange = { value -> input = value; action { timeline.saveDraft(value) } },
                enabled = state.ready && restored, running = state.running,
                canSend = state.ready && restored && !importingFile && ((state.running && draftSelection == null) || (!state.running && !unresolved && queue.isEmpty())) && (input.isNotBlank() || draftFiles.isNotEmpty()),
                permission = permission, notes = selectedNotes.map { id ->
                    val note = availableNotes.find { it.id == id }
                    AgentComposerNote(id, note?.title?.ifBlank { "未命名笔记" } ?: "笔记不可用",
                        note?.summary?.ifBlank { "暂无正文" } ?: "请移除后重试",
                        notebooks.find { it.id == note?.notebookId }?.name ?: "未归档",
                        note?.let { dateFormat.format(java.util.Date(it.updatedAtEpochMs)) }.orEmpty(),
                        draftSelection?.noteId == id)
                },
                onRemoveNote = { id -> action { timeline.selectDraftNotes(selectedNotes - id) } },
                onPreviewNote = { draftPreviewId = it },
                backdrop = backdrop, attachmentAnchor = attachmentAnchor, permissionAnchor = permissionAnchor,
                onAdd = { attachmentMenu = true; permissionMenu = false; messageMenu = null },
                onPermission = { permissionMenu = true; attachmentMenu = false; messageMenu = null },
                onSend = { val sent = input; action { timeline.send(sent); input = "" } },
            )
        }
        XNoteDropdownMenu(attachmentMenu, { attachmentMenu = false },
            listOf(XNoteDropdownMenuItem("笔记", { keyboard?.hide(); attachDialog = true }),
                XNoteDropdownMenuItem("图片", { keyboard?.hide(); filePicker.launch(arrayOf("image/*")) }, enabled = timeline.fileStore != null && !importingFile),
                XNoteDropdownMenuItem("文件（PDF / TXT / MD）", { keyboard?.hide(); filePicker.launch(arrayOf("*/*")) }, enabled = timeline.fileStore != null && !importingFile)), backdrop,
            anchor = attachmentAnchor, placement = XNotePopupPlacement.AboveStart, bottomInset = bottomInset,
            modifier = Modifier.testTag("agent-attachment-menu"))
        XNoteDropdownMenu(permissionMenu, { permissionMenu = false },
            AgentPermissionMode.entries.map { mode ->
                XNoteDropdownMenuItem(mode.permissionLabel(), { action { timeline.savePermission(permission.copy(mode = mode)) } },
                    selected = permission.mode == mode)
            }, backdrop, anchor = permissionAnchor, placement = XNotePopupPlacement.AboveStart,
            bottomInset = bottomInset, modifier = Modifier.testTag("agent-permission-menu"))
        AgentMessageContextMenu(messageMenu, messageMenu?.let { dateFormat.format(java.util.Date(it.message.createdAtEpochMs)) }.orEmpty(),
            backdrop, bottomInset) { messageMenu = null }
        XNoteDropdownMenu(moreMenu, { moreMenu = false }, listOf(
            XNoteDropdownMenuItem("全部笔记改动", { openReviews(null) }),
            XNoteDropdownMenuItem("任务队列 · ${queue.size}", { keyboard?.hide(); queueDrawer = true }),
            XNoteDropdownMenuItem("模型与服务商", { keyboard?.hide(); onOpenModels() }),
            XNoteDropdownMenuItem("记忆与画像", { keyboard?.hide(); moreMenu = false; memoryOpen = true }),
            XNoteDropdownMenuItem("开启后台通知", { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }, enabled = !notificationsEnabled),
        ), backdrop, anchor = moreAnchor, bottomInset = bottomInset)
        XNoteDrawer(queueDrawer, { queueDrawer = false }, "任务队列", backdrop, XNoteDrawerPlacement.Bottom,
            Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
            Text("队列中的消息会依次执行；运行中可在输入框补充当前任务。")
            if (queue.isEmpty()) Text("暂无待执行任务")
            if (input.isNotBlank() || draftFiles.isNotEmpty()) XNoteButton({ val sent = input; action { timeline.enqueue(sent); input = "" } },
                enabled = state.ready && restored && !importingFile, modifier = Modifier.testTag("agent-enqueue")) { Text("将输入内容加入队列") }
            if (queue.isNotEmpty() && !state.running && !unresolved) XNoteButton({ action { timeline.resumeQueue(); queueDrawer = false } }, modifier = Modifier.testTag("agent-resume-queue")) { Text("继续队列") }
            queue.forEach { queued ->
                messages.find { it.id == queued.messageId }?.let { message ->
                    AgentQueueCard(queued.id, message.text, queued.status == AgentQueueStatus.Paused,
                        onSave = { value -> action { timeline.editQueued(queued.id, value) } },
                        onMove = { direction -> action { timeline.moveQueued(queued.id, direction) } },
                        onRemove = { action { timeline.removeQueued(queued.id) } })
                    AgentFilesStrip(fileCards.filter { it.ownerType == "message" && it.ownerId == message.id }, { filePreview = it })
                }
            }
            TextButton({ queueDrawer = false }) { Text("关闭") }
        }
        AgentReviewDrawer(reviewsOpen, timeline, library, backdrop, ::showNotice, reviewNoteId) {
            input = timeline.draft.value
            reviewsOpen = false
        }
        filePreview?.let { card -> timeline.fileStore?.let { AgentFilePreview(card, it, backdrop, ::showNotice) { filePreview = null } } }
        if (draftPreviewId != null) {
            val note = availableNotes.find { it.id == draftPreviewId }
            XNoteDialog(true, { draftPreviewId = null }, note?.title?.ifBlank { "未命名笔记" } ?: "笔记不可用", backdrop,
                XNoteDialogAction("关闭", { draftPreviewId = null })) {
                draftSelection?.takeIf { it.noteId == note?.id }?.let { selected ->
                    Text("仅润色你选择的文字。", style = MaterialTheme.typography.labelMedium)
                    val selectedText = note?.takeIf { it.agentVersion() == selected.selection.version }
                        ?.let { selectedAgentText(decodeNoteDocument(it.documentJson), selected.selection) }
                    Text(selectedText ?: "笔记已变化，请回到编辑器重新选择。", Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState()))
                }
                Text(note?.let { extractPlainText(decodeNoteDocument(it.documentJson)) } ?: "该笔记已删除或不再可用，可从输入框移除。",
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
            }
        }
        if (attachDialog) AgentAttachNotesDialog(availableNotes, selectedNotes, backdrop, { attachDialog = false }) { ids ->
            action { timeline.selectDraftNotes(ids); attachDialog = false }
        }
        permissionRequest?.let { request ->
            AgentApprovalDialog(request, backdrop, (availableNotes + trashedNotes).associate { it.id to it.title }, notebooks.associate { it.id to it.name }, onDismiss = { permissionRequest = null }) { approved ->
                action { timeline.answerPermission(request.runId, request.callId, approved, request.argumentsJson); permissionRequest = null }
            }
        }
        snapshotPreview?.let { AgentSnapshotDialog(it, backdrop) { snapshotPreview = null } }
        toolPreview?.let { event ->
            val run = runs.find { it.id == event.runId }
            val canContinue = !state.running && !unresolved && run?.errorCode != "history_removed" && run?.status in setOf(AgentRunStatus.Failed, AgentRunStatus.Cancelled)
            AgentToolDialog(event, backdrop, (availableNotes + trashedNotes).associate { it.id to it.title }, notebooks.associate { it.id to it.name },
                onContinue = if (canContinue) ({ action { timeline.continueRun(event.runId); toolPreview = null } }) else null,
                onStop = if (state.running && run?.status == AgentRunStatus.Running) timeline::stop else null,
                onDismiss = { toolPreview = null })
        }

    }
}

@Composable
private fun AgentQueueCard(id: String, text: String, paused: Boolean,
    onSave: (String) -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    var editing by rememberSaveable(id) { mutableStateOf(false) }
    var input by rememberSaveable(id, text) { mutableStateOf(text) }
    XNoteGroupCard(Modifier.fillMaxWidth().testTag("agent-queue-$id")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (paused) "队列已暂停" else "等待执行", style = MaterialTheme.typography.labelMedium)
            if (editing) XNoteTextField(input, { input = it }, singleLine = false) else Text(text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XNoteButton({ if (editing) { onSave(input); editing = false } else editing = true }, enabled = !editing || input.isNotBlank()) { Text(if (editing) "保存" else "编辑") }
                XNoteButton({ onMove(-1) }) { Text("上移") }
                XNoteButton({ onMove(1) }) { Text("下移") }
                XNoteButton(onRemove) { Text("删除") }
            }
        }
    }
}
