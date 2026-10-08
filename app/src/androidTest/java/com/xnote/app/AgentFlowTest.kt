package com.xnote.app

import android.content.Context
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.db.AgentMessageEntity
import com.xnote.app.data.db.AgentSegmentEntity
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.model.SystemEpochClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull

// -- Tests

class AgentFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = XNoteDatabase.createInMemory(context)
    private val profiles = ModelProfileStore(database, AndroidModelCredentialStore(context))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = object : ModelClient {
        override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
            emit(ModelEvent.Text("这是一条逐步保存的回复。"))
            delay(100)
            emit(ModelEvent.Text("你可以继续发送消息，也可以开始新会话。"))
            emit(ModelEvent.Usage(12, 24))
            emitAgentFinish("这是一条逐步保存的回复。\n你可以继续发送消息，也可以开始新会话。")
        }
    }
    private val timeline = AgentTimeline(database, profiles, client, scope)
    private val library = NoteLibrary(database, AttachmentFileStore(File(context.cacheDir, "agent-flow")), SystemEpochClock)

    @After fun cleanup() = runBlocking {
        scope.coroutineContext[Job]?.cancelAndJoin()
        database.agent().unfinishedRuns().forEach { database.agent().saveRun(it.copy(status = AgentRunStatus.Cancelled)) }
        database.agent().pendingQueue().forEach { database.agent().deleteQueueItem(it.id) }
        profiles.list().forEach { profiles.delete(it.id) }
        database.close()
    }

    @Test fun timelineStartsAtLatestRemembersReadingPositionAndReturnsToBottom() {
        runBlocking {
            timeline.awaitReady()
            database.agent().saveSegment(AgentSegmentEntity("layout", 1))
            repeat(40) { index ->
                database.agent().insertMessage(AgentMessageEntity(
                    id = "history-$index", segmentId = "layout", runId = null,
                    role = if (index % 2 == 0) AgentMessageRole.User else AgentMessageRole.Assistant,
                    text = "历史消息 $index", status = AgentMessageStatus.Complete, createdAtEpochMs = index.toLong(),
                ))
            }
            timeline.openConversation("layout")
        }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, agentTimeline = timeline) } }
        compose.onNodeWithContentDescription("Agent").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("agent-message-history-39").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("agent-message-history-39").assertIsDisplayed()
        compose.onNodeWithTag("agent-scroll-to-bottom").assertDoesNotExist()
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("历史消息 20"))
        val readingBounds = compose.onNodeWithTag("agent-message-history-20").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("agent-scroll-to-bottom").assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(46f))
        compose.onNodeWithTag("agent-scroll-arrow").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("Agent").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("agent-message-history-20").fetchSemanticsNodes().isNotEmpty() }
        val restoredBounds = compose.onNodeWithTag("agent-message-history-20").fetchSemanticsNode().boundsInRoot
        assertEquals(readingBounds.top, restoredBounds.top, 1f)
        compose.onNodeWithTag("agent-scroll-to-bottom").performClick()
        compose.onNodeWithTag("agent-message-history-39").assertIsDisplayed()
        compose.onNodeWithTag("agent-scroll-to-bottom").assertDoesNotExist()
    }

    @Test fun composerSendsSupplementAtNextRequestAndStopsShortRunningConversation() {
        runBlocking {
            timeline.awaitReady()
            profiles.save(ModelProfile("composer", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "local-test")
        }
        val release = CompletableDeferred<Unit>()
        val requests = java.util.concurrent.CopyOnWriteArrayList<ModelRequest>()
        val model = object : ModelClient {
            override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
                requests += request
                if (requests.size == 1) {
                    emit(ModelEvent.Text("你好。"))
                    release.await()
                    emitAgentFinish("已处理")
                } else {
                    emit(ModelEvent.Text("收到补充。"))
                    awaitCancellation()
                }
            }
        }
        val running = AgentTimeline(database, profiles, model, scope)
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, agentTimeline = running) } }
        configureWindow()
        compose.onNodeWithContentDescription("Agent").performClick()
        compose.waitUntil(5000) { running.state.value.ready }
        compose.onNodeWithTag("agent-input").performTextInput("你好")
        compose.onNodeWithTag("agent-send").assertIsEnabled().performClick()
        compose.waitUntil(5000) { requests.size == 1 }
        hideKeyboard()
        compose.onNodeWithTag("agent-stop").assertIsEnabled().assertContentDescriptionEquals("停止任务")
        compose.onNodeWithTag("agent-send").assertDoesNotExist()
        compose.onNodeWithText("正在执行").assertDoesNotExist()
        compose.onNodeWithTag("agent-scroll-to-bottom").assertDoesNotExist()

        compose.onNodeWithTag("agent-input").performTextInput("请继续")
        compose.onNodeWithTag("agent-send").assertIsEnabled().assertContentDescriptionEquals("补充当前任务")
        compose.onNodeWithTag("agent-stop").assertDoesNotExist()
        compose.onNodeWithTag("agent-input").performTextClearance()
        compose.onNodeWithTag("agent-stop").assertIsEnabled()
        compose.onNodeWithTag("agent-input").performTextInput("请继续")
        compose.onNodeWithTag("agent-send").performClick()
        compose.waitUntil(5000) { runBlocking { database.agent().messages().any { it.text == "请继续" } } }
        hideKeyboard()
        compose.onNodeWithText("请继续").assertIsDisplayed()
        compose.onNodeWithTag("agent-stop").assertIsEnabled()
        compose.onNodeWithTag("agent-scroll-to-bottom").assertDoesNotExist()
        assertEquals(1, requests.size)
        runBlocking { assertEquals(AgentMessageStatus.Pending, database.agent().messages().single { it.text == "请继续" }.status) }

        release.complete(Unit)
        compose.waitUntil(5000) { requests.size == 2 }
        assertTrue(requests.last().messages.any { it.role == AgentMessageRole.User && it.text.contains("请继续") })
        compose.onNodeWithTag("agent-stop").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { !running.state.value.running }
        compose.onNodeWithTag("agent-stop").assertDoesNotExist()
        compose.onNodeWithTag("agent-send").assertIsNotEnabled()
        runBlocking {
            assertEquals(AgentRunStatus.Cancelled, database.agent().run(database.agent().messages().first().runId!!)!!.status)
            assertEquals(1, database.agent().messages().count { it.text == "请继续" })
        }
        assertEquals(2, requests.size)
    }

    @Test fun newConversationClearsMessagesAndReturningDoesNotReplayToast() {
        runBlocking { profiles.save(ModelProfile("test", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "test-key") }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = client, agentTimeline = timeline) } }
        configureWindow()
        compose.onNodeWithContentDescription("Agent").performClick()
        compose.waitUntil(5000) { timeline.state.value.ready }
        compose.onNodeWithTag("agent-input").performTextInput("帮我整理今天的想法")
        screenshot("agent-before-send")
        compose.onNodeWithTag("agent-send").assertIsEnabled().performClick()
        try {
            compose.waitUntil(5000) { !timeline.state.value.running && runBlocking {
                database.agent().messages().any { it.role == AgentMessageRole.Assistant && it.status == AgentMessageStatus.Complete }
            } }
        } catch (error: Throwable) {
            screenshot("agent-send-failure")
            throw AssertionError("状态=${timeline.state.value}; 消息=${runBlocking { database.agent().messages().map { it.status } }}", error)
        }
        compose.onNodeWithTag("agent-input").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        hideKeyboard()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("这是一条逐步保存的回复。", substring = true))
        compose.onNodeWithText("这是一条逐步保存的回复。", substring = true).assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("Agent").performClick()
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("帮我整理今天的想法"))
        compose.onNodeWithText("帮我整理今天的想法").assertExists()
        compose.onNodeWithText("帮我整理今天的想法").performTouchInput { longClick() }
        compose.onNodeWithTag("agent-message-menu").assertIsDisplayed()
        compose.onNodeWithTag("xnote-bottom-navigation").assertDoesNotExist()
        compose.onNodeWithTag("agent-copy-message").performClick()
        compose.runOnIdle {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals("帮我整理今天的想法", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        screenshot("agent-timeline")
        val conversation = timeline.conversationId.value
        val run = runBlocking { database.agent().observeRuns().first().single() }
        compose.onNodeWithTag("agent-task-process-${run.id}").assertDoesNotExist()
        compose.onNodeWithTag("agent-final-${run.id}-0").assertExists()
        compose.onNodeWithTag("agent-final-${run.id}-1").assertExists()
        val divider = compose.onNodeWithTag("agent-task-divider-${run.id}").fetchSemanticsNode().boundsInRoot
        val task = compose.onNodeWithTag("agent-task-${run.id}").fetchSemanticsNode().boundsInRoot
        assertEquals(task.width, divider.width, 1f)
        compose.onNodeWithContentDescription("新会话").performClick()
        compose.waitUntil(5000) { timeline.conversationId.value != conversation && timeline.state.value.notice == null }
        compose.onNodeWithText("想对笔记做些什么？").assertIsDisplayed()
        compose.onNodeWithText("帮我整理今天的想法").assertDoesNotExist()
        compose.onNodeWithText("新会话已开始").assertIsDisplayed()
        compose.onNodeWithTag("agent-notice").assertDoesNotExist()
        compose.waitUntil(7000) { compose.onAllNodesWithText("新会话已开始").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("Agent").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("新会话已开始").assertDoesNotExist()
        assertNull(timeline.state.value.notice)
        compose.onNodeWithContentDescription("历史记录").performClick()
        compose.onNodeWithTag("agent-history-popup").assertIsDisplayed()
        compose.onNodeWithTag("agent-history-$conversation").performClick()
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("帮我整理今天的想法"))
        compose.onNodeWithText("帮我整理今天的想法").assertExists()
    }

    @Test fun attachSnapshotAndChangePermissionModeThroughUi() {
        val note = runBlocking {
            profiles.save(ModelProfile("test", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "test-key")
            val created = library.createNote(null)
            library.saveNote(requireNotNull(library.getNote(created.id)).copy(title = "测试笔记", document = com.xnote.app.domain.document.NoteDocument(blocks = listOf(
                com.xnote.app.domain.document.TextBlock("body", inlines = listOf(com.xnote.app.domain.document.InlineRun("发送时正文"))),
            ))))
        }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = client, agentTimeline = timeline) } }
        compose.onNodeWithContentDescription("Agent").performClick()
        compose.waitUntil(5000) { timeline.state.value.ready }
        compose.onNodeWithTag("agent-add-attachment").performClick()
        compose.onNodeWithTag("xnote-bottom-navigation").assertDoesNotExist()
        val attachmentBounds = compose.onNodeWithTag("agent-attachment-menu").fetchSemanticsNode().boundsInRoot
        val attachmentComposer = compose.onNodeWithTag("agent-composer").fetchSemanticsNode().boundsInRoot
        assertTrue(attachmentBounds.bottom <= attachmentComposer.bottom)
        compose.onNode(hasText("笔记") and hasAnyAncestor(hasTestTag("agent-attachment-menu"))).performClick()
        compose.onNodeWithTag("agent-attach-${note.id}").performClick()
        compose.onNodeWithText("添加 1 篇").performClick()
        compose.waitUntil(5000) { timeline.draftNotes.value == listOf(note.id) }
        compose.onNodeWithTag("agent-input").performTextInput("总结这篇笔记")
        compose.onNodeWithTag("agent-send").performClick()
        compose.waitUntil(5000) { !timeline.state.value.running && runBlocking { database.agent().messages().any { it.role == AgentMessageRole.Assistant && it.status == AgentMessageStatus.Complete } } }
        hideKeyboard()
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("发送时的笔记 · 测试笔记"))
        compose.onNodeWithText("发送时的笔记 · 测试笔记").performClick()
        compose.onNodeWithTag("agent-snapshot-body").assertTextContains("发送时正文")
        screenshot("agent-snapshot-preview")
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("agent-permission-settings").performClick()
        compose.onNodeWithTag("xnote-bottom-navigation").assertDoesNotExist()
        val menuBounds = compose.onNodeWithTag("agent-permission-menu").fetchSemanticsNode().boundsInRoot
        val permissionComposer = compose.onNodeWithTag("agent-composer").fetchSemanticsNode().boundsInRoot
        assertTrue(menuBounds.bottom <= permissionComposer.bottom)
        compose.onNodeWithText("完全隐私").assertExists()
        compose.onAllNodesWithText("请求批准").onLast().assertExists()
        screenshot("agent-permission-modes")
        compose.onNodeWithText("完全访问").performClick()
        compose.waitUntil(5000) { runBlocking { AgentPermissionStore(database).current().let { it.mode == AgentPermissionMode.FullAccess } } }
    }

    @Test fun composerMenusAndReviewDrawerKeepTheDraftAndAttachment() {
        val note = runBlocking {
            timeline.awaitReady()
            profiles.save(ModelProfile("layout", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "test-key")
            val created = library.createNote(null)
            library.saveNote(requireNotNull(library.getNote(created.id)).copy(title = "布局测试笔记"))
        }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = client, agentTimeline = timeline) } }
        configureWindow()
        val bottomAction = compose.onNodeWithTag("xnote-open-agent").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Agent").performClick()
        compose.waitUntil(5000) { timeline.state.value.ready }
        compose.onNodeWithTag("agent-clear").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索").assertDoesNotExist()
        compose.onNodeWithTag("agent-header").assertIsDisplayed()
        compose.onNodeWithTag("agent-send").assertIsNotEnabled()
        val header = compose.onNodeWithTag("agent-header").fetchSemanticsNode().boundsInRoot
        val topic = compose.onNodeWithContentDescription("新会话").fetchSemanticsNode().boundsInRoot
        val more = compose.onNodeWithContentDescription("更多").fetchSemanticsNode().boundsInRoot
        assertTrue(topic.right <= more.left)
        compose.onNodeWithContentDescription("新会话").assertWidthIsEqualTo(com.xnote.app.design.XNoteButtonSize)
        compose.onNodeWithContentDescription("更多").assertHeightIsEqualTo(com.xnote.app.design.XNoteButtonSize)
        assertTrue(header.contains(topic.center) && header.contains(more.center))
        val composer = compose.onNodeWithTag("agent-composer").fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithTag("agent-add-attachment").fetchSemanticsNode().boundsInRoot
        val permission = compose.onNodeWithTag("agent-permission-settings").fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithTag("agent-send").fetchSemanticsNode().boundsInRoot
        assertTrue(composer.contains(add.center) && composer.contains(permission.center) && composer.contains(send.center))
        assertTrue(add.right <= permission.left && permission.right <= send.left)
        assertTrue(permission.width < composer.width / 2)
        val composerBeforeKeyboard = compose.onNodeWithTag("agent-composer").fetchSemanticsNode().boundsInRoot
        assertEquals(bottomAction.bottom, composerBeforeKeyboard.bottom, 1f)
        val back = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        val history = compose.onNodeWithContentDescription("历史记录").fetchSemanticsNode().boundsInRoot
        assertTrue(back.right <= history.left)
        compose.onNodeWithTag("xnote-bottom-navigation").assertDoesNotExist()
        screenshot("agent-empty-layout")
        compose.onNodeWithTag("agent-add-attachment").performClick()
        compose.onNode(hasText("笔记") and hasAnyAncestor(hasTestTag("agent-attachment-menu"))).assertIsDisplayed()
        compose.onNodeWithText("图片").assertExists()
        screenshot("agent-attachment-menu")
        compose.onNode(hasText("笔记") and hasAnyAncestor(hasTestTag("agent-attachment-menu"))).performClick()
        compose.onNodeWithTag("agent-attach-${note.id}").performClick()
        compose.onNodeWithText("添加 1 篇").performClick()
        compose.waitUntil(5000) { timeline.draftNotes.value == listOf(note.id) }
        compose.onNodeWithTag("agent-draft-note-${note.id}").performClick()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("agent-input").performClick().performTextInput("保留这段草稿")
        compose.waitUntil(5000) { timeline.draft.value == "保留这段草稿" }
        compose.waitUntil(5000) {
            compose.onNodeWithTag("agent-composer").fetchSemanticsNode().boundsInRoot.bottom < composerBeforeKeyboard.bottom - 100f
        }
        compose.onNodeWithTag("xnote-bottom-navigation").assertDoesNotExist()
        assertTrue(compose.onNodeWithTag("agent-composer").fetchSemanticsNode().boundsInRoot.bottom < composerBeforeKeyboard.bottom)
        compose.onNodeWithTag("agent-add-attachment").performClick()
        compose.onNode(hasText("笔记") and hasAnyAncestor(hasTestTag("agent-attachment-menu"))).assertIsDisplayed()
        screenshot("agent-keyboard-attachment-menu")
        compose.onNode(hasText("笔记") and hasAnyAncestor(hasTestTag("agent-attachment-menu"))).performClick()
        compose.onNodeWithText("取消").performClick()
        hideKeyboard()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("全部笔记改动").performClick()
        compose.onNodeWithText("暂无笔记改动").assertIsDisplayed()
        compose.onNodeWithText("我的").assertDoesNotExist()
        screenshot("agent-review-popup")
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("agent-input").assertTextContains("保留这段草稿")
        compose.onNodeWithTag("agent-draft-note-${note.id}").assertIsDisplayed()
        compose.onNodeWithTag("agent-remove-note-${note.id}").performClick()
        compose.waitUntil(5000) { timeline.draftNotes.value.isEmpty() }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("任务队列 · 0").assertIsDisplayed()
        compose.onNodeWithText("模型与服务商").assertIsDisplayed()
        compose.onNodeWithText("清空聊天").assertDoesNotExist()
        compose.onNodeWithText("任务队列 · 0").performClick()
        compose.onNodeWithTag("agent-enqueue").performClick()
        compose.waitUntil(5000) { runBlocking { database.agent().pendingQueue().size == 1 } }
        compose.onNodeWithText("保留这段草稿").assertIsDisplayed()
        screenshot("agent-queue-popup")
        compose.onNodeWithText("编辑").performClick()
        compose.onNode(hasSetTextAction() and hasText("保留这段草稿")).performTextReplacement("调整后的任务")
        hideKeyboard()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000) { runBlocking { database.agent().messages().any { it.text == "调整后的任务" } } }
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(5000) { runBlocking { database.agent().pendingQueue().isEmpty() } }
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("agent-input").assertTextContains("")
        compose.onNodeWithTag("agent-input").performTextInput("返回前保留的草稿")
        hideKeyboard()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("xnote-bottom-navigation").assertIsDisplayed()
        compose.onNodeWithContentDescription("Agent").performClick()
        compose.onNodeWithTag("agent-input").assertTextContains("返回前保留的草稿")
    }

    @Test fun authorizeInspectDecisionAndContinueFailedTaskWithoutReplayingRead() {
        val note = runBlocking {
            timeline.awaitReady()
            profiles.save(ModelProfile("test", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "test-key")
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(true, true, 1))
            library.createNote(null)
        }
        var requests = 0
        val recoveringModel = object : ModelClient {
            override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
                when (++requests) {
                    1 -> {
                        emit(ModelEvent.ToolCall(ModelToolCall("inspect-once", "read", buildJsonObject { put("note_id", note.id) })))
                        emit(ModelEvent.Finished(ModelFinish.ToolCalls))
                    }
                    2 -> { emit(ModelEvent.Text("已经读取，正在整理")); throw ModelException(ModelError.Quota) }
                    else -> { emitAgentFinish("继续任务已完成") }
                }
            }
        }
        val recovery = AgentTimeline(database, profiles, recoveringModel, scope)
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = recoveringModel, agentTimeline = recovery) } }
        compose.onNodeWithContentDescription("Agent").performClick()
        compose.waitUntil(5000) { recovery.state.value.ready }
        compose.onNodeWithTag("agent-input").performTextInput("读取并整理")
        compose.onNodeWithTag("agent-send").performClick()
        compose.waitUntil(5000) { !recovery.state.value.running && runBlocking { database.agent().unfinishedRuns().any { it.status == AgentRunStatus.WaitingPermission } } }
        hideKeyboard()
        compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("agent-authorize") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("agent-authorize").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("查看完整请求").performClick()
        compose.onNodeWithTag("agent-approval-arguments").assertTextContains(note.id, substring = true)
        screenshot("agent-call-approval")
        compose.onNodeWithText("允许本次").performClick()
        compose.waitUntil(5000) { !recovery.state.value.running && runBlocking { database.agent().messages().any { it.status == AgentMessageStatus.Failed } } }
        val runId = runBlocking { database.agent().messages().first().runId!! }
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasTestTag("agent-task-toggle-$runId"))
        compose.onNodeWithTag("agent-task-toggle-$runId").performClick()
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("读取笔记", substring = true))
        compose.onNodeWithText("读取笔记", substring = true).performClick()
        compose.onNodeWithTag("xnote-bottom-navigation").assertDoesNotExist()
        compose.onNodeWithText("调用参数").assertExists()
        compose.onNodeWithText("执行结果").assertExists()
        compose.onAllNodesWithText("当时的权限：请求批准")[0].performScrollTo().assertIsDisplayed()
        screenshot("agent-tool-decision")
        compose.onNodeWithTag("agent-tool-continue").performScrollTo().performClick()
        compose.waitUntil(5000) { !recovery.state.value.running && runBlocking { database.agent().messages().any { it.text == "继续任务已完成" } } }
        compose.onNodeWithTag("xnote-bottom-navigation").assertDoesNotExist()
        runBlocking {
            assertEquals(1, database.agent().toolEvents(database.agent().messages().first().runId!!).count { it.name != AgentFinishToolName })
            assertEquals(AgentPermission(), AgentPermissionStore(database).current())
            assertEquals(3, requests)
            assertEquals(AgentRunStatus.Complete, database.agent().run(database.agent().messages().first().runId!!)!!.status)
        }
    }

    // -- Functions

    @Test fun toolDetailsCanStopTheOwningRunWithoutUndoingCommittedRead() {
        val note = runBlocking {
            timeline.awaitReady()
            profiles.save(ModelProfile("stop-tool", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "local-test")
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(true, true, 1))
            AgentPermissionStore(database).saveFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            library.createNote(null)
        }
        var requests = 0
        val model = object : ModelClient {
            override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
                if (++requests == 1) {
                    emit(ModelEvent.ToolCall(ModelToolCall("read", "read", buildJsonObject { put("note_id", note.id) })))
                    emit(ModelEvent.Finished(ModelFinish.ToolCalls))
                } else awaitCancellation()
            }
        }
        val running = AgentTimeline(database, profiles, model, scope)
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, agentTimeline = running) } }
        compose.onNodeWithContentDescription("Agent").performClick()
        runBlocking { running.send("读取后继续整理") }
        compose.waitUntil(5000) { requests == 2 }
        compose.onNodeWithTag("agent-scroll-to-bottom").assertDoesNotExist()
        compose.onNodeWithTag("agent-running-dots").assertDoesNotExist()
        compose.onNodeWithTag("agent-scroll-arrow").assertDoesNotExist()
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("读取笔记", substring = true))
        compose.onNodeWithText("读取笔记", substring = true).performClick()
        compose.onNodeWithTag("agent-tool-stop").performScrollTo().performClick()
        compose.waitUntil(5000) { !running.state.value.running }
        runBlocking {
            val run = database.agent().run(database.agent().messages().first().runId!!)!!
            assertEquals(AgentRunStatus.Cancelled, run.status)
            assertEquals(AgentToolStatus.Committed, database.agent().toolEvents(run.id).single().status)
        }
    }

    private fun configureWindow() {
        // Match MainActivity's resize policy; the generic Compose host otherwise pans the window.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).forEach {
                    if (it is androidx.activity.ComponentActivity) it.enableEdgeToEdge()
                    it.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
        }
    }

    private fun hideKeyboard() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).forEach {
                    it.window.insetsController?.hide(android.view.WindowInsets.Type.ime())
                }
        }
    }

    private fun screenshot(name: String) {
        val directory = File(context.getExternalFilesDir(null), "s11-screenshots").apply { mkdirs() }
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
