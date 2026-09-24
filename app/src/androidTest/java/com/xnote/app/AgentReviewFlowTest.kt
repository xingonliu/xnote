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
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

// -- Tests

class AgentReviewFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = XNoteDatabase.createInMemory(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profiles = ModelProfileStore(database, AndroidModelCredentialStore(context))
    private val client = object : ModelClient {
        override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow<ModelEvent> { error("Review must not call the model") }
    }
    private val timeline = AgentTimeline(database, profiles, client, scope)
    private val library = NoteLibrary(database, AttachmentFileStore(File(context.cacheDir, "review-ui")), SystemEpochClock)

    @After fun cleanup() = runBlocking { scope.coroutineContext[Job]?.cancelAndJoin(); database.close() }

    @Test fun independentReviewSurvivesClearChatAndRejectPreservesUserContent() {
        val noteId = seed()
        runBlocking {
            library.saveNoteContent(noteId, "周末计划", document("先整理想法，再写用户总结。"))
            timeline.clearChat()
            AgentPermissionStore(database).saveFromUser(AgentPermission())
        }
        open(noteId)
        screenshot("cumulative-diff")
        compose.onNodeWithTag("agent-review-reject").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { database.agent().reviews(noteId).single().status == AgentReviewStatus.Rejected } }
        runBlocking { assertEquals("先整理笔记，再写用户总结。", body(noteId)) }
    }

    @Test fun acceptKeepsAppliedContentAndUndoPreservesLaterUserText() {
        val noteId = seed()
        open(noteId)
        compose.onNodeWithTag("agent-review-accept").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { database.agent().reviews(noteId).single().status == AgentReviewStatus.Accepted } }
        runBlocking {
            assertEquals("先整理想法，再写总结。", body(noteId))
            library.saveNoteContent(noteId, "周末计划", document("先整理想法，再写用户总结。"))
        }
        compose.onNodeWithTag("agent-review-undo").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { database.agent().reviews(noteId).single().status == AgentReviewStatus.Undone } }
        runBlocking { assertEquals("先整理笔记，再写用户总结。", body(noteId)) }
    }

    @Test fun conflictPausesWholeRejectionAndAdjustmentPreservesExistingDraft() {
        val noteId = seed()
        runBlocking {
            library.saveNoteContent(noteId, "周末计划", document("先整理用户自己的内容，再写总结。"))
            timeline.saveDraft("尚未发送的想法")
        }
        open(noteId)
        compose.onNodeWithTag("agent-review-conflict").assertExists()
        compose.onNodeWithTag("agent-review-reject").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { database.agent().reviews(noteId).single().status == AgentReviewStatus.Conflict } }
        runBlocking { assertEquals("先整理用户自己的内容，再写总结。", body(noteId)) }
        screenshot("conflict-review")
        compose.onNodeWithTag("agent-review-adjust").performScrollTo().performClick()
        compose.waitUntil(5000) { timeline.draft.value.contains("重新读取") }
        assertTrue(timeline.draft.value.startsWith("尚未发送的想法"))
        assertEquals(listOf(noteId), timeline.draftNotes.value)
        compose.onNodeWithTag("agent-input").assertExists()
    }

    // -- Functions

    private fun seed(): String = runBlocking {
        timeline.awaitReady()
        val note = library.createNote(null)
        library.saveNoteContent(note.id, "周末计划", document("先整理笔记，再写总结。"))
        database.agent().saveRun(AgentRunEntity("review-run", "segment", "user", "profile", 1, AgentRunStatus.Running, 1, 1))
        AgentPermissionStore(database).saveFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.All))
        timeline.reviewStore.applyEdit("review-run", "change", database.notes().get(note.id)!!.editBase(), AgentEditableContent("周末计划", document("先整理想法，再写总结。")))
        database.agent().saveRun(database.agent().run("review-run")!!.copy(status = AgentRunStatus.Complete))
        note.id
    }
    private fun open(noteId: String) {
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = client, agentTimeline = timeline) } }
        compose.onNodeWithText("Agent").performClick()
        compose.onNodeWithTag("agent-reviews").performClick()
        compose.onNodeWithTag("agent-review-$noteId").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("agent-review-reject").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun document(text: String) = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(text)))))
    private suspend fun body(noteId: String) = (decodeNoteDocument(database.notes().get(noteId)!!.documentJson).blocks.single() as TextBlock).inlines.plainText()
    private fun screenshot(name: String) {
        val directory = File(context.getExternalFilesDir(null), "s11-review-screenshots").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
