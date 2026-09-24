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
        val boundary = history.lastOrNull { it.role == AgentMessageRole.Event && it.text == "开始新话题" }?.sequence ?: 0
        val turns = mutableListOf<AgentContextTurn>()
        val sources = mutableListOf<AgentMessageSource>()
        for ((previousId, exchange) in history.filter { it.sequence > boundary && it.runId != run.id && it.runId != null }.groupBy { it.runId }) {
            val previous = database.agent().run(checkNotNull(previousId)) ?: continue
            if (previous.status != AgentRunStatus.Complete || previous.errorCode == "history_removed") continue
            val users = exchange.filter { it.role == AgentMessageRole.User && it.status == AgentMessageStatus.Complete }
            val answer = exchange.lastOrNull { it.role == AgentMessageRole.Assistant && it.status == AgentMessageStatus.Complete && it.text.isNotBlank() } ?: continue
            if (users.isEmpty()) continue
            val projected = (users + answer).map { notes.projectMessage(run.id, it) }
            if (projected.any { it == null }) continue
            val safe = projected.filterNotNull()
            turns += AgentContextTurn(safe.dropLast(1).joinToString("\n") { it.message.text }, safe.last().message.text)
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
        val tools = if (profile.capabilities.tools) AgentReadTools else emptyList()
        return AgentRequestContext(planAgentExecutionContext(profile, turns, execution, tools), sources.distinct())
    }
}
