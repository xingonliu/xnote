package com.xnote.app.data

import android.content.Context
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.SystemEpochClock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

// -- Tests

class AgentLifecycleToolTest {
    @Test fun rejectingCreationAndDeletionKeepsTheOriginalRecycleBinDeadline() = runBlocking { fixture { db, store, _ ->
        store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
        var clock = 100L
        val reviews = AgentReviewStore(db) { clock }
        val created = (reviews.applyCreate("run", "create", AgentCreateTarget(null), AgentEditableContent("新建", document("正文"))) as AgentReviewResult.Applied).note
        clock = 200
        reviews.applyTrash("run", "delete", created.id, created.agentVersion())
        val deleted = db.notes().get(created.id)!!
        clock = 100_000
        reviews.reject(created.id)
        assertEquals(deleted, db.notes().get(created.id))
        assertEquals(200L, db.notes().get(created.id)!!.deletedAtEpochMs)
    } }

    @Test fun lowerPermissionsCannotCreateOrDeleteWithoutUserAuthorization() = runBlocking {
        for (level in listOf(AgentPermissionMode.RequestApproval)) fixture { db, store, _ ->
            val original = note("existing", null)
            db.notes().upsert(original)
            store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            store.toolExecutor.execute("run", read("read", original.id))
            store.savePermissionFromUser(AgentPermission(level))
            assertTrue(store.toolExecutor.execute("run", delete("delete", original)) is AgentToolResult.PermissionRequired)
            assertEquals(original, db.notes().get(original.id))
            resume(db)
            assertTrue(store.toolExecutor.execute("run", create("create")) is AgentToolResult.PermissionRequired)
            assertEquals(1, db.notes().getAll().size)
        }
    }

    @Test fun soleWritableTargetCreatesOnceAndRejectMovesToTrash() = runBlocking { fixture { db, store, _ ->
        store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
        val call = create("create")
        val first = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
        assertEquals(first, store.toolExecutor.execute("run", call))
        val note = db.notes().get(first.noteId())!!
        assertNull(note.notebookId)
        assertEquals(1, db.notes().getAll().size)
        assertEquals(AgentChangeKind.Create, db.agent().noteChanges(note.id).single().kind)
        assertTrue(store.access(db.agent().run("run")!!).canEdit(AgentNoteAccess(note.id, null)))
        assertTrue(AgentReviewStore(db).reject(note.id) is AgentReviewResult.Applied)
        assertNotNull(db.notes().get(note.id)!!.deletedAtEpochMs)
        assertEquals(AgentReviewStatus.Rejected, db.agent().reviews(note.id).single().status)
    } }

    @Test fun explicitCreationAndDefaultTargetUseTheSameApprovalBoundary() = runBlocking { fixture { db, store, _ ->
        books(db)
        val call = create("once", AgentCreateTarget("one"))
        assertTrue(store.toolExecutor.execute("run", call) is AgentToolResult.PermissionRequired)
        assertTrue(db.notes().getAll().isEmpty())
        store.approveFromUser("run", call.id, call.arguments.toString())
        resume(db)
        val created = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
        assertEquals("one", db.notes().get(created.noteId())!!.notebookId)
        assertFalse(store.access(db.agent().run("run")!!).canEdit(AgentNoteAccess(created.noteId(), "one")))
        assertTrue(store.toolExecutor.execute("run", create("next")) is AgentToolResult.PermissionRequired)
    } }

    @Test fun approvalExpiresWithPermissionRevisionAndMissingTargetCannotBroadenIt() = runBlocking { fixture { db, store, _ ->
        books(db)
        val call = create("once", AgentCreateTarget("one"))
        assertTrue(store.toolExecutor.execute("run", call) is AgentToolResult.PermissionRequired)
        store.approveFromUser("run", call.id, call.arguments.toString())
        store.savePermissionFromUser(AgentPermission())
        resume(db)
        assertTrue(store.toolExecutor.execute("run", call) is AgentToolResult.PermissionRequired)
        assertTrue(db.notes().getAll().isEmpty())
        store.approveFromUser("run", call.id, call.arguments.toString())
        db.notebooks().deleteById("one")
        resume(db)
        val result = store.toolExecutor.execute("run", call) as AgentToolResult.Finished
        assertTrue(result.result.content.contains("resource_unavailable"))
        assertTrue(db.notes().getAll().isEmpty())
    } }

