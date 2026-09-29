package com.xnote.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

class EditorAgentFlowTest {
    // -- State and Variables

    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = XNoteDatabase.createInMemory(context)
    private val library = NoteLibrary(db, AttachmentFileStore(File(context.cacheDir, "editor-agent-flow")), SystemEpochClock)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profiles = ModelProfileStore(db, AndroidModelCredentialStore(context))

    // -- Functions

    @After fun close() = runBlocking {
        scope.coroutineContext[Job]?.cancelAndJoin()
        profiles.list().forEach { profiles.delete(it.id) }
        db.close()
    }

    @Test fun editorButtonSavesLatestTextAndCarriesMetadataWithoutSending() {
        val note = runBlocking {
            val created = library.createNote(null)
            library.saveNote(created.copy(title = "携带笔记", document = document("准备发送的正文")))
        }
        val client = object : ModelClient {
            override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow<ModelEvent> { error("must not send") }
        }
        val timeline = AgentTimeline(db, profiles, client, scope)
        runBlocking { timeline.saveDraft("已有草稿") }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, agentTimeline = timeline) } }
        compose.onNodeWithTag("xnote-collection-unfiled").performClick()
        compose.onNodeWithText("携带笔记").performClick()
        compose.onNodeWithTag("xnote-editor-body").performTextInputSelection(TextRange("准备发送的正文".length))
        compose.onNodeWithTag("xnote-editor-body").performTextInput("新增内容")
        compose.onNodeWithTag("xnote-editor-agent").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("agent-input").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("agent-input").assertTextContains("已有草稿")
        compose.onNodeWithTag("agent-draft-note-${note.id}").assertTextContains("携带笔记").assertTextContains("准备发送的正文新增内容")
        assertTrue(runBlocking { db.agent().messages().isEmpty() })
        assertTrue(runBlocking { db.notes().get(note.id)!!.documentJson.contains("新增内容") })
        screenshot("editor-carried-note")
    }

    @Test fun selectedPolishUsesApplicationRangeAndCanBeRejectedFromReview() = polishFromEditor(selected = true)

    @Test fun fullPolishUsesSameWriteAndReviewFlow() = polishFromEditor(selected = false)

    @Test fun tabletEditorSelectionCarriesThroughTheSharedAgentFlow() = polishFromEditor(selected = true, tablet = true)

    private fun polishFromEditor(selected: Boolean, tablet: Boolean = false) {
        val note = runBlocking {
            profiles.save(ModelProfile("polish-ui", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "local-test")
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(true, true, 1))
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.All))
            val created = library.createNote(null)
            library.saveNote(created.copy(title = "润色验收", document = document("前原文后")))
        }
        var requests = 0
        val client = object : ModelClient {
            override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
                if (++requests == 1) {
                    assertTrue(request.messages.any { it.text.contains("用户显式附加的笔记引用") })
                    val base = db.notes().get(note.id)!!
                    emit(ModelEvent.ToolCall(ModelToolCall("polish", "write", buildJsonObject {
                        put("note_id", note.id); put("base_version", base.agentVersion()); put("title", note.title)
                        put("document_json", document(if (selected) "前润色内容后" else "全文润色内容").encodeToJson())
                    })))
                    emit(ModelEvent.Finished(ModelFinish.ToolCalls))
                } else {
                    emit(ModelEvent.Text("润色完成")); emit(ModelEvent.Finished(ModelFinish.Complete))
                }
            }
        }
        val timeline = AgentTimeline(db, profiles, client, scope)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(if (tablet) 0.8f else density.density, density.fontScale)) {
                XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = client, agentTimeline = timeline) }
            }
        }
        compose.onNodeWithTag("xnote-collection-unfiled").performClick()
        compose.onNodeWithText("润色验收").performClick()
        if (selected) compose.onNodeWithTag("xnote-editor-body").performTextInputSelection(TextRange(1, 3))
        compose.onNode(hasContentDescription("更多") and if (tablet) hasAnyAncestor(hasTestTag("xnote-tablet-content")) else SemanticsMatcher("任意页面") { true }).performClick()
        if (selected) screenshot("selection-polish-menu")
        compose.onNodeWithText(if (selected) "润色所选文字" else "润色全文").assertIsEnabled()
        compose.onNodeWithText(if (selected) "润色所选文字" else "润色全文").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("agent-input").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(selected, timeline.draftSelection.value != null)
        if (selected) assertEquals(1, timeline.draftSelection.value!!.selection.start)
        screenshot(if (tablet) "tablet-selection-polish-draft" else if (selected) "selection-polish-draft" else "full-polish-draft")
        compose.onNodeWithTag("agent-send").performClick()
        compose.waitUntil(8000) { requests == 2 && !timeline.state.value.running }
        assertEquals(document(if (selected) "前润色内容后" else "全文润色内容"), runBlocking { library.getNote(note.id)!!.document })
        compose.onNodeWithTag("agent-reviews").performClick()
        compose.onNodeWithTag("agent-review-${note.id}").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("agent-review-reject").fetchSemanticsNodes().isNotEmpty() }
        screenshot(if (tablet) "tablet-selection-polish-review" else if (selected) "selection-polish-review" else "full-polish-review")
        compose.onNodeWithTag("agent-review-reject").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { db.agent().reviews(note.id).single().status == AgentReviewStatus.Rejected } }
        assertEquals(note.document, runBlocking { library.getNote(note.id)!!.document })
    }

    @Test fun openEditorRefreshesAgentBodyAndContinuesTypingWithoutStaleOverwrite() {
        val note = runBlocking {
            db.agent().saveSegment(AgentSegmentEntity("segment", 1))
            db.agent().saveRun(AgentRunEntity("run", "segment", "user", "profile", 1, AgentRunStatus.Running, 1, 1))
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.All))
            val created = library.createNote(null)
            library.saveNote(requireNotNull(library.getNote(created.id)).copy(title = "并行编辑验收", document = document("原始正文")))
        }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library) } }
        compose.onNodeWithTag("xnote-collection-unfiled").performClick()
        compose.onNodeWithText("并行编辑验收").performClick()
        compose.onNodeWithTag("xnote-editor-body").assertTextContains("原始正文")
        runBlocking {
            val current = db.notes().get(note.id)!!
            assertTrue(AgentReviewStore(db).applyEdit("run", "write", current.editBase(), AgentEditableContent(note.title, document("Agent 整理后的正文"))) is AgentReviewResult.Applied)
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Agent 整理后的正文").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("xnote-editor-body").performTextInputSelection(TextRange("Agent 整理后的正文".length))
        compose.onNodeWithTag("xnote-editor-body").performTextInput("；用户继续输入")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(5000) { runBlocking { library.getNote(note.id)!!.document.blocks.filterIsInstance<TextBlock>().single().inlines.plainText() == "Agent 整理后的正文；用户继续输入" } }
        compose.onNodeWithText("并行编辑验收").performClick()
        compose.onNodeWithTag("xnote-editor-body").assertTextContains("Agent 整理后的正文；用户继续输入")
        compose.onNodeWithContentDescription("返回").performClick()
    }

    private fun document(text: String) = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(text)))))

    private fun screenshot(name: String) {
        val file = File(context.getExternalFilesDir(null), "s11-editor-agent-screenshots/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
