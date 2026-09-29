package com.xnote.app.domain.agent

import kotlinx.serialization.json.*

// -- Type Definitions

data class AgentToolContext(val access: AgentAccessContext, val selection: AgentMessageSource? = null)

enum class AgentToolExecutionMode { LocalTransaction, External }

data class AgentToolRegistration(
    val definition: ModelTool,
    val available: suspend (AgentToolContext) -> Boolean,
    val execute: suspend (AgentToolContext, ModelToolCall) -> AgentToolResult,
    val maxArgumentCharacters: Int = AgentNoteLimits.MaxToolArgumentCharacters,
    val allowedInSelection: Boolean = true,
    val auditReason: String,
    val authorize: suspend (AgentToolContext, ModelToolCall) -> Boolean = { context, _ -> available(context) },
    val mode: AgentToolExecutionMode = AgentToolExecutionMode.LocalTransaction,
    val reconcile: (suspend (AgentToolContext, ModelToolCall) -> AgentToolResult.Finished?)? = null,
)

/** Providers may impose availability constraints; global approval is always enforced by the executor. */
fun interface AgentToolProvider {
    fun registrations(): List<AgentToolRegistration>
}

class AgentToolRegistry(providers: List<AgentToolProvider>) {
    // -- State and Variables

    private val entries = providers.flatMap { it.registrations() }.also { registrations ->
        require(registrations.map { it.definition.name }.distinct().size == registrations.size) { "工具名称不能重复。" }
        require(registrations.all { it.definition.name.matches(Regex("[A-Za-z0-9_-]{1,64}")) && it.maxArgumentCharacters > 0 })
    }.associateBy { it.definition.name }

    // -- Functions

    suspend fun definitions(context: AgentToolContext): List<ModelTool> = entries.values.filter {
        context.access.permission.mode != AgentPermissionMode.Private && (context.selection == null || it.allowedInSelection) && it.available(context)
    }.map { it.definition }

    suspend fun authorized(context: AgentToolContext, call: ModelToolCall): Boolean = entries[call.name]?.let {
        (context.selection == null || it.allowedInSelection) && it.authorize(context, call)
    } == true

    fun validationError(call: ModelToolCall): String? {
        val entry = entries[call.name] ?: return "unsupported_tool"
        return if (call.id.isBlank() || call.arguments.toString().length > entry.maxArgumentCharacters) "invalid_arguments" else null
    }

    fun allowedInSelection(name: String): Boolean = entries[name]?.allowedInSelection == true

    fun reason(name: String): String = entries[name]?.auditReason ?: "工具未注册。"

    fun mode(name: String): AgentToolExecutionMode = entries[name]?.mode ?: AgentToolExecutionMode.LocalTransaction

    internal suspend fun reconcile(context: AgentToolContext, call: ModelToolCall): AgentToolResult.Finished? =
        if (context.access.canAccessData() && authorized(context, call)) entries[call.name]?.reconcile?.invoke(context, call) else null

    internal suspend fun execute(context: AgentToolContext, call: ModelToolCall): AgentToolResult {
        if (!context.access.canAccessData()) return AgentToolResult.PermissionRequired(call.id)
        val entry = entries[call.name] ?: return error(call, "unsupported_tool")
        require(call.id.isNotBlank() && call.arguments.toString().length <= entry.maxArgumentCharacters)
        if (context.selection != null && !entry.allowedInSelection) return error(call, "selection_scope")
        if (!entry.authorize(context, call)) return AgentToolResult.PermissionRequired(call.id)
        return entry.execute(context, call)
    }

    private fun error(call: ModelToolCall, code: String) = AgentToolResult.Finished(
        ModelToolResult(call.id, call.name, buildJsonObject { put("error", code) }.toString()), emptyList())
}