    @Test fun createdNoteWithUserContentBlocksWholeBatchRejection() = runBlocking { fixture { db, store, library ->
        store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
        val created = store.toolExecutor.execute("run", create("create")) as AgentToolResult.Finished
        val original = db.notes().get(created.noteId())!!
        AgentReviewStore(db).applyEdit("run", "edit", original.editBase(), AgentEditableContent(original.title, document("Agent 后续正文")))
        library.saveNote(requireNotNull(library.getNote(original.id)).copy(title = original.title, document = document("Agent 后续正文；用户补充")))
        val before = db.notes().get(original.id)!!
        assertTrue(AgentReviewStore(db).reject(original.id) is AgentReviewResult.Conflict)
        assertEquals(before, db.notes().get(original.id))
        assertEquals(AgentReviewStatus.Conflict, db.agent().reviews(original.id).single().status)
    } }

    @Test fun deletionRequiresLatestVersionAndRejectRestoresWithoutLosingContent() = runBlocking { fixture { db, store, library ->
        store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
        val original = note("note", null)
        db.notes().upsert(original)
        store.toolExecutor.execute("run", read("read", original.id))
        library.saveNote(requireNotNull(library.getNote(original.id)).copy(title = original.title, document = document("用户已经改写")))
        val current = db.notes().get(original.id)!!
        assertTrue(store.toolExecutor.execute("run", delete("stale", original)) is AgentToolResult.Conflict)
        assertEquals(current, db.notes().get(original.id))
        store.replanConflictFromUser("run", "stale"); resume(db)
        store.toolExecutor.execute("run", read("fresh", original.id))
        val removed = store.toolExecutor.execute("run", delete("delete", current)) as AgentToolResult.Finished
        assertTrue(removed.sources.isEmpty())
        assertNotNull(db.notes().get(original.id)!!.deletedAtEpochMs)
        assertTrue(AgentReviewStore(db).reject(original.id) is AgentReviewResult.Applied)
        assertNull(db.notes().get(original.id)!!.deletedAtEpochMs)
        assertEquals(current.documentJson, db.notes().get(original.id)!!.documentJson)
        assertEquals(removed, store.toolExecutor.execute("run", delete("delete", current)))
        assertNull(db.notes().get(original.id)!!.deletedAtEpochMs)
    } }

    @Test fun rejectDeletionKeepsUserRestorationAndFallsBackToUnfiled() = runBlocking { fixture { db, store, library ->
        books(db)
        store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
        val original = note("note", "one")
        db.notes().upsert(original)
        store.toolExecutor.execute("run", read("read", original.id)); store.toolExecutor.execute("run", delete("delete", original))
        library.restoreNotes(listOf(original.id))
        library.saveNote(requireNotNull(library.getNote(original.id)).copy(title = "用户恢复后的标题", document = document("用户恢复后的正文")))
        val restored = db.notes().get(original.id)!!
        AgentReviewStore(db).reject(original.id)
        assertEquals(restored, db.notes().get(original.id))
        store.toolExecutor.execute("run", read("again", original.id)); store.toolExecutor.execute("run", delete("delete-again", restored))
        db.notebooks().deleteById("one")
        AgentReviewStore(db).reject(original.id)
        assertNull(db.notes().get(original.id)!!.notebookId)
        assertNull(db.notes().get(original.id)!!.deletedAtEpochMs)
        assertEquals(restored.documentJson, db.notes().get(original.id)!!.documentJson)
    } }

