package com.xnote.app.data

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xnote.app.data.agent.AgentPermissionStore
import com.xnote.app.data.db.*
import com.xnote.app.data.settings.AppSettingsStore
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.emptyNoteDocument
import com.xnote.app.domain.document.encodeToJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentFoundationTest {
    @Test fun versionEightMigrationPreservesAgentFactsAndAddsReadReferences() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "agent-v8-migration-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val document = emptyNoteDocument().encodeToJson()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.xnote.app.data.db.XNoteDatabase/8.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        try {
            BundledSQLiteDriver().open(path.absolutePath).use { connection ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    connection.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (j in 0 until indices.length()) connection.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                val queries = schema.getJSONArray("setupQueries")
                for (i in 0 until queries.length()) connection.execSQL(queries.getString(i))
                connection.prepare("INSERT INTO notes VALUES ('note', NULL, '保留标题', ?, NULL, 1, 0, 0, '', 100, 200, NULL, NULL)").use { it.bindText(1, document); it.step() }
                connection.prepare("INSERT INTO agent_snapshots VALUES ('snapshot', 'note', 'v8', '保留标题', ?, NULL, NULL, 200)").use { it.bindText(1, document); it.step() }
                connection.execSQL("INSERT INTO agent_snapshot_refs VALUES ('message', 'snapshot', 'segment', 0)")
                connection.execSQL("INSERT INTO agent_segments VALUES ('segment', 100, NULL, NULL)")
                connection.execSQL("INSERT INTO agent_runs (id, segmentId, userMessageId, profileId, profileVersion, status, createdAtEpochMs, updatedAtEpochMs) VALUES ('run', 'segment', 'message', 'profile', 1, 'Interrupted', 100, 200)")
                connection.execSQL("INSERT INTO agent_tool_events (id, runId, callId, name, argumentsJson, resultJson, status, permissionRevision, createdAtEpochMs) VALUES ('event', 'run', 'read', 'read', '{}', '{}', 'Committed', 0, 200)")
                connection.execSQL("INSERT INTO agent_reviews VALUES ('review', 'note', 'Pending', 200, NULL)")
                connection.execSQL("PRAGMA user_version = 8")
            }
            XNoteDatabase.create(context, name).withClose { db ->
                assertEquals(document, db.notes().get("note")!!.documentJson)
                assertEquals(AgentRunStatus.Interrupted, db.agent().run("run")!!.status)
                assertEquals(AgentReviewStatus.Pending, db.agent().reviews("note").single().status)
                assertEquals("snapshot", db.agent().snapshotRefs("message").single().snapshotId)
                assertEquals(AgentToolStatus.Committed, db.agent().toolEvent("run", "read")!!.status)
                db.agent().insertToolSnapshotRef(AgentToolSnapshotRefEntity("event", "snapshot"))
                assertEquals(document, db.agent().readSnapshot("segment", "note", "v8")!!.documentJson)
                db.agent().deleteSnapshotRefs("message")
                db.agent().deleteToolEvents("run")
                db.agent().deleteUnusedSnapshots()
                assertNull(db.agent().snapshot("snapshot"))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun versionThreeMigrationPreservesFactsFilesAndSettings() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "agent-migration-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        val document = emptyNoteDocument().encodeToJson()
        val attachment = java.io.File(context.filesDir, "migration-attachment-${System.nanoTime()}")
        attachment.writeBytes(byteArrayOf(1, 7, 9))
        val settings = AppSettingsStore(context)
        val previous = settings.settings.first()
        settings.setMarkdownShortcutsEnabled(!previous.markdownShortcutsEnabled)
        val expectedSettings = settings.settings.first()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.xnote.app.data.db.XNoteDatabase/3.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        try {
            BundledSQLiteDriver().open(path.absolutePath).use { connection ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    connection.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (j in 0 until indices.length()) {
                        connection.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val queries = schema.getJSONArray("setupQueries")
                for (i in 0 until queries.length()) connection.execSQL(queries.getString(i))
                connection.execSQL("INSERT INTO notebooks VALUES ('book', '保留本', 7, 100, 200, 'gold', 'notebook')")
                connection.prepare("INSERT INTO notes VALUES ('note', 'book', '保留标题', ?, NULL, 1, 0, 0, '', 100, 200, NULL, NULL)").use {
                    it.bindText(1, document); it.step()
                }
                connection.prepare("INSERT INTO attachments VALUES ('file', 'image', 'image/png', NULL, ?, 3, 1, 1, 100)").use {
                    it.bindText(1, attachment.name); it.step()
                }
                connection.execSQL("PRAGMA user_version = 3")
            }
            XNoteDatabase.create(context, name).withClose { database ->
                assertEquals(document, database.notes().get("note")?.documentJson)
                assertEquals("保留标题", database.notes().get("note")?.title)
                assertEquals("保留本", database.notebooks().get("book")?.name)
                assertEquals(attachment.name, database.attachments().get("file")?.relativePath)
                assertArrayEquals(byteArrayOf(1, 7, 9), attachment.readBytes())
                assertEquals(expectedSettings, AppSettingsStore(context).settings.first())
                assertEquals(AgentPermission(), AgentPermissionStore(database).current())
                assertTrue(database.agent().messages().isEmpty())
            }
        } finally {
            context.deleteDatabase(name)
            attachment.delete()
            settings.setMarkdownShortcutsEnabled(previous.markdownShortcutsEnabled)
        }
    }

    @Test fun factsAndPermissionRevisionSurviveReopen() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "agent-facts-${System.nanoTime()}.db"
        try {
            XNoteDatabase.create(context, name).withClose { db ->
                val store = AgentPermissionStore(db)
                assertEquals(1L, store.saveFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.All)).revision)
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
