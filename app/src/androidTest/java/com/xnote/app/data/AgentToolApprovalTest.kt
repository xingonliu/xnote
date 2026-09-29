package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

// -- Type Definitions

class AgentToolApprovalTest {
    // -- Functions

    @Test fun everyProviderModeRequiresApprovalBeforeHandlerAndReplaysOnlyOnce() = runBlocking {
        for (mode in AgentToolExecutionMode.entries) fixture { db ->
            var executions = 0
            val provider = AgentToolProvider { listOf(AgentToolRegistration(ModelTool("probe", "probe", buildJsonObject {}),
                { true }, { _, call -> executions++; AgentToolResult.Finished(ModelToolResult(call.id, call.name, "{}"), emptyList()) },
                auditReason = "probe", mode = mode)) }
            val store = AgentNoteStore(db, toolProviders = listOf(provider))
            val call = ModelToolCall("one", "probe", buildJsonObject { put("target", "A") })
            assertEquals(AgentPermissionMode.RequestApproval, AgentPermissionStore(db).current().mode)
            assertTrue(store.toolExecutor.execute("run", call) is AgentToolResult.PermissionRequired)
            assertEquals(0, executions)
            store.approveFromUser("run", call.id, call.arguments.toString())
            resume(db)
            val reopened = AgentNoteStore(db, toolProviders = listOf(provider))
            assertTrue(reopened.toolExecutor.execute("run", call) is AgentToolResult.Finished)
            reopened.toolExecutor.execute("run", call)
            assertEquals(1, executions)
            assertTrue(reopened.toolExecutor.execute("run", call.copy(id = "two")) is AgentToolResult.PermissionRequired)
            assertEquals(1, executions)
        }
    }

    @Test fun privateModeBlocksAllBuiltInsAndExternalHandlersEvenWithAttachments() = runBlocking { fixture { db ->
        var executions = 0
        val provider = AgentToolProvider { listOf(AgentToolRegistration(ModelTool("remote", "remote", buildJsonObject {}),
            { true }, { _, call -> executions++; AgentToolResult.Finished(ModelToolResult(call.id, call.name, "{}"), emptyList()) },
            auditReason = "remote", mode = AgentToolExecutionMode.External)) }
        val store = AgentNoteStore(db, toolProviders = listOf(provider))
        store.savePermissionFromUser(AgentPermission(AgentPermissionMode.Private))
        assertTrue(store.toolRegistry.definitions(store.toolContext(db.agent().run("run")!!)).isEmpty())
        for (name in (AgentNoteTools + AgentMemoryTools).map { it.name } + "remote") {
            val result = store.toolExecutor.execute("run", ModelToolCall(name, name, buildJsonObject {})) as AgentToolResult.Finished
            assertTrue(result.result.content.contains("private_mode"))
            assertEquals(AgentToolStatus.Denied, db.agent().toolEvent("run", name)!!.status)
        }
        assertEquals(0, executions)
        assertEquals(AgentRunStatus.Running, db.agent().run("run")!!.status)
    } }

    @Test fun changedArgumentsAndPermissionRevisionCannotReuseApproval() = runBlocking { fixture { db ->
        val store = AgentNoteStore(db)
        val call = ModelToolCall("one", "read", buildJsonObject { put("note_id", "A") })
        store.toolExecutor.execute("run", call)
        try { store.approveFromUser("run", call.id, "{}"); fail("different arguments") } catch (_: IllegalArgumentException) { }
        store.approveFromUser("run", call.id, call.arguments.toString())
        resume(db)
        try { store.toolExecutor.execute("run", call.copy(arguments = buildJsonObject { put("note_id", "B") })); fail("changed target") }
        catch (_: IllegalArgumentException) { }
        store.savePermissionFromUser(AgentPermission())
        assertTrue(store.toolExecutor.execute("run", call) is AgentToolResult.PermissionRequired)
        assertEquals(AgentToolStatus.Requested, db.agent().toolEvent("run", call.id)!!.status)
        assertTrue(db.agent().toolEvent("run", call.id)!!.resultJson == null)
    } }