    @Test fun editThenDeletionRejectIsAtomicWhenEarlierEditConflicts() = runBlocking { fixture { db, store, library ->
        store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
        val original = note("note", null)
        db.notes().upsert(original)
        AgentReviewStore(db).applyEdit("run", "edit", original.editBase(), AgentEditableContent(original.title, document("Agent 正文")))
        val edited = db.notes().get(original.id)!!
        store.toolExecutor.execute("run", read("read", original.id)); store.toolExecutor.execute("run", delete("delete", edited))
        library.saveNote(requireNotNull(library.getNote(original.id)).copy(title = original.title, document = document("用户改写这段")))
        val current = db.notes().get(original.id)!!
        assertTrue(AgentReviewStore(db).reject(original.id) is AgentReviewResult.Conflict)
        assertEquals(current, db.notes().get(original.id))
        assertNotNull(current.deletedAtEpochMs)
    } }

    @Test fun acceptedCreationAndDeletionCanBeUndoneAndPermanentDeletionIsUnrecoverable() = runBlocking { fixture { db, store, library ->
        store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
        val id = (store.toolExecutor.execute("run", create("create")) as AgentToolResult.Finished).noteId()
        val reviews = AgentReviewStore(db)
        reviews.accept(id); reviews.undoAccepted(id)
        assertNotNull(db.notes().get(id)!!.deletedAtEpochMs)
        library.restoreNotes(listOf(id))
        val restored = db.notes().get(id)!!
        store.toolExecutor.execute("run", read("read", id)); store.toolExecutor.execute("run", delete("delete", restored))
        reviews.accept(id); reviews.undoAccepted(id)
        assertNull(db.notes().get(id)!!.deletedAtEpochMs)
        library.permanentlyDeleteNotes(listOf(id))
        assertEquals(AgentReviewStatus.Unrecoverable, reviews.detail(id)!!.review.status)
        assertTrue(db.agent().noteChanges(id).isEmpty())
    } }

    @Test fun creationRollbackIncludesToolResultAndReview() = runBlocking { fixture { db, store, _ ->
        store.savePermissionFromUser(AgentPermission(AgentPermissionMode.FullAccess))
        val run = db.agent().run("run")!!
        try { db.useWriterConnection { it.immediateTransaction {
            store.toolExecutor.execute("run", create("rollback")); error("before outer commit")
        } }; fail("rollback") } catch (_: IllegalStateException) { }
        assertTrue(db.notes().getAll().isEmpty())
        assertNull(db.agent().toolEvent("run", "rollback"))
        assertEquals(run, db.agent().run("run"))
    } }

    // -- Functions

    private fun AgentToolResult.Finished.noteId() = Json.parseToJsonElement(result.content).jsonObject.getValue("note_id").jsonPrimitive.content
    private fun document(text: String) = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(text)))))
    private fun note(id: String, book: String?) = NoteEntity(id, book, "标题", document("原始正文").encodeToJson(), null, 0, 0, 0, "", 1, 1, null, null)
    private fun create(id: String, target: AgentCreateTarget? = null) = ModelToolCall(id, "create", Json.encodeToJsonElement(AgentCreateArguments("新建标题", document("原始正文").encodeToJson(), target)).jsonObject)
    private fun read(id: String, noteId: String) = ModelToolCall(id, "read", Json.encodeToJsonElement(AgentReadArguments(noteId)).jsonObject)
    private fun delete(id: String, note: NoteEntity) = ModelToolCall(id, "delete", Json.encodeToJsonElement(AgentDeleteArguments(note.id, note.agentVersion())).jsonObject)
    private suspend fun resume(db: XNoteDatabase) { db.agent().saveRun(db.agent().run("run")!!.copy(status = AgentRunStatus.Running)) }
    private suspend fun books(db: XNoteDatabase) { for (id in listOf("one", "two")) db.notebooks().upsert(NotebookEntity(id, id, 0, 1, 1)) }
    private suspend fun fixture(block: suspend (XNoteDatabase, AgentNoteStore, NoteLibrary) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = XNoteDatabase.createInMemory(context)
        try {
            db.agent().saveSegment(AgentSegmentEntity("segment", 1))
            db.agent().saveRun(AgentRunEntity("run", "segment", "user", "profile", 1, AgentRunStatus.Running, 1, 1))
            block(db, AgentNoteStore(db), NoteLibrary(db, AttachmentFileStore(File(context.cacheDir, "lifecycle-tool-test")), SystemEpochClock))
        } finally { db.close() }
    }
}
