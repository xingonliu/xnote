package com.xnote.app

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.XNoteDatabase
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

class AgentWriteFlowTest {
    // -- State and Variables

    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = XNoteDatabase.createInMemory(context)
    private val profiles = ModelProfileStore(db, AndroidModelCredentialStore(context))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val library = NoteLibrary(db, AttachmentFileStore(File(context.cacheDir, "write-flow")), SystemEpochClock)

    // -- Functions

    @After fun close() = runBlocking {
        scope.coroutineContext[Job]?.cancelAndJoin()
        profiles.list().forEach { profiles.delete(it.id) }
        db.close()
    }

    @Test fun userResolvesWriteConflictThenRejectsOnlyTheAgentChange() {
        val note = runBlocking {
            profiles.save(ModelProfile("write-ui", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "local-test")
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(true, true, 1))
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.All))
            val created = library.createNote(null)
            library.saveNoteContent(created.id, "周末计划", document("先整理笔记，再写总结。"))
        }
        var requests = 0
        val client = object : ModelClient {
            override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
                when (++requests) {
                    1, 3 -> emit(ModelEvent.ToolCall(ModelToolCall("read-$requests", "read", buildJsonObject { put("note_id", note.id) })))
                    2, 4 -> {
                        val version = Json.parseToJsonElement(request.messages.last().results.single().content).jsonObject.getValue("version").jsonPrimitive.content
                        if (requests == 2) library.saveNoteContent(note.id, note.title, document("先整理我的想法，再写总结。"))
                        emit(ModelEvent.ToolCall(ModelToolCall("write-$requests", "write", buildJsonObject {
                            put("note_id", note.id); put("base_version", version); put("title", note.title)
                            put("document_json", document(if (requests == 2) "先整理思路，再写总结。" else "先整理我的想法，再写一份总结。").encodeToJson())
                        })))
                    }
                    else -> emit(ModelEvent.Text("已保留你的内容并完成调整。"))
                }
                emit(ModelEvent.Finished(if (requests <= 4) ModelFinish.ToolCalls else ModelFinish.Complete))
            }
        }
        val timeline = AgentTimeline(db, profiles, client, scope)
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library, modelProfiles = profiles, modelClient = client, agentTimeline = timeline) } }
        compose.onNodeWithText("Agent").performClick()
        runBlocking { timeline.send("帮我整理周末计划") }
        compose.waitUntil(5000) { !timeline.state.value.running && runBlocking { db.agent().unfinishedRuns().any { it.status == AgentRunStatus.WaitingConflict } } }
        compose.onNodeWithTag("agent-replan-conflict").assertIsDisplayed()
        screenshot("write-conflict")
        assertTrue(runBlocking { db.notes().get(note.id)!!.documentJson.contains("我的想法") })
        compose.onNodeWithTag("agent-replan-conflict").performClick()
        compose.waitUntil(5000) { !timeline.state.value.running && runBlocking { db.agent().unfinishedRuns().isEmpty() } }
        assertEquals(5, requests)
        compose.onNodeWithTag("agent-reviews").performClick()
        compose.onNodeWithTag("agent-review-${note.id}").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("agent-review-reject").fetchSemanticsNodes().isNotEmpty() }
        screenshot("write-review")
        compose.onNodeWithTag("agent-review-reject").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { db.agent().reviews(note.id).single().status == AgentReviewStatus.Rejected } }
        assertEquals(document("先整理我的想法，再写总结。"), decodeNoteDocument(runBlocking { db.notes().get(note.id)!!.documentJson }))
    }

    private fun document(text: String) = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(text)))))
    private fun screenshot(name: String) {
        val file = File(context.getExternalFilesDir(null), "s11-write-screenshots/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
