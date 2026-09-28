package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentHistoryMemoryTest {
    @Test fun largeChineseReadAutomaticallyPagesUnderTokenLimitWithoutLosingText() = runBlocking {
        fixture { db, run ->
            val body = "中文资料😀\"".repeat(600)
            val original = db.agent().message("user")!!
            db.agent().updateMessage(original.sequence, body, AgentMessageStatus.Complete)
            var cursor: String? = null
            val restored = StringBuilder()
            var pages = 0
            do {
                val response = execute(db, run, "page-${pages++}", "memory_read", buildJsonObject {
                    put("episodeId", "old"); put("limit", 6000); cursor?.let { put("cursor", it) }
                }).result.content
                assertTrue(estimatedAgentTokens(response) <= AgentMemoryToolLimits.PageTokens)
                val json = Json.parseToJsonElement(response).jsonObject
                json.getValue("messages").jsonArray.forEach { restored.append(it.jsonObject.getValue("text").jsonPrimitive.content) }
                cursor = json["cursor"]?.jsonPrimitive?.contentOrNull
            } while (cursor != null)
            assertEquals(body + "建议提前订票并保留返程时间。", restored.toString())
            assertTrue(pages > 1)
        }
    }

    @Test fun searchesRawHistoryWhenSummaryFailedAndPagesCompleteVisibleText() = runBlocking {
        fixture { db, run ->
            val search = execute(db, run, "search", "memory_search", buildJsonObject { put("query", "高铁") })
            val item = Json.parseToJsonElement(search.result.content).jsonObject.getValue("items").jsonArray.single().jsonObject
            assertEquals("old", item.getValue("episodeId").jsonPrimitive.content)
            assertTrue(item.getValue("excerpt").jsonPrimitive.content.contains("高铁"))
            var cursor: String? = null
            val chunks = StringBuilder()
            var pages = 0
            do {
                val result = execute(db, run, "read-${pages++}", "memory_read", buildJsonObject {
                    put("episodeId", "old"); put("limit", 10); cursor?.let { put("cursor", it) }
                })
                val json = Json.parseToJsonElement(result.result.content).jsonObject
                json.getValue("messages").jsonArray.forEach { chunks.append(it.jsonObject.getValue("text").jsonPrimitive.content) }
                cursor = json["cursor"]?.jsonPrimitive?.contentOrNull
            } while (cursor != null)
            assertEquals("周末乘高铁去杭州，请比较行程。建议提前订票并保留返程时间。", chunks.toString())
            assertTrue(pages > 1)
            assertTrue(db.agent().toolEvents(run.id).all { it.status == AgentToolStatus.Committed })
        }
    }

    @Test fun repeatedSearchAndPerRunLimitsAreEnforced() = runBlocking {
        fixture { db, run ->
            val arguments = buildJsonObject { put("query", "高铁") }
            execute(db, run, "one", "memory_search", arguments)
            assertTrue(execute(db, run, "two", "memory_search", arguments).result.content.contains("duplicate_memory_request"))
            repeat(5) { execute(db, run, "extra-$it", "memory_search", buildJsonObject { put("query", "不存在$it") }) }
            assertTrue(execute(db, run, "last", "memory_search", arguments).result.content.contains("memory_call_limit"))
        }
    }

    @Test fun deletionRevokesCachedToolResultsAndTransitiveReplies() = runBlocking {
        fixture { db, run ->
            val arguments = buildJsonObject { put("query", "高铁") }
            val result = execute(db, run, "search", "memory_search", arguments)
            assertTrue(result.sourceMessageIds.contains("user"))
            AgentMemoryProvenance(db).save("next-reply", result.sourceMessageIds)
            db.agent().insertMessage(AgentMessageEntity(id = "next-reply", segmentId = run.segmentId, runId = run.id,
                role = AgentMessageRole.Assistant, text = "用户周末去杭州", status = AgentMessageStatus.Complete, createdAtEpochMs = 10))
            AgentMemoryProvenance(db).save("later", listOf("next-reply"))
            db.agent().deleteMessage("user")
            assertFalse(AgentMemoryProvenance(db).canUse("later", run))
            val repeated = execute(db, run, "search", "memory_search", arguments)
            assertFalse(repeated.result.content.contains("杭州"))
            assertNull(AgentNoteStore(db).projectMessage(run.id, db.agent().message("next-reply")!!))
        }
    }

    @Test fun revokedSourcesProduceNoHitsAndChangingVisibilityInvalidatesCursor() = runBlocking {
        fixture { db, run ->
            val first = execute(db, run, "read", "memory_read", buildJsonObject { put("episodeId", "old"); put("limit", 5) })
            val cursor = Json.parseToJsonElement(first.result.content).jsonObject.getValue("cursor").jsonPrimitive.content
            db.agent().updateMessageContext("user", Json.encodeToString(listOf(AgentMessageSource("missing"))), null)
            val changed = execute(db, run, "read-next", "memory_read", buildJsonObject { put("episodeId", "old"); put("limit", 5); put("cursor", cursor) })
            assertTrue(changed.result.content.contains("invalid_arguments"))
            val search = execute(db, run, "search", "memory_search", buildJsonObject { put("query", "高铁") })
            assertTrue(Json.parseToJsonElement(search.result.content).jsonObject.getValue("items").jsonArray.isEmpty())
        }
    }

    // -- Functions

    private suspend fun execute(db: XNoteDatabase, run: AgentRunEntity, id: String, name: String, args: JsonObject) =
        AgentNoteStore(db).executeTool(run.id, ModelToolCall(id, name, args)) as AgentToolResult.Finished

    private suspend fun fixture(block: suspend (XNoteDatabase, AgentRunEntity) -> Unit) {
        val db = XNoteDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        try {
            db.agent().saveSegment(AgentSegmentEntity("old", 1))
            db.agent().saveRun(AgentRunEntity("old-run", "old", "user", "profile", 1, AgentRunStatus.Complete, 1, 3))
            db.agent().insertMessage(AgentMessageEntity(id = "user", segmentId = "old", runId = "old-run", role = AgentMessageRole.User,
                text = "周末乘高铁去杭州，请比较行程。", status = AgentMessageStatus.Complete, createdAtEpochMs = 1))
            db.agent().insertMessage(AgentMessageEntity(id = "answer", segmentId = "old", runId = "old-run", role = AgentMessageRole.Assistant,
                text = "建议提前订票并保留返程时间。", status = AgentMessageStatus.Complete, createdAtEpochMs = 2))
            AgentEpisodeStore(db).close(db.agent().segment("old")!!, "idle")
            val run = AgentRunEntity("current", "new", "request", "profile", 1, AgentRunStatus.Running, 4, 4)
            db.agent().saveSegment(AgentSegmentEntity("new", 4))
            db.agent().saveRun(run)
            block(db, run)
        } finally { db.close() }
    }
}
