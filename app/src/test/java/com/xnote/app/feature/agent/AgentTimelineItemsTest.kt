package com.xnote.app.feature.agent

import com.xnote.app.data.db.AgentMessageEntity
import com.xnote.app.data.db.AgentRunEntity
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
        val pending = agentTimelineItems(listOf(user, reply), listOf(event), listOf(run(AgentRunStatus.WaitingPermission)), emptySet())
        assertEquals(listOf("user", "task:run"), pending.map { it.key })
        assertEquals(listOf("tool:event"), (pending.last() as AgentTimelineItem.Task).process.map { it.key })

        val result = message("result", AgentMessageRole.Tool).copy(modelJson = Json.encodeToString(
            ModelMessage(AgentMessageRole.Tool, results = listOf(ModelToolResult("read-1", "read", "ok")))))
        val completed = agentTimelineItems(listOf(user, reply, result, message("answer", AgentMessageRole.Assistant).copy(isFinal = true)),
            listOf(event.copy(status = AgentToolStatus.Committed, resultJson = "{}")), listOf(run(AgentRunStatus.Complete)), emptySet())
        assertEquals(listOf("user", "task:run"), completed.map { it.key })
        val task = completed[1] as AgentTimelineItem.Task
        assertTrue(task.finished)
        assertEquals("answer", task.ending?.id)
        assertEquals(AgentToolStatus.Committed, (task.process.single() as AgentTimelineItem.Tool).event.status)
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

    @Test fun attachmentsSnapshotsAndPendingStatusSeparateMessageGroups() {
        val first = AgentTimelineItem.Message(message("one", AgentMessageRole.User))
        val next = AgentTimelineItem.Message(message("two", AgentMessageRole.User).copy(createdAtEpochMs = 20_000))
        assertFalse(joinsMessageGroup(first, next, setOf("one")))
        assertFalse(joinsMessageGroup(first, next, setOf("two")))
        val snapshot = first.copy(message = first.message.copy(sourcesJson = "[{\"noteId\":\"note\"}]"))
        assertFalse(joinsMessageGroup(snapshot, next))
        assertFalse(joinsMessageGroup(first, next.copy(message = next.message.copy(sourcesJson = snapshot.message.sourcesJson))))
        assertFalse(joinsMessageGroup(first.copy(message = first.message.copy(status = AgentMessageStatus.Pending)), next))
        assertFalse(joinsMessageGroup(first, next.copy(message = next.message.copy(status = AgentMessageStatus.Pending))))
    }

    @Test fun messageGroupingHonorsTheExactTimeBoundaryAndChronologicalOrder() {
        val first = AgentTimelineItem.Message(message("one", AgentMessageRole.User).copy(createdAtEpochMs = 100_000))
        fun next(at: Long) = AgentTimelineItem.Message(message("two", AgentMessageRole.User).copy(createdAtEpochMs = at))
        assertTrue(joinsMessageGroup(first, next(100_000)))
        assertTrue(joinsMessageGroup(first, next(160_000)))
        assertFalse(joinsMessageGroup(first, next(160_001)))
        assertFalse(joinsMessageGroup(first, next(99_999)))
        assertFalse(joinsMessageGroup(first, next(100_001).copy(message = next(100_001).message.copy(text = ""))))
        assertFalse(joinsMessageGroup(first, AgentTimelineItem.Task(run(AgentRunStatus.Complete), emptyList(), null)))
    }

    @Test fun ordinaryProseToolsAndFinalEndingStayInOneTaskAcrossReload() {
        val read = ModelToolCall("read", "read", buildJsonObject {})
        val close = ModelToolCall("close", AgentFinishToolName, buildJsonObject {})
        val prose = message("thinking", AgentMessageRole.Assistant, "先看看笔记。")
        val reply = message("calls", AgentMessageRole.Assistant, "").copy(modelJson = Json.encodeToString(ModelMessage(AgentMessageRole.Assistant, calls = listOf(read))))
        val finalReply = message("closing", AgentMessageRole.Assistant, "").copy(modelJson = Json.encodeToString(ModelMessage(AgentMessageRole.Assistant, calls = listOf(close))))
        val ending = message("ending", AgentMessageRole.Assistant, "整理好了。\n可以看一下。").copy(isFinal = true)
        val event = AgentToolEventEntity("read-event", "run", "read", "read", "{}", "{}", AgentToolStatus.Committed, 0, 2)
        val finish = event.copy(id = "close-event", callId = "close", name = AgentFinishToolName)
        val items = agentTimelineItems(listOf(message("user", AgentMessageRole.User), prose, reply, finalReply, ending),
            listOf(event, finish), listOf(run(AgentRunStatus.Complete)), emptySet())
        val task = items.last() as AgentTimelineItem.Task
        assertEquals(listOf("thinking", "tool:read-event"), task.process.map { it.key })
        assertEquals(ending, task.ending)
        assertEquals("用时 1分2秒", agentTaskElapsedText(task.run))
        assertTrue(task.finished)
        assertFalse(task.copy(run = task.run.copy(status = AgentRunStatus.Running)).finished)
        assertFalse(joinsMessageGroup(AgentTimelineItem.Message(prose), AgentTimelineItem.Message(ending)))
    }

    @Test fun excludesQueuedMessagesAndInternalRequestsButKeepsTopicBoundaries() {
        val items = agentTimelineItems(listOf(
            message("queued", AgentMessageRole.User),
            message("request", AgentMessageRole.Event, "模型请求"),
            message("topic", AgentMessageRole.Event, "开始新话题"),
        ), emptyList(), emptyList(), setOf("queued"))
        assertEquals(listOf("topic"), items.map { it.key })
    }

    @Test fun keepsTheAttachmentOwnerEvenWhenTheToolResponseHasNoText() {
        val reply = message("file-reply", AgentMessageRole.Assistant, "").copy(
            modelJson = Json.encodeToString(ModelMessage(AgentMessageRole.Assistant,
                calls = listOf(ModelToolCall("export", "file_create", buildJsonObject {})))))
        val items = agentTimelineItems(listOf(reply), emptyList(), emptyList(), emptySet(), setOf(reply.id))
        assertEquals(listOf("file-reply"), items.map { it.key })
    }

    // -- Functions

    private fun run(status: AgentRunStatus) = AgentRunEntity("run", "topic", "user", "profile", 1, status, 1000, 63000)

    private fun message(id: String, role: AgentMessageRole, text: String = "消息") = AgentMessageEntity(
        id = id, segmentId = "topic", runId = "run", role = role, text = text,
        status = AgentMessageStatus.Complete, createdAtEpochMs = 1,
    )
}
