package com.xnote.app.data

import android.content.Context
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentWriteToolTest {
    @Test fun readBaseIsDurableAndWritePreservesConcurrentUserChanges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "agent-write-${System.nanoTime()}.db"
        var db = XNoteDatabase.create(context, name)
        try {
            seed(db)
            val store = AgentNoteStore(db)
            val base = db.notes().get("note")!!
            store.executeTool("run", read())
            db.close()
            db = XNoteDatabase.create(context, name)
            db.notes().upsert(base.copy(title = "用户标题", documentJson = document("start 用户 end"), updatedAtEpochMs = 2))
            val result = AgentNoteStore(db).executeTool("run", write("write", base, "A middle end")) as AgentToolResult.Finished
            assertEquals("applied", result.json()["status"]!!.jsonPrimitive.content)
            val saved = db.notes().get("note")!!
            assertEquals("用户标题", saved.title)
            assertEquals(document("A 用户 end"), saved.documentJson)
            assertEquals(AgentToolStatus.Committed, db.agent().toolEvent("run", "write")!!.status)
            assertEquals(1, db.agent().noteChanges("note").size)
            assertNotNull(AgentReviewStore(db).detail("note"))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun writeReplayDoesNotReapplyAndRevocationDoesNotExposeCachedResult() = runBlocking {
        fixture { db, store ->
            val base = db.notes().get("note")!!
            store.executeTool("run", read())
            val call = write("once", base, "A middle end")
            val first = store.executeTool("run", call)
            val user = db.notes().get("note")!!.copy(title = "之后的用户标题")
            db.notes().upsert(user)
            assertEquals(first, store.executeTool("run", call))
            assertEquals(user, db.notes().get("note"))
            assertEquals(1, db.agent().noteChanges("note").size)
            try { store.executeTool("run", write("once", base, "other")); fail("call identity") } catch (_: IllegalArgumentException) { }
            store.savePermissionFromUser(AgentPermission())
            val revoked = store.executeTool("run", call) as AgentToolResult.Finished
            assertTrue(revoked.result.content.contains("unavailable_under_current_permission"))
            assertEquals(user, db.notes().get("note"))
        }
    }

    @Test fun onlyCurrentTopicReadOrDispatchedAttachmentCanSupplyTheBase() = runBlocking {
        fixture { db, store ->
            val base = db.notes().get("note")!!
            assertTrue((store.executeTool("run", write("unread", base, "A")) as AgentToolResult.Finished).result.content.contains("read_required"))
            store.capture("user", listOf("note"))
            assertEquals("applied", (store.executeTool("run", write("attached", base, "A middle end")) as AgentToolResult.Finished).json()["status"]!!.jsonPrimitive.content)
            store.executeTool("run", read())
            val latest = db.notes().get("note")!!
            db.agent().saveSegment(AgentSegmentEntity("other-segment", 3))
            db.agent().saveRun(db.agent().run("run")!!.copy(id = "other-run", segmentId = "other-segment"))
            assertTrue((store.executeTool("other-run", write("other-topic", latest, "B middle end")) as AgentToolResult.Finished).result.content.contains("read_required"))
        }
    }

    @Test fun readReferencesNeverGrantAttachedWritePermissionAndAreGarbageCollected() = runBlocking {
        fixture { db, store ->
            val base = db.notes().get("note")!!.copy(documentJson = NoteDocument(blocks = listOf(ImageBlock("media", "file"))).encodeToJson())
            db.notes().upsert(base)
            store.executeTool("run", read())
            val snapshot = db.agent().readSnapshot("segment", "note", base.agentVersion())!!
            db.agent().deleteUnusedSnapshots()
            assertNotNull(db.agent().snapshot(snapshot.id))
            assertEquals(listOf("file"), db.agent().referencedAttachmentIds())
            store.savePermissionFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.Attached))
            assertTrue(store.access(db.agent().run("run")!!).attachedNoteIds.isEmpty())
            assertTrue(store.executeTool("run", write("outside", base, "x")) is AgentToolResult.PermissionRequired)
            db.agent().deleteToolEvents("run")
            db.agent().deleteUnusedSnapshots()
            db.agent().deleteUnusedSnapshotAttachments()
            assertNull(db.agent().snapshot(snapshot.id))
            assertTrue(db.agent().referencedAttachmentIds().isEmpty())
        }
    }

    @Test fun lowerLevelsCannotWriteEvenWithAnAttachedSnapshot() = runBlocking {
        for (level in listOf(AgentPermissionLevel.None, AgentPermissionLevel.Read)) fixture { db, store ->
            val base = db.notes().get("note")!!
            store.capture("user", listOf("note"))
            store.savePermissionFromUser(AgentPermission(level, AgentScope.All))
            assertTrue(store.executeTool("run", write("denied", base, "A middle end")) is AgentToolResult.PermissionRequired)
            assertEquals(base, db.notes().get("note"))
            assertTrue(db.agent().noteChanges("note").isEmpty())
            assertEquals(AgentRunStatus.WaitingPermission, db.agent().run("run")!!.status)
        }
    }

    @Test fun conflictingWriteWaitsAndExplicitReplanNeverReexecutesTheOldWrite() = runBlocking {
        fixture { db, store ->
            val base = db.notes().get("note")!!
            store.executeTool("run", read())
            val user = base.copy(documentJson = document("start 用户 end"))
            db.notes().upsert(user)
            val call = write("conflict", base, "start Agent end")
            assertTrue(store.executeTool("run", call) is AgentToolResult.Conflict)
            assertEquals(AgentRunStatus.WaitingConflict, db.agent().run("run")!!.status)
            assertEquals(user, db.notes().get("note"))
            assertTrue(db.agent().noteChanges("note").isEmpty())
            store.replanConflictFromUser("run", "conflict")
            db.agent().saveRun(db.agent().run("run")!!.copy(status = AgentRunStatus.Running))
            assertTrue((store.executeTool("run", call) as AgentToolResult.Finished).result.content.contains("edit_conflict"))
            assertEquals(user, db.notes().get("note"))
            store.executeTool("run", read("read-latest"))
            assertEquals("applied", (store.executeTool("run", write("adjusted", user, "start 用户 end！")) as AgentToolResult.Finished).json()["status"]!!.jsonPrimitive.content)
        }
    }

    @Test fun malformedUnknownProtectedAndOversizedWritesLeaveContentUntouched() = runBlocking {
        fixture { db, store ->
            val base = db.notes().get("note")!!.copy(documentJson = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun("start middle end"))), ImageBlock("media", "file"))).encodeToJson())
            db.notes().upsert(base)
            store.executeTool("run", read())
            val valid = write("base", base, "A").arguments
            val invalid = listOf(
                JsonObject(valid + ("extra" to JsonPrimitive(true))),
                JsonObject(valid + ("document_json" to JsonPrimitive("{\"blocks\":[],\"unknown\":true}"))),
                JsonObject(valid + ("document_json" to JsonPrimitive(document("media removed")))),
                JsonObject(valid + ("document_json" to JsonPrimitive(document("x".repeat(AgentNoteLimits.MaxWriteArgumentCharacters))))),
            )
            invalid.forEachIndexed { index, args ->
                assertTrue((store.executeTool("run", ModelToolCall("bad-$index", "write", args)) as AgentToolResult.Finished).result.content.contains("invalid_arguments"))
            }
            assertEquals(base, db.notes().get("note"))
            assertTrue(db.agent().noteChanges("note").isEmpty())
        }
    }

    @Test fun outerTransactionFailureRollsBackBodyReviewAndToolResultTogether() = runBlocking {
        fixture { db, store ->
            val base = db.notes().get("note")!!
            store.executeTool("run", read())
            try { db.useWriterConnection { connection -> connection.immediateTransaction {
                store.executeTool("run", write("rollback", base, "A middle end"))
                error("simulated interruption before commit")
            } }; fail("rollback") } catch (_: IllegalStateException) { }
            assertEquals(base, db.notes().get("note"))
            assertNull(db.agent().toolEvent("run", "rollback"))
            assertTrue(db.agent().noteChanges("note").isEmpty())
            assertNull(AgentReviewStore(db).detail("note"))
        }
    }

    @Test fun paginatedReadCanPinItsVersionAndPermanentDeletionRemovesTheBase() = runBlocking {
        fixture { db, store ->
            val base = db.notes().get("note")!!
            val first = (store.executeTool("run", ModelToolCall("page", "read", buildJsonObject { put("note_id", "note"); put("limit", 20) })) as AgentToolResult.Finished).json()
            val snapshotId = first.getValue("snapshot_id").jsonPrimitive.content
            db.notes().upsert(base.copy(documentJson = document("NEW")))
            val rest = (store.executeTool("run", ModelToolCall("rest", "read", buildJsonObject {
                put("note_id", "note"); put("snapshot_id", snapshotId); put("offset", first.getValue("next_offset").jsonPrimitive.int)
            })) as AgentToolResult.Finished).json()
            assertEquals(base.documentJson, first.getValue("document_json").jsonPrimitive.content + rest.getValue("document_json").jsonPrimitive.content)
            db.notes().deleteByIds(listOf("note"))
            assertNull(db.agent().readSnapshot("segment", "note", base.agentVersion()))
        }
    }

    // -- Functions

    private fun AgentToolResult.Finished.json() = Json.parseToJsonElement(result.content).jsonObject
    private fun read(id: String = "read") = ModelToolCall(id, "read", buildJsonObject { put("note_id", "note") })
    private fun write(id: String, base: NoteEntity, text: String) = ModelToolCall(id, "write", buildJsonObject {
        put("note_id", base.id); put("base_version", base.agentVersion()); put("title", base.title); put("document_json", document(text))
    })
    private fun document(text: String) = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(text))))).encodeToJson()
    private suspend fun seed(db: XNoteDatabase) {
        db.notes().upsert(NoteEntity("note", null, "标题", document("start middle end"), null, 0, 0, 0, "", 1, 1, null, null))
        db.agent().saveSegment(AgentSegmentEntity("segment", 1))
        db.agent().saveRun(AgentRunEntity("run", "segment", "user", "profile", 1, AgentRunStatus.Running, 1, 1))
        db.agent().insertMessage(AgentMessageEntity(id = "user", segmentId = "segment", runId = "run", role = AgentMessageRole.User,
            text = "修改笔记", status = AgentMessageStatus.Complete, createdAtEpochMs = 1))
        AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.All))
    }
    private suspend fun fixture(block: suspend (XNoteDatabase, AgentNoteStore) -> Unit) {
        val db = XNoteDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        try { seed(db); block(db, AgentNoteStore(db)) } finally { db.close() }
    }
}
