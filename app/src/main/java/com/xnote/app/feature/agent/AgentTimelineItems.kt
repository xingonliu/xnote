package com.xnote.app.feature.agent

import com.xnote.app.data.db.AgentMessageEntity
import com.xnote.app.data.db.AgentRunEntity
import com.xnote.app.data.db.AgentToolEventEntity
import com.xnote.app.domain.agent.*
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

    data class Task(val run: AgentRunEntity, val process: List<AgentTimelineItem>, val ending: AgentMessageEntity?) : AgentTimelineItem {
        override val key = "task:${run.id}"
        val finished: Boolean get() = run.status in setOf(AgentRunStatus.Complete, AgentRunStatus.Failed, AgentRunStatus.Cancelled)
    }
}

// -- Constants

private const val MessageGroupIntervalMs = 60_000L

// -- Functions

internal fun agentTimelineItems(
    messages: List<AgentMessageEntity>,
    tools: List<AgentToolEventEntity>,
    runs: List<AgentRunEntity>,
    queuedMessageIds: Set<String>,
    fileMessageIds: Set<String> = emptySet(),
): List<AgentTimelineItem> {
    val visible = messages.filter { it.id !in queuedMessageIds }
    val events = tools.filter { it.name != AgentFinishToolName }.associateBy { it.runId to it.callId }
    val emitted = mutableSetOf<String>()
    val lastInRun = visible.filter { it.runId != null }.groupBy { it.runId }.mapValues { it.value.last().id }
    val ordered = buildList {
        visible.forEach { message ->
            val toolOnlyReply = message.role == AgentMessageRole.Assistant && message.text.isBlank() &&
                message.status == AgentMessageStatus.Complete && message.modelJson != null && message.id !in fileMessageIds
            if (!toolOnlyReply && message.role != AgentMessageRole.Tool && !(message.role == AgentMessageRole.Event &&
                    (message.text == "模型请求" || message.text.contains("请单独调用 finish_task")))) {
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
                events.values.filter { it.runId == message.runId && it.id !in emitted }
                    .sortedWith(compareBy({ it.createdAtEpochMs }, { it.id })).forEach {
                        emitted += it.id
                        add(AgentTimelineItem.Tool(it))
                    }
            }
        }
    }
    val byRun = runs.associateBy { it.id }
    fun processRun(item: AgentTimelineItem): String? = when (item) {
        is AgentTimelineItem.Tool -> item.event.runId
        is AgentTimelineItem.Message -> item.message.runId?.takeIf { item.message.role != AgentMessageRole.User }
        is AgentTimelineItem.Task -> null
    }
    val process = ordered.filter { processRun(it) in byRun }.groupBy(::processRun)
    val last = process.mapValues { it.value.last().key }
    return buildList {
        ordered.forEach { item ->
            val runId = processRun(item)
            val run = byRun[runId]
            if (run == null) add(item)
            else if (last[runId] == item.key) {
                val items = process.getValue(runId)
                val ending = items.filterIsInstance<AgentTimelineItem.Message>().lastOrNull { it.message.isFinal }?.message
                add(AgentTimelineItem.Task(run, items.filterNot { it is AgentTimelineItem.Message && it.message.isFinal }, ending))
            }
        }
    }
}

internal fun joinsMessageGroup(previous: AgentTimelineItem?, next: AgentTimelineItem?, fileMessageIds: Set<String> = emptySet()): Boolean {
    val first = (previous as? AgentTimelineItem.Message)?.message ?: return false
    val second = (next as? AgentTimelineItem.Message)?.message ?: return false
    return first.role == AgentMessageRole.User && second.role == AgentMessageRole.User && first.segmentId == second.segmentId &&
        first.text.isNotBlank() && second.text.isNotBlank() &&
        first.status == AgentMessageStatus.Complete && second.status == AgentMessageStatus.Complete &&
        first.sourcesJson == "[]" && second.sourcesJson == "[]" &&
        first.id !in fileMessageIds && second.id !in fileMessageIds &&
        second.createdAtEpochMs - first.createdAtEpochMs in 0..MessageGroupIntervalMs
}

internal fun agentTaskElapsedText(run: AgentRunEntity): String {
    val seconds = ((run.updatedAtEpochMs - run.createdAtEpochMs).coerceAtLeast(0) / 1000)
    val duration = if (seconds < 60) "${seconds}秒" else "${seconds / 60}分${seconds % 60}秒"
    return when (run.status) {
        AgentRunStatus.Failed -> "失败 · 用时 $duration"
        AgentRunStatus.Cancelled -> "已停止 · 用时 $duration"
        else -> "用时 $duration"
    }
}
