package com.xnote.app.data

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.AgentPermissionStore
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentFoundationTest {
    @Test fun oldDatabaseIsRecreatedWithApprovalAsDefault() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "agent-recreate-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        try {
            BundledSQLiteDriver().open(path.absolutePath).use { connection ->
                connection.execSQL("CREATE TABLE obsolete_data (value TEXT)")
                connection.execSQL("INSERT INTO obsolete_data VALUES ('old')")
                connection.execSQL("PRAGMA user_version = 15")
            }
            XNoteDatabase.create(context, name).withClose { db ->
                assertTrue(db.notes().getAll().isEmpty())
                assertTrue(db.agent().messages().isEmpty())
                assertEquals(AgentPermissionMode.RequestApproval, AgentPermissionStore(db).current().mode)
            }
            BundledSQLiteDriver().open(path.absolutePath).use { connection ->
                connection.prepare("SELECT name FROM sqlite_master WHERE name = 'obsolete_data'").use { assertFalse(it.step()) }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun factsAndPermissionRevisionSurviveReopen() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "agent-facts-${System.nanoTime()}.db"
        try {
            XNoteDatabase.create(context, name).withClose { db ->
                val store = AgentPermissionStore(db)
                assertEquals(1L, store.saveFromUser(AgentPermission(AgentPermissionMode.FullAccess)).revision)
                assertEquals(2L, store.saveFromUser(AgentPermission()).revision)
                db.agent().saveSegment(AgentSegmentEntity("segment", 100))
                db.agent().insertMessage(AgentMessageEntity(id = "message", segmentId = "segment", runId = "run", role = AgentMessageRole.User, text = "保留输入", status = AgentMessageStatus.Complete, createdAtEpochMs = 100))
                db.agent().saveRun(AgentRunEntity("run", "segment", "message", "profile", 4, AgentRunStatus.Interrupted, 100, 101))
                db.agent().saveQueueItem(AgentQueueEntity("queue", "next", "profile", 4, 0, AgentQueueStatus.Paused, 100))
                db.agent().saveToolEvent(AgentToolEventEntity("event", "run", "call", "read", "{}", null, AgentToolStatus.Denied, 2, 100))
                db.agent().saveReview(AgentReviewEntity("review", "note", AgentReviewStatus.Pending, 100))
            }
            XNoteDatabase.create(context, name).withClose { db ->
                assertEquals(AgentPermission(revision = 2), AgentPermissionStore(db).current())
                assertEquals("保留输入", db.agent().messages().single().text)
                assertEquals(4L, db.agent().unfinishedRuns().single().profileVersion)
                assertEquals(AgentQueueStatus.Paused, db.agent().pendingQueue().single().status)
                assertEquals(AgentToolStatus.Denied, db.agent().toolEvent("run", "call")?.status)
                assertEquals("segment", db.agent().openSegment()?.id)
            }
        } finally { context.deleteDatabase(name) }
    }
}

// -- Functions

private inline fun <T> XNoteDatabase.withClose(block: (XNoteDatabase) -> T): T =
    try { block(this) } finally { close() }
