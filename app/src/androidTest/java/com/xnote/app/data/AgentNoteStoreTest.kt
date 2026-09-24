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
    @Test fun snapshotIsImmutableReusableAndDistinctAcrossVersions() = runBlocking {
        fixture { db, store ->
            seed(db)
            val first = store.capture("user", listOf("note")).single()
            user(db, "second")
            assertEquals(first.snapshotId, store.capture("second", listOf("note")).single().snapshotId)
            val note = db.notes().get("note")!!
            db.notes().upsert(note.copy(title = "新版标题", documentJson = document("新版正文"), updatedAtEpochMs = 2))
            user(db, "third")
            val newer = store.capture("third", listOf("note")).single()
            assertNotEquals(first.snapshotId, newer.snapshotId)
            val result = store.executeTool("run", read("snapshot", snapshotId = first.snapshotId)) as AgentToolResult.Finished
            assertTrue(result.result.content.contains("旧版正文"))
            assertFalse(result.result.content.contains("新版正文"))
            assertTrue(store.executeTool("run", read("current")) is AgentToolResult.PermissionRequired)
        }
    }

    @Test fun snapshotPermissionEndsOnScopeChangeSegmentCloseTrashAndPermanentDeletion() = runBlocking {
        fixture { db, store ->
            seed(db)
            val sources = store.capture("user", listOf("note"))
            val message = db.agent().message("user")!!
            assertNotNull(store.projectMessage("run", message))
            val settings = AgentPermissionStore(db)
            settings.saveFromUser(AgentPermission())
            assertNull(store.projectMessage("run", message))
            settings.saveFromUser(AgentPermission(AgentPermissionLevel.Read, AgentScope.All))
            assertNotNull(store.projectMessage("run", message))
            db.notes().upsert(db.notes().get("note")!!.copy(deletedAtEpochMs = 3))
            assertNull(store.projectMessage("run", message))
            db.notes().upsert(db.notes().get("note")!!.copy(deletedAtEpochMs = null))
            settings.saveFromUser(AgentPermission())
            user(db, "fresh")
            store.capture("fresh", listOf("note"))
            db.agent().saveSegment(AgentSegmentEntity("segment", 1, 5, "new_topic"))
            assertNull(store.projectMessage("run", db.agent().message("fresh")!!))
            db.notes().deleteByIds(listOf("note"))
            assertNull(db.agent().snapshot(sources.single().snapshotId!!))
        }
    }

    @Test fun searchFiltersBeforePaginationAndNeverRevealsOtherNotesOrCounts() = runBlocking {
        fixture { db, store ->
            seed(db)
            db.notebooks().upsert(NotebookEntity("hidden", "隐私笔记本", 1, 1, 1))
            db.notes().upsert(db.notes().get("note")!!.copy(id = "secret", title = "秘密标题", documentJson = document("秘密匹配正文"), notebookId = "hidden", updatedAtEpochMs = 5))
            store.savePermissionFromUser(AgentPermission(AgentPermissionLevel.Read, AgentScope.Unfiled))
            val result = store.executeTool("run", search("search", "")) as AgentToolResult.Finished
            assertTrue(result.result.content.contains("旧版标题"))
            assertFalse(result.result.content.contains("秘密"))
            assertFalse(result.result.content.contains("secret"))
            assertEquals(false, Json.parseToJsonElement(result.result.content).jsonObject["has_more"]!!.jsonPrimitive.boolean)
            assertEquals(listOf(AgentMessageSource("note")), result.sources)
            assertTrue(store.executeTool("run", read("outside", noteId = "secret")) is AgentToolResult.PermissionRequired)
        }
    }

    @Test fun resultReplayIsIdempotentButRevocationInvalidatesCachedContent() = runBlocking {
        fixture { db, store ->
            seed(db)
            store.savePermissionFromUser(AgentPermission(AgentPermissionLevel.Read, AgentScope.All))
            val call = read("once")
            val first = store.executeTool("run", call)
            db.notes().upsert(db.notes().get("note")!!.copy(documentJson = document("用户已经改了")))
            assertEquals(first, store.executeTool("run", call))
            assertEquals(1, db.agent().toolEvents("run").size)
            store.savePermissionFromUser(AgentPermission())
            val replay = store.executeTool("run", call) as AgentToolResult.Finished
            assertFalse(replay.result.content.contains("旧版正文"))
            assertTrue(replay.result.content.contains("unavailable"))
            assertTrue(replay.sources.isEmpty())
            try { store.executeTool("run", read("once", noteId = "other")); fail("different operation cannot reuse call id") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun onceGrantIsBoundToRunAndCurrentPermissionRevision() = runBlocking {
        fixture { db, store ->
            seed(db)
            assertTrue(store.executeTool("run", read("needs-authorization")) is AgentToolResult.PermissionRequired)
            store.grantFromUser("run", AgentPermission(AgentPermissionLevel.Read, AgentScope.Unfiled), false)
            val grant = db.agent().run("run")!!
            db.agent().saveRun(grant.copy(status = AgentRunStatus.Running))
            val granted = store.executeTool("run", read("needs-authorization")) as AgentToolResult.Finished
            assertTrue(granted.result.content.contains("旧版正文"))
            assertEquals(AgentPermission(), AgentPermissionStore(db).current())
            val event = db.agent().toolEvent("run", "needs-authorization")!!
            val decisions = Json.decodeFromString<List<AgentToolDecision>>(event.decisionsJson)
            assertEquals(2, decisions.size)
            assertNull(decisions.first().grant)
            assertEquals(setOf("note"), decisions.last().grant?.noteIds)
            assertEquals(AgentPermissionLevel.None, decisions.last().permission.level)
            store.savePermissionFromUser(AgentPermission())
            assertTrue(store.executeTool("run", read("after-revoke")) is AgentToolResult.PermissionRequired)
            assertEquals(event.decisionsJson, db.agent().toolEvent("run", "needs-authorization")!!.decisionsJson)
        }
    }

    @Test fun strictArgumentsAndPagingProtectDocumentAndUnknownFields() = runBlocking {
        fixture { db, store ->
            seed(db)
            store.savePermissionFromUser(AgentPermission(AgentPermissionLevel.Read, AgentScope.All))
            val invalid = ModelToolCall("bad", "read", buildJsonObject { put("note_id", "note"); put("ignore_permission", true) })
            val denied = store.executeTool("run", invalid) as AgentToolResult.Finished
            assertTrue(denied.result.content.contains("invalid_arguments"))
            val document = document("长文本😀".repeat(100))
            db.notes().upsert(db.notes().get("note")!!.copy(documentJson = document))
            var offset = 0
            val parts = StringBuilder()
            do {
                val call = ModelToolCall("page-$offset", "read", buildJsonObject { put("note_id", "note"); put("offset", offset); put("limit", 31) })
                val result = store.executeTool("run", call) as AgentToolResult.Finished
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
            store.savePermissionFromUser(AgentPermission(AgentPermissionLevel.Read, AgentScope.All))
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
            assertTrue(store.canUseSources(db.agent().run("run")!!, listOf(snapshot)))
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
