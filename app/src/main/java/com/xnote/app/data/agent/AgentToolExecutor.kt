package com.xnote.app.data.agent

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.UUID

// -- Type Definitions

/** Every provider crosses the same durable approval boundary before its handler is called. */
class AgentToolExecutor(private val database: XNoteDatabase, private val notes: AgentNoteStore, private val registry: AgentToolRegistry) {
    // -- Functions

    suspend fun execute(runId: String, call: ModelToolCall): AgentToolResult =
        if (registry.mode(call.name) == AgentToolExecutionMode.External) executeExternal(runId, call)
        else transaction {
            val run = requireNotNull(database.agent().run(runId))
            require(run.status == AgentRunStatus.Running)
            val event = event(run, call)
            replay(run, call, event)?.let { return@transaction it }
            gate(run, call, event)?.let { return@transaction it }
            val executing = event.copy(status = AgentToolStatus.Executing, permissionRevision = AgentPermissionStore(database).current().revision)
            database.agent().saveToolEvent(executing)
            val result = try {
                withContext(AgentCallExecution(run.id, call.id, executing.permissionRevision)) {
                    registry.execute(notes.toolContext(run), call)
                }
            } catch (_: IllegalArgumentException) {
                error(call, "invalid_arguments")
            }
            finish(run, call, executing, result)
        }

    suspend fun reconcilePending(runId: String) {
        for (event in database.agent().toolEvents(runId).filter { it.status in setOf(AgentToolStatus.Executing, AgentToolStatus.Unknown) }) {
            require(registry.mode(event.name) == AgentToolExecutionMode.External) { "工具提交结果尚未核实。" }
            executeExternal(runId, ModelToolCall(event.callId, event.name, Json.parseToJsonElement(event.argumentsJson).jsonObject), recovering = true)
        }
    }

    private suspend fun executeExternal(runId: String, call: ModelToolCall, recovering: Boolean = false): AgentToolResult {
        var immediate: AgentToolResult? = null
        var fresh = false
        val prepared = transaction {
            val run = requireNotNull(database.agent().run(runId))
            require(recovering || run.status == AgentRunStatus.Running)
            val event = event(run, call)
            immediate = replay(run, call, event)
            if (immediate != null) return@transaction null
            fresh = event.status !in setOf(AgentToolStatus.Executing, AgentToolStatus.Unknown)
            if (fresh) {
                require(!recovering)
                immediate = gate(run, call, event)
                if (immediate != null) return@transaction null
            } else {
                // Reconciliation also performs external I/O and cannot run after a permission change.
                require(event.permissionRevision == AgentPermissionStore(database).current().revision &&
                    AgentPermissionStore(database).current().mode != AgentPermissionMode.Private) { "权限已变化，外部调用结果待核实。" }
            }
            val executing = event.copy(status = AgentToolStatus.Executing, permissionRevision = AgentPermissionStore(database).current().revision)
            database.agent().saveToolEvent(executing)
            run to executing
        } ?: return requireNotNull(immediate)
        val (run, event) = prepared
        try {
            val result = withContext(AgentCallExecution(run.id, call.id, event.permissionRevision)) {
                val context = notes.toolContext(run)
                require(context.access.canAccessData()) { "权限已变化，停止外部派发。" }
                if (fresh) registry.execute(context, call) else registry.reconcile(context, call)
            }
            require(result is AgentToolResult.Finished) { "外部工具结果尚未核实，不能重复执行。" }
            return withContext(NonCancellable) { transaction {
                val current = AgentPermissionStore(database).current()
                finish(run, call, event, if (current.revision == event.permissionRevision) result else error(call, "external_result_unavailable"))
            } }
        } catch (failure: Exception) {
            withContext(NonCancellable) { transaction {
                database.agent().saveToolEvent(event.copy(status = AgentToolStatus.Unknown))
            } }
            throw failure
        }
    }

    private suspend fun event(run: AgentRunEntity, call: ModelToolCall): AgentToolEventEntity {
        currentCoroutineContext().ensureActive()
        val old = database.agent().toolEvent(run.id, call.id)
        require(old == null || (old.name == call.name && Json.parseToJsonElement(old.argumentsJson) == call.arguments)) {
            "工具调用 ID 不能复用为不同操作。"
        }
        return old ?: AgentToolEventEntity(UUID.randomUUID().toString(), run.id, call.id, call.name,
            call.arguments.toString(), null, AgentToolStatus.Requested, AgentPermissionStore(database).current().revision,
            System.currentTimeMillis()).also { database.agent().saveToolEvent(it) }
    }

