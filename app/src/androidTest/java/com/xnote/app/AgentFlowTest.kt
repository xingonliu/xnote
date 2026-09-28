package com.xnote.app

import android.content.Context
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.model.SystemEpochClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

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
            emit(ModelEvent.Text("你可以继续发送消息，也可以开始新话题。"))
            emit(ModelEvent.Usage(12, 24))
            emit(ModelEvent.Finished(ModelFinish.Complete))
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

    @Test fun sendNavigateBackAndStartNewTopic() {
        runBlocking { profiles.save(ModelProfile("test", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "test-key") }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = client, agentTimeline = timeline) } }
        configureWindow()
        compose.onNodeWithText("Agent").performClick()
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
        compose.waitUntil(5000) { compose.onAllNodesWithText("我的").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("这是一条逐步保存的回复。", substring = true))
        compose.onNodeWithText("这是一条逐步保存的回复。", substring = true).assertExists()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("Agent").performClick()
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("帮我整理今天的想法"))
        compose.onNodeWithText("帮我整理今天的想法").assertExists()
        screenshot("agent-timeline")
        compose.onNodeWithTag("agent-new-topic").performClick()
        compose.waitUntil(5000) { runBlocking { database.agent().messages().any { it.role == AgentMessageRole.Event && it.text == "开始新话题" } } }
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("帮我整理今天的想法"))
        compose.onNodeWithText("帮我整理今天的想法").assertExists()
    }

    @Test fun attachSnapshotAndChangePermissionScopeThroughUi() {
        val note = runBlocking {
            profiles.save(ModelProfile("test", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "test-key")
            val created = library.createNote(null)
            library.saveNote(requireNotNull(library.getNote(created.id)).copy(title = "测试笔记", document = com.xnote.app.domain.document.NoteDocument(blocks = listOf(
                com.xnote.app.domain.document.TextBlock("body", inlines = listOf(com.xnote.app.domain.document.InlineRun("发送时正文"))),
            ))))
        }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = client, agentTimeline = timeline) } }
        compose.onNodeWithText("Agent").performClick()
        compose.waitUntil(5000) { timeline.state.value.ready }
        compose.onNodeWithTag("agent-add-attachment").performClick()
        compose.onNodeWithText("笔记").performClick()
        compose.onNodeWithTag("agent-attach-${note.id}").performClick()
        compose.onNodeWithText("确认 1 篇").performClick()
        compose.waitUntil(5000) { timeline.draftNotes.value == listOf(note.id) }
        compose.onNodeWithTag("agent-input").performTextInput("总结这篇笔记")
        compose.onNodeWithTag("agent-send").performClick()
        compose.waitUntil(5000) { !timeline.state.value.running && runBlocking { database.agent().messages().any { it.role == AgentMessageRole.Assistant && it.status == AgentMessageStatus.Complete } } }
        hideKeyboard()
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("发送快照 · 测试笔记"))
        compose.onNodeWithText("发送快照 · 测试笔记").performClick()
        compose.onNodeWithTag("agent-snapshot-body").assertTextContains("发送时正文")
        screenshot("agent-snapshot-preview")
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("agent-permission-settings").performClick()
        compose.onNodeWithTag("agent-permission-Read").performScrollTo().performClick()
        compose.onNodeWithTag("agent-scope-All").performScrollTo().performClick()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000) { runBlocking { AgentPermissionStore(database).current().let { it.level == AgentPermissionLevel.Read && it.scope == AgentScope.All } } }
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
        compose.onNodeWithText("Agent").performClick()
        compose.waitUntil(5000) { timeline.state.value.ready }
        compose.onNodeWithTag("agent-clear").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索").assertDoesNotExist()
        compose.onAllNodesWithText("Agent").assertCountEquals(1)
        compose.onNodeWithTag("agent-send").assertIsNotEnabled()
        val header = compose.onNodeWithTag("agent-header").fetchSemanticsNode().boundsInRoot
        val reviews = compose.onNodeWithTag("agent-reviews").fetchSemanticsNode().boundsInRoot
        val topic = compose.onNodeWithTag("agent-new-topic").fetchSemanticsNode().boundsInRoot
        val more = compose.onNodeWithTag("agent-more").fetchSemanticsNode().boundsInRoot
        assertTrue(reviews.right <= topic.left && topic.right <= more.left)
        assertTrue(header.contains(reviews.center) && header.contains(more.center))
        val composer = compose.onNodeWithTag("agent-composer").fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithTag("agent-add-attachment").fetchSemanticsNode().boundsInRoot
        val permission = compose.onNodeWithTag("agent-permission-settings").fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithTag("agent-send").fetchSemanticsNode().boundsInRoot
        assertTrue(composer.contains(add.center) && composer.contains(permission.center) && composer.contains(send.center))
        assertTrue(add.right <= permission.left && permission.right <= send.left)
        screenshot("agent-empty-layout")
        compose.onNodeWithTag("agent-add-attachment").performClick()
        compose.onNodeWithText("笔记").assertIsDisplayed()
        compose.onNodeWithText("图片").assertExists()
        screenshot("agent-attachment-menu")
        compose.onNodeWithText("笔记").performClick()
        compose.onNodeWithTag("agent-attach-${note.id}").performClick()
        compose.onNodeWithText("确认 1 篇").performClick()
        compose.waitUntil(5000) { timeline.draftNotes.value == listOf(note.id) }
        compose.onNodeWithTag("agent-draft-note-${note.id}").performClick()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("agent-input").performTextInput("保留这段草稿")
        compose.waitUntil(5000) { timeline.draft.value == "保留这段草稿" }
        compose.onNodeWithTag("agent-add-attachment").performClick()
        compose.onNodeWithText("笔记").assertIsDisplayed()
        screenshot("agent-keyboard-attachment-menu")
        compose.onNodeWithText("笔记").performClick()
        compose.onNodeWithText("取消").performClick()
        hideKeyboard()
        compose.onNodeWithTag("agent-reviews").performClick()
        compose.onNodeWithText("暂无笔记改动").assertIsDisplayed()
        compose.onNodeWithText("我的").assertDoesNotExist()
        screenshot("agent-review-popup")
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("agent-input").assertTextContains("保留这段草稿")
        compose.onNodeWithTag("agent-draft-note-${note.id}").assertIsDisplayed()
        compose.onNodeWithTag("agent-remove-note-${note.id}").performClick()
        compose.waitUntil(5000) { timeline.draftNotes.value.isEmpty() }
        compose.onNodeWithTag("agent-more").performClick()
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
                    else -> { emit(ModelEvent.Text("继续任务已完成")); emit(ModelEvent.Finished(ModelFinish.Complete)) }
                }
            }
        }
        val recovery = AgentTimeline(database, profiles, recoveringModel, scope)
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = recoveringModel, agentTimeline = recovery) } }
        compose.onNodeWithText("Agent").performClick()
        compose.waitUntil(5000) { recovery.state.value.ready }
        compose.onNodeWithTag("agent-input").performTextInput("读取并整理")
        compose.onNodeWithTag("agent-send").performClick()
        compose.waitUntil(5000) { !recovery.state.value.running && runBlocking { database.agent().unfinishedRuns().any { it.status == AgentRunStatus.WaitingPermission } } }
        hideKeyboard()
        compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("agent-authorize") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("agent-authorize").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("agent-scope-Unfiled").performScrollTo().performClick()
        compose.onNodeWithText("允许并继续").performClick()
        compose.waitUntil(5000) { !recovery.state.value.running && runBlocking { database.agent().messages().any { it.status == AgentMessageStatus.Failed } } }
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("read · 已完成"))
        compose.onNodeWithText("read · 已完成").performClick()
        compose.onNodeWithText("我的").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索").assertDoesNotExist()
        compose.onAllNodesWithText("当时权限：一级 · 不可查看 · 仅主动附加笔记 · 版本 0")[0].assertExists()
        compose.onNodeWithText("本次运行授权：二级 · 可查看 · 1 篇；仅在当时权限版本内有效。").performScrollTo().assertIsDisplayed()
        screenshot("agent-tool-decision")
        compose.onNodeWithTag("agent-tool-continue").performScrollTo().performClick()
        compose.waitUntil(5000) { !recovery.state.value.running && runBlocking { database.agent().messages().any { it.text == "继续任务已完成" } } }
        compose.onNodeWithText("我的").assertExists()
        runBlocking {
            assertEquals(1, database.agent().toolEvents(database.agent().messages().first().runId!!).size)
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
            AgentPermissionStore(database).saveFromUser(AgentPermission(AgentPermissionLevel.Read, AgentScope.All))
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
        compose.onNodeWithText("Agent").performClick()
        runBlocking { running.send("读取后继续整理") }
        compose.waitUntil(5000) { requests == 2 }
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("read · 已完成"))
        compose.onNodeWithText("read · 已完成").performClick()
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
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
