package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentNoteMemoryTest {
    @Test fun shortNotesUseRawTextAndEditingInvalidatesImmediately() = runBlocking {
        fixture { db, profiles ->
            val store = AgentNoteMemoryStore(db)
            db.notes().upsert(note("高铁行程"))
            assertFalse(store.process(profiles, client { error("Short notes do not need a model") }))
            assertEquals("raw", db.noteMemory().get("note")!!.status)
            assertEquals(1, store.search(reader(), "高铁").size)
            db.notes().upsert(note("轮船行程").copy(updatedAtEpochMs = 2))
            assertNull(db.noteMemory().get("note"))
            assertTrue(db.noteMemory().search("高", listOf("note")).isEmpty())
            assertTrue(store.search(reader(), "高铁").isEmpty())
            assertTrue(store.search(reader(), "轮船").single().text.contains("轮船"))
            assertTrue(db.memory().usage(0).isEmpty())
        }
    }

    @Test fun summariesWaitForStabilityAreToollessAndDeduplicated() = runBlocking {
        fixture { db, profiles ->
            var now = 100_000L
            val store = AgentNoteMemoryStore(db) { now }
            db.notes().upsert(note("旅行计划。".repeat(400)))
            var calls = 0
            val model = client { request ->
                calls++; assertTrue(request.tools.isEmpty())
                emit(ModelEvent.Text(summary())); emit(ModelEvent.Usage(90, 30)); emit(ModelEvent.Finished(ModelFinish.Complete))
            }
            assertTrue(store.process(profiles, model)); assertEquals(0, calls)
            now += AgentNoteMemoryLimits.StableMs
            assertFalse(store.process(profiles, model)); assertFalse(store.process(profiles, model))
            assertEquals(1, calls)
            assertEquals("complete", db.noteMemory().get("note")!!.status)
            assertEquals("乘坐高铁", store.search(reader(), "高铁").single().text)
            assertEquals(90L, db.memory().usage(0).single().inputTokens)
        }
    }

    @Test fun clearingOrEditingDuringSummaryCannotPublishOldContent() = runBlocking {
        fixture { db, profiles ->
            var now = 100_000L
            val store = AgentNoteMemoryStore(db) { now }
            db.notes().upsert(note("旧资料".repeat(800)))
            store.sweep(profiles); now += AgentNoteMemoryLimits.StableMs
            store.process(profiles, client {
                store.clear()
                emit(ModelEvent.Text(summary())); emit(ModelEvent.Finished(ModelFinish.Complete))
            })
            assertTrue(db.noteMemory().all().isEmpty())
            store.sweep(profiles); now += AgentNoteMemoryLimits.StableMs
            store.process(profiles, client {
                db.notes().upsert(note("新正文").copy(updatedAtEpochMs = now))
                emit(ModelEvent.Text(summary())); emit(ModelEvent.Finished(ModelFinish.Complete))
            })
            assertNull(db.noteMemory().get("note"))
            assertTrue(store.search(reader(), "高铁").isEmpty())
            assertEquals("新正文", store.search(reader(), "新正文").single().text)
        }
    }

    @Test fun revokedTrashAndPermanentDeletionRemoveAllSearchResults() = runBlocking {
        fixture { db, profiles ->
            val store = AgentNoteMemoryStore(db)
            db.notes().upsert(note("高铁")); store.sweep(profiles)
            AgentPermissionStore(db).saveFromUser(AgentPermission())
            assertTrue(store.search(reader(), "高铁").isEmpty())
            store.sweep(profiles); assertTrue(db.noteMemory().all().isEmpty())
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Read, AgentScope.All))
            store.sweep(profiles)
            db.notes().upsert(note("高铁").copy(deletedAtEpochMs = 9))
            assertTrue(store.search(reader(), "高铁").isEmpty()); assertTrue(db.noteMemory().all().isEmpty())
            db.notes().upsert(note("高铁")); store.sweep(profiles)
            db.notes().deleteByIds(listOf("note"))
            assertTrue(db.noteMemory().all().isEmpty()); assertTrue(db.noteMemory().search("高", listOf("note")).isEmpty())
        }
    }

    @Test fun revokedDuringGenerationDiscardsSummaryAndAuthenticationNeverRetries() = runBlocking {
        fixture { db, profiles ->
            var now = 100_000L
            val store = AgentNoteMemoryStore(db) { now }
            db.notes().upsert(note("正文".repeat(1000))); store.sweep(profiles); now += AgentNoteMemoryLimits.StableMs
            store.process(profiles, client {
                AgentPermissionStore(db).saveFromUser(AgentPermission())
                emit(ModelEvent.Text(summary())); emit(ModelEvent.Finished(ModelFinish.Complete))
            })
            assertNotEquals("complete", db.noteMemory().get("note")!!.status)
            store.sweep(profiles)
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Read, AgentScope.All))
            store.sweep(profiles); now += AgentNoteMemoryLimits.StableMs
            var calls = 0
            val model = client { calls++; throw ModelException(ModelError.Authentication) }
            assertFalse(store.process(profiles, model)); assertFalse(store.process(profiles, model)); assertEquals(1, calls)
        }
    }

    // -- Functions

    private fun note(text: String) = NoteEntity("note", null, "旅行", NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(text))))).encodeToJson(), null, 0, 0, 0, text, 1, 1, null, null)
    private fun reader() = AgentRunEntity("reader", "current", "user", "profile", 1, AgentRunStatus.Running, 1, 1)
    private fun summary() = Json.encodeToString(AgentNoteSummary("乘坐高铁", listOf("交通选择"), listOf("高铁")))
    private fun client(events: suspend FlowCollector<ModelEvent>.(ModelRequest) -> Unit) = object : ModelClient {
        override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest): Flow<ModelEvent> = flow { events(request) }
    }
    private suspend fun fixture(block: suspend (XNoteDatabase, ModelProfileStore) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = XNoteDatabase.createInMemory(context)
        val profiles = ModelProfileStore(db, AndroidModelCredentialStore(context))
        try {
            profiles.save(ModelProfile("profile", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "test-key")
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Read, AgentScope.All))
            block(db, profiles)
        } finally { profiles.delete("profile"); db.close() }
    }
}
