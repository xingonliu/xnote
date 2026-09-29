package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentNoteStoreTest {
    @Test fun emptyNotebookMetadataCannotLeakThroughDerivedHistoryAfterRevocation() = runBlocking {
        fixture { db, store ->
            seed(db)
            db.notebooks().upsert(NotebookEntity("empty", "空目录秘密", 1, 1, 1))
            store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            val call = ModelToolCall("directory", "read", buildJsonObject { put("note_id", JsonNull) })
            val result = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
            db.agent().insertMessage(AgentMessageEntity(id = "result", segmentId = "segment", runId = "run", role = AgentMessageRole.Tool,
                text = result.result.content, status = AgentMessageStatus.Complete, createdAtEpochMs = 2,
                modelJson = Json.encodeToString(ModelMessage(AgentMessageRole.Tool, results = listOf(result.result)))))
            AgentMemoryProvenance(db).save("derived-answer", listOf("result"))
            assertTrue(AgentMemoryProvenance(db).canUse("derived-answer", db.agent().run("run")!!))
            store.savePermissionFromUser(AgentPermission())
            assertNull(store.projectMessage("run", db.agent().message("result")!!))
            assertFalse(AgentMemoryProvenance(db).canUse("derived-answer", db.agent().run("run")!!))
        }
    }

    @Test fun attachedMessageContainsOnlyMetadataAndReadReturnsFixedBody() = runBlocking {
        fixture { db, store ->
            seed(db)
            store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            val snapshot = store.capture("user", listOf("note")).single()
            val projected = store.projectMessage("run", db.agent().message("user")!!)!!.message.text
            assertTrue(projected.contains(snapshot.snapshotId!!))
            assertTrue(projected.contains("旧版标题"))
            assertFalse(projected.contains("旧版正文"))
            assertFalse(projected.contains("\"document\""))
            db.notes().upsert(db.notes().get("note")!!.copy(documentJson = document("新版正文")))
            val result = store.toolExecutor.execute("run", read("fixed", snapshotId = snapshot.snapshotId)) as AgentToolResult.Finished
            assertTrue(result.result.content.contains("旧版正文"))
            assertFalse(result.result.content.contains("新版正文"))
        }
    }

    @Test fun unifiedReadBrowsesAuthorizedNotebooksAndNotesWithoutBodies() = runBlocking {
        fixture { db, store ->
            seed(db)
            db.notebooks().upsert(NotebookEntity("book", "工作", 1, 1, 1))
            db.notebooks().upsert(NotebookEntity("empty", "空笔记本", 2, 1, 1))
            db.notebooks().upsert(NotebookEntity("secret", "秘密目录", 3, 1, 1))
            db.notes().upsert(db.notes().get("note")!!.copy(notebookId = "book"))
            store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            val root = store.toolExecutor.execute("run", ModelToolCall("root", "read", buildJsonObject {})) as AgentToolResult.Finished
            assertTrue(root.result.content.contains("空笔记本"))
            assertTrue(root.result.content.contains("旧版标题"))
            assertFalse(root.result.content.contains("旧版正文"))
            assertTrue(root.result.content.contains("秘密目录"))
            val page = store.toolExecutor.execute("run", ModelToolCall("book", "read", buildJsonObject { put("notebook_id", "book"); put("limit", 1) })) as AgentToolResult.Finished
            assertTrue(page.result.content.contains("旧版标题"))
            assertFalse(page.result.content.contains("空笔记本"))
            val body = store.toolExecutor.execute("run", read("body")) as AgentToolResult.Finished
            assertTrue(body.result.content.contains("旧版正文"))
            store.savePermissionFromUser(AgentPermission())
            val replay = store.toolExecutor.execute("run", ModelToolCall("root", "read", buildJsonObject {})) as AgentToolResult.Finished
            assertFalse(replay.result.content.contains("空笔记本"))
            assertTrue(store.toolExecutor.execute("run", ModelToolCall("hidden", "read", buildJsonObject { put("notebook_id", "secret") })) is AgentToolResult.PermissionRequired)
        }
    }

    @Test fun toolDefinitionsFollowThreeModesWithoutAttachmentExceptions() = runBlocking {
        fixture { db, store ->
            seed(db)
            suspend fun names() = store.toolRegistry.definitions(store.toolContext(db.agent().run("run")!!)).map { it.name }.toSet()
            val tools = setOf("read", "note_search", "create", "write", "delete", "memory_remember", "memory_search", "memory_read")
            assertEquals(tools, names())
            store.savePermissionFromUser(AgentPermission(AgentPermissionMode.Private))
            store.capture("user", listOf("note"))
            assertTrue(names().isEmpty())
            store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            assertEquals(tools, names())
        }
    }

    @Test fun externalProviderUsesSharedAuditIndependentPermissionAndReconciliation() = runBlocking {
        fixture { db, _ ->
            seed(db)
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            var allowed = true
            var executions = 0
            var reconciliations = 0
            val tool = ModelTool("example_remote_read", "示例外部工具", buildJsonObject { put("type", "object") })
            val provider = AgentToolProvider { listOf(AgentToolRegistration(tool, { allowed }, { _, call ->
                executions++
                // A database write from a different coroutine proves the remote call holds no writer transaction.
                withContext(Dispatchers.Default) { db.notebooks().upsert(NotebookEntity("remote", "外部结果", 1, 1, 1)) }
                throw java.io.IOException("result unknown")
            }, auditReason = "独立外部授权", mode = AgentToolExecutionMode.External, reconcile = { _, call ->
                reconciliations++
                AgentToolResult.Finished(ModelToolResult(call.id, call.name, "{\"ok\":true}"), emptyList())
            })) }
            val store = AgentNoteStore(db, toolProviders = listOf(provider))
            val call = ModelToolCall("remote-call", tool.name, buildJsonObject {})
            try { withTimeout(5000) { store.toolExecutor.execute("run", call) }; fail("unknown result") } catch (_: java.io.IOException) { }
            assertEquals(AgentToolStatus.Unknown, db.agent().toolEvent("run", call.id)!!.status)
            store.toolExecutor.reconcilePending("run")
            assertEquals(1, executions)
            assertEquals(1, reconciliations)
            assertEquals(AgentToolStatus.Committed, db.agent().toolEvent("run", call.id)!!.status)
            store.toolExecutor.execute("run", call)
            assertEquals(1, executions)
            allowed = false
            assertFalse(store.toolRegistry.definitions(store.toolContext(db.agent().run("run")!!)).any { it.name == tool.name })
            val replay = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
            assertTrue(replay.result.content.contains("unavailable"))
            store.toolExecutor.execute("run", call.copy(id = "denied-call"))
            assertEquals(AgentToolStatus.Denied, db.agent().toolEvent("run", "denied-call")!!.status)
            assertEquals(1, executions)
        }
    }

    @Test fun snapshotIsImmutableReusableAndDistinctAcrossVersions() = runBlocking {
        fixture { db, store ->
            seed(db)
            store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            val first = store.capture("user", listOf("note")).single()
            user(db, "second")
            assertEquals(first.snapshotId, store.capture("second", listOf("note")).single().snapshotId)
            val note = db.notes().get("note")!!
            db.notes().upsert(note.copy(title = "新版标题", documentJson = document("新版正文"), updatedAtEpochMs = 2))
            user(db, "third")
            val newer = store.capture("third", listOf("note")).single()
            assertNotEquals(first.snapshotId, newer.snapshotId)
            val result = store.toolExecutor.execute("run", read("snapshot", snapshotId = first.snapshotId)) as AgentToolResult.Finished
            assertTrue(result.result.content.contains("旧版正文"))
            assertFalse(result.result.content.contains("新版正文"))
            store.savePermissionFromUser(AgentPermission())
            assertTrue(store.toolExecutor.execute("run", read("current")) is AgentToolResult.PermissionRequired)
        }
    }

    @Test fun attachedMetadataNeverAuthorizesBodyAndPrivateModeOmitsIt() = runBlocking {
        fixture { db, store ->
            seed(db)
            val sources = store.capture("user", listOf("note"))
            val message = db.agent().message("user")!!
            assertTrue(store.projectMessage("run", message)!!.message.text.contains("旧版标题"))
            assertFalse(store.canUseSources(db.agent().run("run")!!, sources))
            store.savePermissionFromUser(AgentPermission(AgentPermissionMode.Private))
            assertFalse(store.projectMessage("run", message)!!.message.text.contains("旧版标题"))
            val denied = store.toolExecutor.execute("run", read("private", snapshotId = sources.single().snapshotId)) as AgentToolResult.Finished
            assertTrue(denied.result.content.contains("private_mode"))
            db.notes().deleteByIds(listOf("note"))
            assertNull(db.agent().snapshot(sources.single().snapshotId!!))
        }
    }

    @Test fun approvedSearchUsesItsQueryAndDoesNotAuthorizeReadingTheResults() = runBlocking {
        fixture { db, store ->
            seed(db)
            db.notes().upsert(db.notes().get("note")!!.copy(id = "other", title = "另一篇", documentJson = document("别的正文")))
            val call = search("search", "旧版")
            assertTrue(store.toolExecutor.execute("run", call) is AgentToolResult.PermissionRequired)
            store.approveFromUser("run", call.id, call.arguments.toString())
            db.agent().saveRun(db.agent().run("run")!!.copy(status = AgentRunStatus.Running))
            val result = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
            assertTrue(result.result.content.contains("旧版标题"))
            assertFalse(result.result.content.contains("另一篇"))
            assertTrue(store.toolExecutor.execute("run", read("body")) is AgentToolResult.PermissionRequired)
        }
    }

    @Test fun resultReplayIsIdempotentButRevocationInvalidatesCachedContent() = runBlocking {
        fixture { db, store ->
            seed(db)
            store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            val call = read("once")
            val first = store.toolExecutor.execute("run", call)
            db.notes().upsert(db.notes().get("note")!!.copy(documentJson = document("用户已经改了")))
            assertEquals(first, store.toolExecutor.execute("run", call))
            assertEquals(1, db.agent().toolEvents("run").size)
            store.savePermissionFromUser(AgentPermission())
            val replay = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
            assertFalse(replay.result.content.contains("旧版正文"))
            assertTrue(replay.result.content.contains("unavailable"))
            assertTrue(replay.sources.isEmpty())
            try { store.toolExecutor.execute("run", read("once", noteId = "other")); fail("different operation cannot reuse call id") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun approvalIsBoundToOneCallAndCurrentPermissionRevision() = runBlocking {
        fixture { db, store ->
            seed(db)
            val call = read("needs-authorization")
            assertTrue(store.toolExecutor.execute("run", call) is AgentToolResult.PermissionRequired)
            store.approveFromUser("run", call.id, call.arguments.toString())
            db.agent().saveRun(db.agent().run("run")!!.copy(status = AgentRunStatus.Running))
            val granted = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
            assertTrue(granted.result.content.contains("旧版正文"))
            assertEquals(AgentPermission(), AgentPermissionStore(db).current())
            assertEquals(granted, store.toolExecutor.execute("run", call))
            assertTrue(store.toolExecutor.execute("run", read("another")) is AgentToolResult.PermissionRequired)
            val event = db.agent().toolEvent("run", call.id)!!
            val decisions = Json.decodeFromString<List<AgentToolDecision>>(event.decisionsJson)
            assertEquals(3, decisions.size)
            assertEquals(AgentPermissionMode.RequestApproval, decisions.last().permission.mode)
        }
    }

    @Test fun strictArgumentsAndPagingProtectDocumentAndUnknownFields() = runBlocking {
        fixture { db, store ->
            seed(db)
            store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            val invalid = ModelToolCall("bad", "read", buildJsonObject { put("note_id", "note"); put("ignore_permission", true) })
            val denied = store.toolExecutor.execute("run", invalid) as AgentToolResult.Finished
            assertTrue(denied.result.content.contains("invalid_arguments"))
            val document = document("长文本😀".repeat(100))
            db.notes().upsert(db.notes().get("note")!!.copy(documentJson = document))
            var offset = 0
            val parts = StringBuilder()
            do {
                val call = ModelToolCall("page-$offset", "read", buildJsonObject { put("note_id", "note"); put("offset", offset); put("limit", 31) })
                val result = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
                val payload = Json.parseToJsonElement(result.result.content).jsonObject
                parts.append(payload["document_json"]!!.jsonPrimitive.content)
                offset = payload["next_offset"]?.jsonPrimitive?.intOrNull ?: -1
            } while (offset >= 0)
            assertEquals(document, parts.toString())
        }
    }

    @Test fun derivedHistoryDisappearsWhenItsSourceLeavesScope() = runBlocking {
        fixture { db, store ->
            seed(db)
            store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            val derived = AgentMessageEntity(id = "reply", segmentId = "segment", runId = "run", role = AgentMessageRole.Assistant,
                text = "来自笔记的隐私内容", status = AgentMessageStatus.Complete, createdAtEpochMs = 2, sourcesJson = Json.encodeToString(listOf(AgentMessageSource("note"))))
            assertNotNull(store.projectMessage("run", derived))
            store.savePermissionFromUser(AgentPermission())
            assertNull(store.projectMessage("run", derived))
        }
    }

    @Test fun snapshotReferencesProtectMediaWithoutCopyingTheAttachment() = runBlocking {
        fixture { db, store ->
            seed(db)
            val note = db.notes().get("note")!!
            val document = NoteDocument(blocks = listOf(ImageBlock("image", "attachment")))
            db.notes().upsert(note.copy(documentJson = document.encodeToJson()))
            store.capture("user", listOf("note"))
            assertEquals(listOf("attachment"), db.agent().referencedAttachmentIds())
            db.agent().deleteSnapshotRefs("user")
            db.agent().deleteUnusedSnapshots()
            db.agent().deleteUnusedSnapshotAttachments()
            assertTrue(db.agent().referencedAttachmentIds().isEmpty())
        }
    }

    @Test fun queuedSnapshotDoesNotExpandCurrentRunScopeBeforeDispatch() = runBlocking {
        fixture { db, store ->
            seed(db)
            db.agent().insertMessage(AgentMessageEntity(id = "queued-user", segmentId = "segment", runId = null, role = AgentMessageRole.User,
                text = "稍后处理", status = AgentMessageStatus.Pending, createdAtEpochMs = 2))
            val snapshot = store.capture("queued-user", listOf("note")).single()
            assertFalse(store.canUseSources(db.agent().run("run")!!, listOf(snapshot)))
            db.agent().dispatchMessage("queued-user", "run", "segment")
            assertFalse(store.canUseSources(db.agent().run("run")!!, listOf(snapshot)))
        }
    }

    @Test fun attachmentCaptureRollsBackWholeBatchWhenAnyNoteIsMissing() = runBlocking {
        fixture { db, store ->
            seed(db)
            try { store.capture("user", listOf("note", "missing")); fail("partial snapshot capture must roll back") } catch (_: IllegalArgumentException) { }
            assertTrue(db.agent().snapshotRefs("user").isEmpty())
            assertEquals("[]", db.agent().message("user")?.sourcesJson)
            assertNull(db.agent().snapshotForVersion("note", db.notes().get("note")!!.agentVersion()))
        }
    }

    // -- Functions

    private fun read(id: String, noteId: String = "note", snapshotId: String? = null) = ModelToolCall(id, "read", buildJsonObject {
        put("note_id", noteId); snapshotId?.let { put("snapshot_id", it) }
    })
    private fun search(id: String, query: String) = ModelToolCall(id, "note_search", buildJsonObject { put("query", query) })
    private fun document(text: String) = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(text))))).encodeToJson()
    private suspend fun user(db: XNoteDatabase, id: String) { db.agent().insertMessage(AgentMessageEntity(id = id, segmentId = "segment", runId = "run", role = AgentMessageRole.User, text = "读取附加笔记", status = AgentMessageStatus.Complete, createdAtEpochMs = 1)) }
    private suspend fun seed(db: XNoteDatabase) {
        db.notes().upsert(NoteEntity("note", null, "旧版标题", document("旧版正文"), null, 0, 0, 0, "旧版正文", 1, 1, null, null))
        db.agent().saveSegment(AgentSegmentEntity("segment", 1))
        db.agent().saveRun(AgentRunEntity("run", "segment", "user", "profile", 1, AgentRunStatus.Running, 1, 1))
        user(db, "user")
    }
    private suspend fun fixture(block: suspend (XNoteDatabase, AgentNoteStore) -> Unit) {
        val db = XNoteDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        try { block(db, AgentNoteStore(db)) } finally { db.close() }
    }
}
