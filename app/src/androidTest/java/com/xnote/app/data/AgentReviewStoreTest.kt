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
import com.xnote.app.domain.model.EpochClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

// -- Tests

class AgentReviewStoreTest {
    @Test fun appliesImmediatelyAndAccumulatesAcrossRunsWhilePreservingUserEditsOnRejection() = runBlocking {
        fixture { db, store, library ->
            val base = db.notes().get("note")!!
            val first = store.applyEdit("run", "first", base, content("A middle end")) as AgentReviewResult.Applied
            assertEquals("A middle end", text(db.notes().get("note")!!))
            library.saveNoteContent("note", "用户标题", document("A 用户正文 end"))
            val latest = db.notes().get("note")!!
            db.agent().saveRun(db.agent().run("run")!!.copy(id = "next-run"))
            val second = store.applyEdit("next-run", "second", latest, content("A 用户正文 Z").copy(title = "用户标题")) as AgentReviewResult.Applied
            assertEquals(first.reviewId, second.reviewId)
            assertEquals(2, db.agent().reviewChanges(first.reviewId).size)
            assertEquals(listOf(AgentChangeOrigin.Agent, AgentChangeOrigin.User, AgentChangeOrigin.Agent), db.agent().noteChanges("note").map { it.origin })
            assertTrue(store.reject("note") is AgentReviewResult.Applied)
            assertEquals("start 用户正文 end", text(db.notes().get("note")!!))
            assertEquals("用户标题", db.notes().get("note")!!.title)
            assertEquals(AgentReviewStatus.Rejected, db.agent().reviews("note").single().status)
        }
    }

    @Test fun staleBaseMergesIndependentUserChangesButSamePlaceConflictsBeforeSaving() = runBlocking {
        fixture { db, store, library ->
            val base = db.notes().get("note")!!
            library.saveNoteContent("note", "用户标题", document("start user end"))
            assertTrue(store.applyEdit("run", "merge", base, content("A middle end")) is AgentReviewResult.Applied)
            assertEquals("A user end", text(db.notes().get("note")!!))
            assertEquals("用户标题", db.notes().get("note")!!.title)
            val beforeConflict = db.notes().get("note")!!
            assertTrue(store.applyEdit("run", "conflict", base, content("start Agent end")) is AgentReviewResult.Conflict)
            assertEquals(beforeConflict, db.notes().get("note"))
            assertNull(db.agent().committedChange("run", "conflict"))
        }
    }

    @Test fun wholeBatchRollbackDoesNotApplyEarlierSuccessfulInverseWhenAnotherInverseConflicts() = runBlocking {
        fixture { db, store, library ->
            store.applyEdit("run", "first", db.notes().get("note")!!, content("A middle end"))
            store.applyEdit("run", "second", db.notes().get("note")!!, content("A middle Z"))
            library.saveNoteContent("note", "", document("用户 middle Z"))
            val current = db.notes().get("note")!!
            assertTrue(store.reject("note") is AgentReviewResult.Conflict)
            assertEquals(current, db.notes().get("note"))
            assertEquals(AgentReviewStatus.Conflict, db.agent().reviews("note").single().status)
            store.accept("note")
            assertEquals(current, db.notes().get("note"))
            assertEquals(AgentReviewStatus.Accepted, db.agent().reviews("note").single().status)
        }
    }

    @Test fun acceptanceOnlyConfirmsAndUndoProtectsLaterUserEditsWithinThirtyDays() = runBlocking {
        fixture { db, _, library ->
            var time = 10L
            val store = AgentReviewStore(db) { time }
            store.applyEdit("run", "first", db.notes().get("note")!!, content("A middle end"))
            val applied = db.notes().get("note")!!
            store.accept("note")
            assertEquals(applied, db.notes().get("note"))
            library.saveNoteContent("note", "", document("A user end"))
            time += AgentAcceptedUndoRetentionMs - 1
            assertTrue(store.undoAccepted("note") is AgentReviewResult.Applied)
            assertEquals("start user end", text(db.notes().get("note")!!))
            val second = store.applyEdit("run", "second", db.notes().get("note")!!, content("B user end")) as AgentReviewResult.Applied
            assertNotEquals(db.agent().reviews("note").first().id, second.reviewId)
            store.accept("note")
            time += AgentAcceptedUndoRetentionMs
            val current = db.notes().get("note")!!
            try { store.undoAccepted("note"); fail("expired undo") } catch (_: IllegalStateException) { }
            assertEquals(current, db.notes().get("note"))
        }
    }