    private suspend fun replay(run: AgentRunEntity, call: ModelToolCall, event: AgentToolEventEntity): AgentToolResult? {
        if (event.status !in setOf(AgentToolStatus.Committed, AgentToolStatus.Denied, AgentToolStatus.Failed)) return null
        if (event.status != AgentToolStatus.Committed) return AgentToolResult.Finished(
            ModelToolResult(call.id, call.name, requireNotNull(event.resultJson)), emptyList())
        val sources = Json.decodeFromString<List<AgentMessageSource>>(event.sourcesJson)
        val permission = AgentPermissionStore(database).current()
        return if (permission.mode != AgentPermissionMode.Private && event.permissionRevision == permission.revision &&
            registry.authorized(notes.toolContext(run), call) && notes.canUseSources(run, sources) && AgentMemoryProvenance(database).canUse(event.id, run))
            AgentToolResult.Finished(ModelToolResult(call.id, call.name, requireNotNull(event.resultJson)), sources, database.memory().sourceIds(event.id))
        else error(call, "unavailable_under_current_permission")
    }

    private suspend fun gate(run: AgentRunEntity, call: ModelToolCall, event: AgentToolEventEntity): AgentToolResult? {
        val permission = AgentPermissionStore(database).current()
        registry.validationError(call)?.let { return finish(run, call, event, error(call, it)) }
        if (permission.mode == AgentPermissionMode.Private) return finish(run, call, event, error(call, "private_mode"))
        val context = notes.toolContext(run)
        if (context.selection != null && !registry.allowedInSelection(call.name)) return finish(run, call, event, error(call, "selection_scope"))
        if (!registry.authorized(context, call)) return finish(run, call, event, error(call, "tool_unavailable"))
        if (permission.mode == AgentPermissionMode.RequestApproval &&
            (event.status != AgentToolStatus.Approved || event.permissionRevision != permission.revision)) {
            database.agent().saveToolEvent(event.copy(status = AgentToolStatus.Requested, permissionRevision = permission.revision,
                resultJson = null, decisionsJson = decisions(event, permission, "等待用户批准本次函数调用及其固定参数。")))
            database.agent().saveRun(run.copy(status = AgentRunStatus.WaitingPermission, updatedAtEpochMs = System.currentTimeMillis()))
            return AgentToolResult.PermissionRequired(call.id)
        }
        return null
    }

    private suspend fun finish(run: AgentRunEntity, call: ModelToolCall, event: AgentToolEventEntity, result: AgentToolResult): AgentToolResult {
        val permission = AgentPermissionStore(database).current()
        val now = System.currentTimeMillis()
        when (result) {
            is AgentToolResult.Finished -> {
                AgentMemoryProvenance(database).save(event.id, result.sourceMessageIds)
                val failure = Json.parseToJsonElement(result.result.content).jsonObject["error"]?.jsonPrimitive?.content
                database.agent().saveToolEvent(event.copy(resultJson = result.result.content, sourcesJson = Json.encodeToString(result.sources),
                    status = if (failure == null) AgentToolStatus.Committed else AgentToolStatus.Denied,
                    decisionsJson = decisions(event, permission, failure ?: registry.reason(call.name)), committedAtEpochMs = now))
            }
            is AgentToolResult.Conflict -> {
                database.agent().saveToolEvent(event.copy(status = AgentToolStatus.Requested,
                    resultJson = buildJsonObject { put("error", "edit_conflict"); put("location", result.location) }.toString(),
                    decisionsJson = decisions(event, permission, "写入冲突，正文未改变；须重新读取并提出新调用。")))
                database.agent().saveRun(run.copy(status = AgentRunStatus.WaitingConflict, updatedAtEpochMs = now))
            }
            is AgentToolResult.PermissionRequired -> {
                // The global approval has already passed; a resource failure must never broaden it.
                return finish(run, call, event, error(call, "resource_unavailable"))
            }
        }
        return result
    }

    private fun decisions(event: AgentToolEventEntity, permission: AgentPermission, reason: String): String =
        Json.encodeToString(Json.decodeFromString<List<AgentToolDecision>>(event.decisionsJson) +
            AgentToolDecision(System.currentTimeMillis(), permission, emptySet(), reason))

    private fun error(call: ModelToolCall, code: String) = AgentToolResult.Finished(
        ModelToolResult(call.id, call.name, buildJsonObject { put("error", code) }.toString()), emptyList())
    private suspend fun <T> transaction(block: suspend () -> T): T = database.useWriterConnection { it.immediateTransaction { block() } }
}
