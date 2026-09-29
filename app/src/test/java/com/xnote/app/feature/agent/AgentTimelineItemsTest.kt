package com.xnote.app.feature.agent

import com.xnote.app.data.db.AgentMessageEntity
import com.xnote.app.data.db.AgentToolEventEntity
import com.xnote.app.domain.agent.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentTimelineItemsTest {
    @Test fun pendingAndCompletedToolsUseTheSamePositionAfterDatabaseReload() {
        val call = ModelToolCall("read-1", "read", buildJsonObject {})
        val reply = message("reply", AgentMessageRole.Assistant, "").copy(
            modelJson = Json.encodeToString(ModelMessage(AgentMessageRole.Assistant, calls = listOf(call))))
        val event = AgentToolEventEntity("event", "run", "read-1", "read", "{}", null,
            AgentToolStatus.Requested, 0, 2)
        val user = message("user", AgentMessageRole.User)
        val pending = agentTimelineItems(listOf(user, reply), listOf(event), emptySet())
        assertEquals(listOf("user", "tool:event"), pending.map { it.key })

        val result = message("result", AgentMessageRole.Tool).copy(modelJson = Json.encodeToString(
            ModelMessage(AgentMessageRole.Tool, results = listOf(ModelToolResult("read-1", "read", "ok")))))
        val completed = agentTimelineItems(listOf(user, reply, result, message("answer", AgentMessageRole.Assistant)),
            listOf(event.copy(status = AgentToolStatus.Committed, resultJson = "{}")), emptySet())
        assertEquals(listOf("user", "tool:event", "answer"), completed.map { it.key })
        assertEquals(AgentToolStatus.Committed, (completed[1] as AgentTimelineItem.Tool).event.status)
    }

    @Test fun groupsOnlyAdjacentMessagesFromTheSameSpeakerAndTopic() {
        val first = AgentTimelineItem.Message(message("one", AgentMessageRole.User))
        val next = AgentTimelineItem.Message(message("two", AgentMessageRole.User).copy(createdAtEpochMs = 20_000))
        assertTrue(joinsMessageGroup(first, next))
        assertFalse(joinsMessageGroup(first, next.copy(message = next.message.copy(role = AgentMessageRole.Assistant))))
        assertFalse(joinsMessageGroup(first, next.copy(message = next.message.copy(segmentId = "new"))))
        assertFalse(joinsMessageGroup(first, next.copy(message = next.message.copy(createdAtEpochMs = 120_000))))
        assertFalse(joinsMessageGroup(first, null))
    }

    @Test fun excludesQueuedMessagesAndInternalRequestsButKeepsTopicBoundaries() {
        val items = agentTimelineItems(listOf(
            message("queued", AgentMessageRole.User),
            message("request", AgentMessageRole.Event, "模型请求"),
            message("topic", AgentMessageRole.Event, "开始新话题"),
        ), emptyList(), setOf("queued"))
        assertEquals(listOf("topic"), items.map { it.key })
    }

    @Test fun keepsTheAttachmentOwnerEvenWhenTheToolResponseHasNoText() {
        val reply = message("file-reply", AgentMessageRole.Assistant, "").copy(
            modelJson = Json.encodeToString(ModelMessage(AgentMessageRole.Assistant,
                calls = listOf(ModelToolCall("export", "file_create", buildJsonObject {})))))
        val items = agentTimelineItems(listOf(reply), emptyList(), emptySet(), setOf(reply.id))
        assertEquals(listOf("file-reply"), items.map { it.key })
    }

    // -- Functions

    private fun message(id: String, role: AgentMessageRole, text: String = "消息") = AgentMessageEntity(
        id = id, segmentId = "topic", runId = "run", role = role, text = text,
        status = AgentMessageStatus.Complete, createdAtEpochMs = 1,
    )
}