    @Test fun permissionMediaAndIdempotencyBoundariesAreEnforced() = runBlocking {
        fixture { db, store, _ ->
            val base = db.notes().get("note")!!
            for (level in listOf(AgentPermissionLevel.None, AgentPermissionLevel.Read)) {
                AgentPermissionStore(db).saveFromUser(AgentPermission(level, AgentScope.All))
                assertEquals(AgentReviewResult.PermissionRequired, store.applyEdit("run", "denied", base, content("Agent")))
            }
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.All))
            val first = store.applyEdit("run", "once", base, content("A middle end"))
            assertEquals(first, store.applyEdit("run", "once", base, content("A middle end")))
            assertEquals(1, db.agent().noteChanges("note").size)
            val image = ImageBlock("image", "asset")
            val current = db.notes().get("note")!!.copy(documentJson = NoteDocument(blocks = listOf(image, TextBlock("body"))).encodeToJson())
            db.notes().upsert(current)
            try { store.applyEdit("run", "media", current, content("removed image")); fail("protected media") } catch (_: IllegalArgumentException) { }
            assertEquals(current, db.notes().get("note"))
            AgentPermissionStore(db).saveFromUser(AgentPermission())
            assertEquals(AgentReviewResult.PermissionRequired, store.applyEdit("run", "once", base, content("A middle end")))
            store.accept("note") // Local review remains allowed after revocation.
        }
    }

    @Test fun outerTransactionRollbackLeavesNeitherBodyNorReviewNorChange() = runBlocking {
        fixture { db, store, _ ->
            val before = db.notes().get("note")!!
            try {
                db.useWriterConnection { writer -> writer.immediateTransaction {
                    store.applyEdit("run", "rollback", before, content("A middle end"))
                    error("simulate failure before commit")
                } }
            } catch (_: IllegalStateException) { }
            assertEquals(before, db.notes().get("note"))
            assertTrue(db.agent().reviews("note").isEmpty())
            assertTrue(db.agent().noteChanges("note").isEmpty())
        }
    }

    @Test fun permanentDeletionLeavesUnrecoverableCardAndReleasesChangeMedia() = runBlocking {
        fixture { db, store, library ->
            val base = db.notes().get("note")!!.copy(documentJson = NoteDocument(blocks = listOf(TextBlock("body"), ImageBlock("image", "asset"))).encodeToJson())
            db.notes().upsert(base)
            val next = AgentEditableContent("Agent标题", decodeNoteDocument(base.documentJson))
            store.applyEdit("run", "media-reference", base, next)
            assertEquals(listOf("asset"), db.agent().referencedAttachmentIds())
            library.permanentlyDeleteNotes(listOf("note"))
            assertEquals(AgentReviewStatus.Unrecoverable, db.agent().reviews("note").single().status)
            assertTrue(db.agent().noteChanges("note").isEmpty())
            assertTrue(db.agent().referencedAttachmentIds().isEmpty())
            assertNull(store.detail("note")!!.current)
        }
    }

    @Test fun diskReopenRetainsPendingReviewAndAtomicInverse() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "agent-review-reopen-test.db"
        context.deleteDatabase(name)
        var db = XNoteDatabase.create(context, name)
        try {
            val note = NoteEntity("note", null, "", document("start middle end").encodeToJson(), null, 0, 0, 0, "", 1, 1, null, null)
            db.notes().upsert(note)
            db.agent().saveRun(AgentRunEntity("run", "segment", "user", "profile", 1, AgentRunStatus.Running, 1, 1))
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.All))
            AgentReviewStore(db).applyEdit("run", "durable", note, content("A middle end"))
            db.close()
            db = XNoteDatabase.create(context, name)
            val restored = AgentReviewStore(db)
            assertEquals(AgentReviewStatus.Pending, restored.detail("note")!!.review.status)
            assertEquals("A middle end", text(db.notes().get("note")!!))
            assertTrue(restored.reject("note") is AgentReviewResult.Applied)
            assertEquals("start middle end", text(db.notes().get("note")!!))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun laterRejectedBatchDoesNotHideLastAcceptedUndoAndUndoCannotWalkBackOlderBatches() = runBlocking {
        fixture { db, store, _ ->
            store.applyEdit("run", "one", db.notes().get("note")!!, content("A middle end"))
            store.accept("note")
            store.applyEdit("run", "two", db.notes().get("note")!!, content("A middle Z"))
            store.reject("note")
            assertNotNull(store.detail("note")!!.undoReview)
            assertTrue(store.undoAccepted("note") is AgentReviewResult.Applied)
            assertEquals("start middle end", text(db.notes().get("note")!!))
            assertNull(store.detail("note")!!.undoReview)
            try { store.undoAccepted("note"); fail("already undone") } catch (_: IllegalStateException) { }
        }
    }

    @Test fun staleSelectionAndUnchangedProposalDoNotCreateChanges() = runBlocking {
        fixture { db, store, library ->
            val base = db.notes().get("note")!!
            assertTrue(store.applyEdit("run", "no-change", base, content("start middle end")) is AgentReviewResult.Unchanged)
            library.saveNoteContent("note", "用户标题", document("start middle end"))
            assertTrue(store.applyEdit("run", "selection", base, content("A middle end"), AgentSelection(base.agentVersion(), "body", 0, 5)) is AgentReviewResult.Conflict)
            assertTrue(db.agent().reviews("note").isEmpty())
        }
    }

    @Test fun clockCorrectionDoesNotReorderInverseOperationsOrTheMostRecentlyAcceptedBatch() = runBlocking {
        fixture { db, _, _ ->
            var time = 100L
            val store = AgentReviewStore(db) { time }
            store.applyEdit("run", "first", db.notes().get("note")!!, content("A middle end"))
            time = 50L
            store.applyEdit("run", "second", db.notes().get("note")!!, content("B middle end"))
            assertTrue(store.reject("note") is AgentReviewResult.Applied)
            assertEquals("start middle end", text(db.notes().get("note")!!))
            time = 100L
            store.applyEdit("run", "older-batch", db.notes().get("note")!!, content("A middle end"))
            store.accept("note")
            time = 50L
            store.applyEdit("run", "newer-batch", db.notes().get("note")!!, content("A middle Z"))
            store.accept("note")
            assertTrue(store.undoAccepted("note") is AgentReviewResult.Applied)
            assertEquals("A middle end", text(db.notes().get("note")!!))
        }
    }

    // -- Functions

    private fun document(text: String) = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(text)))))
    private fun content(text: String) = AgentEditableContent("", document(text))
    private fun text(note: NoteEntity) = (decodeNoteDocument(note.documentJson).blocks.first() as TextBlock).inlines.plainText()
    private suspend fun fixture(block: suspend (XNoteDatabase, AgentReviewStore, NoteLibrary) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = XNoteDatabase.createInMemory(context)
        var time = 100L
        val clock = EpochClock { ++time }
        val library = NoteLibrary(db, AttachmentFileStore(File(context.cacheDir, "agent-review-tests")), clock)
        try {
            db.notes().upsert(NoteEntity("note", null, "", document("start middle end").encodeToJson(), null, 0, 0, 0, "", 1, 1, null, null))
            db.agent().saveRun(AgentRunEntity("run", "segment", "user", "profile", 1, AgentRunStatus.Running, 1, 1))
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.All))
            block(db, AgentReviewStore(db, clock::nowMs), library)
        } finally { db.close() }
    }
}
