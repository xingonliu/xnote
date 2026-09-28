package com.xnote.app.data.agent

import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*

// -- Type Definitions

data class AgentRequestContext(val plan: AgentContextPlan, val sources: List<AgentMessageSource>)
class AgentSourceAccessException : Exception("当前任务引用的笔记已不可访问，请调整权限或重新附加笔记。")

class AgentConversationContext(private val database: XNoteDatabase, private val notes: AgentNoteStore) {
    // -- Functions

    /** Called inside the request transaction; all current inputs and derived replies are reprojected together. */
    suspend fun prepare(run: AgentRunEntity, profile: ModelProfile): AgentRequestContext {
        val history = database.agent().messages()
        val episodes = AgentEpisodeStore(database)
        val recalled = episodes.recall(run, history.lastOrNull { it.runId == run.id && it.role == AgentMessageRole.User }?.text.orEmpty())
        val summarized = database.memory().episodes().map { it.segmentId }.toSet()
        val previousSegment = history.lastOrNull { it.segmentId != run.segmentId && it.runId != null }?.segmentId
        val turns = mutableListOf<AgentContextTurn>()
        val sources = mutableListOf<AgentMessageSource>()
        for ((previousId, exchange) in history.filter { it.runId != run.id && it.runId != null &&
            (it.segmentId == run.segmentId || (it.segmentId == previousSegment && it.segmentId !in summarized)) }.groupBy { it.runId }) {
            val previous = database.agent().run(checkNotNull(previousId)) ?: continue
            if (previous.status != AgentRunStatus.Complete || previous.errorCode == "history_removed") continue
            val users = exchange.filter { it.role == AgentMessageRole.User && it.status == AgentMessageStatus.Complete }
            val answer = exchange.lastOrNull { it.role == AgentMessageRole.Assistant && it.status == AgentMessageStatus.Complete && it.text.isNotBlank() } ?: continue
            if (users.isEmpty()) continue
            val projected = (users + answer).map { notes.projectMessage(run.id, it) }
            if (projected.any { it == null }) continue
            val safe = projected.filterNotNull()
            val previousSegmentFallback = exchange.first().segmentId != run.segmentId
            turns += AgentContextTurn(
                if (previousSegmentFallback) "[前一片段摘要待生成；原文尾部]\n" + safe.dropLast(1).joinToString("\n") { it.message.text }.takeLast(800)
                else safe.dropLast(1).joinToString("\n") { it.message.text },
                if (previousSegmentFallback) safe.last().message.text.takeLast(800) else safe.last().message.text)
            sources += safe.flatMap { it.sources }
        }
        val execution = mutableListOf<ModelMessage>()
        val current = history.filter { it.runId == run.id && it.role in setOf(AgentMessageRole.User, AgentMessageRole.Assistant) }
        for (message in current) {
            val projected = notes.projectMessage(run.id, message)
            if (message.role == AgentMessageRole.User && projected == null) throw AgentSourceAccessException()
            if (projected == null) continue
            if (projected.message.text.isBlank() && projected.message.calls.isEmpty() && projected.message.results.isEmpty()) continue
            if (projected.message.calls.isNotEmpty()) {
                val result = database.agent().message("tool-results:${message.id}") ?: continue
                val projectedResult = notes.projectMessage(run.id, result) ?: continue
                execution += projected.message
                execution += projectedResult.message
                sources += projected.sources + projectedResult.sources
            } else {
                execution += projected.message
                sources += projected.sources
            }
        }
        require(execution.any { it.role == AgentMessageRole.User })
        if (execution.lastOrNull()?.role == AgentMessageRole.Assistant) execution += ModelMessage(AgentMessageRole.User, "请基于已保存的进度继续完成当前任务。")
        val tools = if (profile.capabilities.tools) AgentNoteTools else emptyList()
        val base = planAgentExecutionContext(profile, turns, execution, tools)
        var remaining = profile.contextTokens - profile.outputTokens - ModelLimits.ToolReserveTokens - base.estimatedInputTokens
        val memoryMessages = mutableListOf<ModelMessage>()
        for (memory in recalled) {
            val message = ModelMessage(AgentMessageRole.User, memory.text)
            val cost = estimatedAgentTokens(kotlinx.serialization.json.Json.encodeToString(message))
            if (cost > remaining) continue
            remaining -= cost
            memoryMessages += message
            sources += memory.sources
        }
        val memoryCost = memoryMessages.sumOf { estimatedAgentTokens(kotlinx.serialization.json.Json.encodeToString(it)) }
        return AgentRequestContext(base.copy(messages = memoryMessages + base.messages, estimatedInputTokens = base.estimatedInputTokens + memoryCost), sources.distinct())
    }
}
