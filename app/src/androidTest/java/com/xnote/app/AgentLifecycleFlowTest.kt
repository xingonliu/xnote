package com.xnote.app

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.SystemEpochClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

// -- Tests

class AgentLifecycleFlowTest {
    // -- State and Variables

    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = XNoteDatabase.createInMemory(context)
    private val profiles = ModelProfileStore(db, AndroidModelCredentialStore(context))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val library = NoteLibrary(db, AttachmentFileStore(File(context.cacheDir, "lifecycle-flow")), SystemEpochClock)

    // -- Functions

    @After fun close() = runBlocking {
        scope.coroutineContext[Job]?.cancelAndJoin()
        db.agent().unfinishedRuns().forEach { db.agent().saveRun(it.copy(status = AgentRunStatus.Cancelled)) }
        profiles.list().forEach { profiles.delete(it.id) }
        db.close()
    }

    @Test fun explicitlyAuthorizeCreationThenOpenItsReviewFromExpandedTool() {
        runBlocking {
            profiles.save(ModelProfile("lifecycle-ui", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "local-test")
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(true, true, 1))
            db.notebooks().upsert(NotebookEntity("book", "旅行笔记", 0, 1, 1))
        }
        var requests = 0
        val model = object : ModelClient {
            override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
                if (++requests == 1) {
                    val document = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun("记录这次旅行的见闻。")))))
                    emit(ModelEvent.ToolCall(ModelToolCall("create", "create", Json.encodeToJsonElement(AgentCreateArguments("旅行计划", document.encodeToJson(), AgentCreateTarget("book"))).jsonObject)))
                    emit(ModelEvent.Finished(ModelFinish.ToolCalls))
                } else { emit(ModelEvent.Text("已创建旅行计划，可以审阅。")); emit(ModelEvent.Finished(ModelFinish.Complete)) }
            }
        }
        val timeline = AgentTimeline(db, profiles, model, scope)
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = model, agentTimeline = timeline) } }
        compose.onNodeWithText("Agent").performClick()
        runBlocking { timeline.send("新建旅行计划") }
        compose.waitUntil(5000) { !timeline.state.value.running && runBlocking { db.agent().unfinishedRuns().any { it.status == AgentRunStatus.WaitingPermission } } }
        compose.onNodeWithTag("agent-authorize").performClick()
        compose.onNodeWithText("查看完整请求").performClick()
        compose.onNodeWithTag("agent-approval-arguments").assertTextContains("book", substring = true)
        screenshot("creation-target")
        compose.onNodeWithText("允许本次").performClick()
        compose.waitUntil(5000) { !timeline.state.value.running && runBlocking { db.agent().unfinishedRuns().isEmpty() } }
        val note = runBlocking { db.notes().getAll().single() }
        assertEquals("book", note.notebookId)
        assertEquals(AgentPermission(), runBlocking { AgentPermissionStore(db).current() })
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("新建笔记", substring = true))
        compose.onNodeWithText("新建笔记", substring = true).performClick()
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasTestTag("agent-review-receipt-${note.id}"))
        compose.onNodeWithTag("agent-review-receipt-${note.id}").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("agent-review-reject").fetchSemanticsNodes().isNotEmpty() }
        screenshot("creation-review")
        compose.onNodeWithTag("agent-review-reject").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { db.notes().get(note.id)!!.deletedAtEpochMs != null } }
        assertEquals(AgentReviewStatus.Rejected, runBlocking { db.agent().reviews(note.id).single().status })
    }

    @Test fun expandedDeletionToolOpensTheReviewAndRejectionRestoresTheNote() {
        val note = runBlocking {
            profiles.save(ModelProfile("delete-ui", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "local-test")
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(true, true, 1))
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            val created = library.createNote(null)
            library.saveNote(requireNotNull(library.getNote(created.id)).copy(title = "可以恢复的笔记", document = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun("删除后仍保留正文。")))))))
        }
        var requests = 0
        val model = object : ModelClient {
            override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
                if (++requests == 1) {
                    emit(ModelEvent.ToolCall(ModelToolCall("delete", "delete", Json.encodeToJsonElement(AgentDeleteArguments(note.id, db.notes().get(note.id)!!.agentVersion())).jsonObject)))
                    emit(ModelEvent.Finished(ModelFinish.ToolCalls))
                } else { emit(ModelEvent.Text("已移入回收站，可以恢复。")); emit(ModelEvent.Finished(ModelFinish.Complete)) }
            }
        }
        val timeline = AgentTimeline(db, profiles, model, scope)
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = model, agentTimeline = timeline) } }
        compose.onNodeWithText("Agent").performClick()
        runBlocking { timeline.selectDraftNotes(listOf(note.id)); timeline.send("删除这篇笔记") }
        compose.waitUntil(5000) { !timeline.state.value.running && runBlocking { db.notes().get(note.id)!!.deletedAtEpochMs != null } }
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasText("删除笔记", substring = true))
        compose.onNodeWithText("删除笔记", substring = true).performClick()
        compose.onNodeWithTag("agent-timeline").performScrollToNode(hasTestTag("agent-review-receipt-${note.id}"))
        compose.onNodeWithTag("agent-note-receipt-${note.id}").assertTextContains("已移入回收站", substring = true)
        screenshot("deletion-card")
        compose.onNodeWithTag("agent-review-receipt-${note.id}").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("agent-review-reject").fetchSemanticsNodes().isNotEmpty() }
        screenshot("deletion-review")
        compose.onNodeWithTag("agent-review-reject").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { db.notes().get(note.id)!!.deletedAtEpochMs == null } }
        assertEquals(note.document, decodeNoteDocument(runBlocking { db.notes().get(note.id)!!.documentJson }))
    }

    private fun screenshot(name: String) {
        val file = File(context.getExternalFilesDir(null), "s11-lifecycle-screenshots/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
