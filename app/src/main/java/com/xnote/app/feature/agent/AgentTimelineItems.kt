package com.xnote.app.feature.agent

import com.xnote.app.data.db.AgentMessageEntity
import com.xnote.app.data.db.AgentToolEventEntity
import com.xnote.app.domain.agent.AgentMessageRole
import com.xnote.app.domain.agent.AgentMessageStatus
import com.xnote.app.domain.agent.ModelMessage
import kotlinx.serialization.json.Json

// -- Type Definitions

internal sealed interface AgentTimelineItem {
    val key: String

    data class Message(val message: AgentMessageEntity) : AgentTimelineItem {
        override val key = message.id
    }

    data class Tool(val event: AgentToolEventEntity) : AgentTimelineItem {
        override val key = "tool:${event.id}"
    }
}

// -- Constants

private const val MessageGroupIntervalMs = 60_000L

// -- Functions

internal fun agentTimelineItems(
    messages: List<AgentMessageEntity>,
    tools: List<AgentToolEventEntity>,
    queuedMessageIds: Set<String>,
    fileMessageIds: Set<String> = emptySet(),
): List<AgentTimelineItem> {
    val visible = messages.filter { it.id !in queuedMessageIds }
    val events = tools.associateBy { it.runId to it.callId }
    val emitted = mutableSetOf<String>()
    val lastInRun = visible.filter { it.runId != null }.groupBy { it.runId }.mapValues { it.value.last().id }
    return buildList {
        visible.forEach { message ->
            val toolOnlyReply = message.role == AgentMessageRole.Assistant && message.text.isBlank() &&
                message.status == AgentMessageStatus.Complete && message.modelJson != null && message.id !in fileMessageIds
            if (!toolOnlyReply && message.role != AgentMessageRole.Tool && !(message.role == AgentMessageRole.Event && message.text == "模型请求")) {
                add(AgentTimelineItem.Message(message))
            }
            // Keep persisted calls beside their originating response, including calls awaiting approval.
            val model = message.modelJson?.let { Json.decodeFromString<ModelMessage>(it) }
            val callIds = model?.let { it.calls.map { call -> call.id } + it.results.map { result -> result.id } }.orEmpty()
            callIds.forEach { callId ->
                events[message.runId to callId]?.let { event ->
                    if (emitted.add(event.id)) add(AgentTimelineItem.Tool(event))
                }
            }
            if (lastInRun[message.runId] == message.id) {
                tools.filter { it.runId == message.runId && it.id !in emitted }
                    .sortedWith(compareBy({ it.createdAtEpochMs }, { it.id })).forEach {
                        emitted += it.id
                        add(AgentTimelineItem.Tool(it))
                    }
            }
        }
    }
}

internal fun joinsMessageGroup(previous: AgentTimelineItem?, next: AgentTimelineItem?): Boolean {
    val first = (previous as? AgentTimelineItem.Message)?.message ?: return false
    val second = (next as? AgentTimelineItem.Message)?.message ?: return false
    return first.role in setOf(AgentMessageRole.User, AgentMessageRole.Assistant) &&
        first.role == second.role && first.segmentId == second.segmentId &&
        first.text.isNotBlank() && second.text.isNotBlank() &&
        second.createdAtEpochMs - first.createdAtEpochMs in 0..MessageGroupIntervalMs
}
