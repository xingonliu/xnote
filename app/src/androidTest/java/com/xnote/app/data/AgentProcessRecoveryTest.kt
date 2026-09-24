package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

// -- Tests

/** Two explicit instrumentation invocations with a host-side SIGKILL between them. */
class AgentProcessRecoveryTest {
    // -- Constants

    companion object {
        private const val DatabaseName = "agent-process-recovery-test.db"
        private const val MarkerName = "agent-process-recovery-ready"
    }

    // -- Functions

    @Test fun prepareAndWaitForKill() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("processPhase") == "prepare")
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DatabaseName)
        val db = XNoteDatabase.create(context, DatabaseName)
        val profiles = ModelProfileStore(db, AndroidModelCredentialStore(context))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val marker = File(context.filesDir, MarkerName)
        marker.delete()
        try {
            profiles.save(ModelProfile("process-test", name = "进程恢复测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "local-process-test")
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(true, true, 1))
            val document = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun("终止前正文"))))).encodeToJson()
            db.notes().upsert(NoteEntity("process-note", null, "进程测试", document, null, 0, 0, 0, "终止前正文", 1, 1, null, null))
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.All))
            var requests = 0
            val model = object : ModelClient {
                override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
                    if (++requests == 1) {
                        emit(ModelEvent.ToolCall(ModelToolCall("durable-read", "read", buildJsonObject { put("note_id", "process-note") })))
                        emit(ModelEvent.Finished(ModelFinish.ToolCalls))
                    } else if (requests == 2) {
                        val version = Json.parseToJsonElement(request.messages.last().results.single().content).jsonObject.getValue("version").jsonPrimitive.content
                        emit(ModelEvent.ToolCall(ModelToolCall("durable-write", "write", buildJsonObject {
                            put("note_id", "process-note"); put("base_version", version); put("title", "进程测试")
                            put("document_json", NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun("Agent 整理：终止前正文"))))).encodeToJson())
                        })))
                        emit(ModelEvent.Finished(ModelFinish.ToolCalls))
                    } else {
                        emit(ModelEvent.Text("终止前已保存的部分回复"))
                        marker.writeText(android.os.Process.myPid().toString())
                        awaitCancellation()
                    }
                }
            }
            val timeline = AgentTimeline(db, profiles, model, scope)
            timeline.send("读取并修改笔记后继续整理")
            withTimeout(60_000) { while (!marker.exists()) delay(50) }
            timeline.enqueue("恢复后仍应等待的队列")
            marker.writeText("${android.os.Process.myPid()}:ready")
            withTimeout(60_000) { awaitCancellation() }
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.close()
        }
    }

    @Test fun recoverAndContinueWithoutReplayingCommittedTools() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("processPhase") == "recover")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val marker = File(context.filesDir, MarkerName)
        assertNotEquals(marker.readText().substringBefore(':').toInt(), android.os.Process.myPid())
        val db = XNoteDatabase.create(context, DatabaseName)
        val profiles = ModelProfileStore(db, AndroidModelCredentialStore(context))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var requests = 0
        val model = object : ModelClient {
            override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
                requests++
                val results = request.messages.flatMap { it.results }
                assertTrue(results.single { it.name == "read" }.content.contains("终止前正文"))
                assertTrue(results.single { it.name == "write" }.content.contains("applied"))
                assertTrue(request.messages.any { it.text == "终止前已保存的部分回复" })
                emit(ModelEvent.Text("重启后继续完成"))
                emit(ModelEvent.Finished(ModelFinish.Complete))
            }
        }
        val timeline = AgentTimeline(db, profiles, model, scope)
        try {
            timeline.awaitReady()
            delay(250)
            assertEquals(0, requests)
            val run = db.agent().unfinishedRuns().single()
            assertEquals(AgentRunStatus.Interrupted, run.status)
            assertEquals("process_interrupted", run.errorCode)
            assertEquals(AgentQueueStatus.Paused, db.agent().pendingQueue().single().status)
            val committed = db.agent().toolEvents(run.id)
            assertEquals(2, committed.size)
            assertTrue(committed.all { it.status == AgentToolStatus.Committed })
            val note = db.notes().get("process-note")!!
            assertTrue(note.documentJson.contains("Agent 整理：终止前正文"))
            val changes = db.agent().noteChanges(note.id)
            assertEquals(1, changes.size)
            assertEquals(AgentReviewStatus.Pending, timeline.reviewStore.detail(note.id)!!.review.status)
            val user = note.copy(documentJson = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun("Agent 整理：终止前正文；用户补充"))))).encodeToJson())
            db.notes().upsert(user)
            timeline.continueRun(run.id)
            withTimeout(5000) { timeline.state.first { !it.running } }
            assertEquals(1, requests)
            assertEquals(AgentRunStatus.Complete, db.agent().run(run.id)!!.status)
            assertEquals(committed, db.agent().toolEvents(run.id))
            assertEquals(changes, db.agent().noteChanges(note.id))
            assertEquals(user, db.notes().get(note.id))
            assertEquals(AgentQueueStatus.Paused, db.agent().pendingQueue().single().status)
            timeline.reviewStore.reject(note.id)
            assertEquals("终止前正文；用户补充", decodeNoteDocument(db.notes().get(note.id)!!.documentJson).blocks.filterIsInstance<TextBlock>().single().inlines.plainText())
        } finally {
            timeline.clearChat()
            scope.coroutineContext[Job]?.cancelAndJoin()
            profiles.delete("process-test")
            db.close()
            context.deleteDatabase(DatabaseName)
            marker.delete()
        }
    }

}