    @Test fun denyingMemoryCallNeverExecutesItAndCancelledRunCannotConsumeApproval() = runBlocking { fixture { db ->
        val store = AgentNoteStore(db)
        val call = ModelToolCall("one", "memory_search", buildJsonObject { put("query", "secret") })
        store.toolExecutor.execute("run", call)
        store.denyFromUser("run", call.id)
        resume(db)
        val denied = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
        assertTrue(denied.result.content.contains("user_denied"))
        val next = call.copy(id = "two")
        store.toolExecutor.execute("run", next)
        store.approveFromUser("run", next.id, next.arguments.toString())
        db.agent().saveRun(db.agent().run("run")!!.copy(status = AgentRunStatus.Cancelled))
        try { store.toolExecutor.execute("run", next); fail("cancelled run") } catch (_: IllegalArgumentException) { }
        assertEquals(AgentToolStatus.Approved, db.agent().toolEvent("run", next.id)!!.status)
    } }

    @Test fun permissionChangeHidesPreviouslyGeneratedMemoryContent() = runBlocking { fixture { db ->
        val store = AgentNoteStore(db)
        val permission = store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
        val answer = AgentMessageEntity(id = "answer", segmentId = "segment", runId = "run", role = AgentMessageRole.Assistant,
            text = "自动记忆里的秘密", status = AgentMessageStatus.Complete, createdAtEpochMs = 1,
            contextPermissionRevision = permission.revision)
        db.agent().insertMessage(answer)
        assertNotNull(store.projectMessage("run", answer))
        store.savePermissionFromUser(AgentPermission())
        assertNull(store.projectMessage("run", answer))
        store.savePermissionFromUser(AgentPermission(AgentPermissionMode.Private))
        assertNull(store.projectMessage("run", answer))
    } }

    @Test fun stoppingPendingCallCancelsItsApprovalEvenIfRunIsContinued() = runBlocking { fixture { db ->
        val store = AgentNoteStore(db)
        val call = ModelToolCall("pending", "read", buildJsonObject { put("note_id", "A") })
        store.toolExecutor.execute("run", call)
        store.approveFromUser("run", call.id, call.arguments.toString())
        store.cancelPendingFromUser("run")
        resume(db)
        val result = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
        assertTrue(result.result.content.contains("user_cancelled"))
    } }

    @Test fun explicitHistoryApprovalMakesReturnedSourcesUsableAfterPermissionChange() = runBlocking { fixture { db ->
        val store = AgentNoteStore(db)
        val old = store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
        db.agent().saveSegment(AgentSegmentEntity("old", 1))
        db.agent().saveRun(AgentRunEntity("old-run", "old", "old-user", "profile", 1, AgentRunStatus.Complete, 1, 2))
        for ((id, role) in listOf("old-user" to AgentMessageRole.User, "old-answer" to AgentMessageRole.Assistant)) {
            db.agent().insertMessage(AgentMessageEntity(id = id, segmentId = "old", runId = "old-run", role = role,
                text = "历史旅行内容", status = AgentMessageStatus.Complete, createdAtEpochMs = 1, contextPermissionRevision = old.revision))
        }
        AgentEpisodeStore(db).close(db.agent().segment("old")!!, "new_topic")
        store.savePermissionFromUser(AgentPermission())
        val call = ModelToolCall("history", "memory_read", buildJsonObject { put("episodeId", "old") })
        assertTrue(store.toolExecutor.execute("run", call) is AgentToolResult.PermissionRequired)
        store.approveFromUser("run", call.id, call.arguments.toString())
        resume(db)
        val result = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
        assertTrue(result.result.content.contains("历史旅行内容"))
        val event = db.agent().toolEvent("run", call.id)!!
        assertTrue(AgentMemoryProvenance(db).canUse(event.id, db.agent().run("run")!!))
        val replay = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
        assertEquals(result.result, replay.result)
        assertEquals(result.sourceMessageIds.toSet(), replay.sourceMessageIds.toSet())
    } }

    private suspend fun resume(db: XNoteDatabase) { db.agent().saveRun(db.agent().run("run")!!.copy(status = AgentRunStatus.Running)) }

    private suspend fun fixture(block: suspend (XNoteDatabase) -> Unit) {
        val db = XNoteDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        try {
            db.agent().saveSegment(AgentSegmentEntity("segment", 1))
            db.agent().saveRun(AgentRunEntity("run", "segment", "user", "profile", 1, AgentRunStatus.Running, 1, 1))
            block(db)
        } finally { db.close() }
    }
}
