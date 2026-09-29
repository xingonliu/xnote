package com.xnote.app.data.agent

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.UUID

// -- Type Definitions

/** All providers share durable call identity, permission rechecks and atomic local commits. */
class AgentToolExecutor(private val database: XNoteDatabase, private val notes: AgentNoteStore, private val registry: AgentToolRegistry) {
    // -- Functions

    suspend fun execute(runId: String, call: ModelToolCall): AgentToolResult =
        if (registry.mode(call.name) == AgentToolExecutionMode.External) executeExternal(runId, call)
        else executeLocal(runId, call)

    suspend fun reconcilePending(runId: String) {
        for (event in database.agent().toolEvents(runId).filter { it.status in setOf(AgentToolStatus.Executing, AgentToolStatus.Unknown) }) {
            require(registry.mode(event.name) == AgentToolExecutionMode.External) { "工具提交结果尚未核实。" }
            executeExternal(runId, ModelToolCall(event.callId, event.name, Json.parseToJsonElement(event.argumentsJson).jsonObject), recovering = true)
        }
    }

    private suspend fun executeExternal(runId: String, call: ModelToolCall, recovering: Boolean = false): AgentToolResult {
        var fresh = false
        val context = transaction {
            val run = requireNotNull(database.agent().run(runId))
            require(recovering || run.status == AgentRunStatus.Running)
            val old = database.agent().toolEvent(runId, call.id)
            require(old == null || (old.name == call.name && Json.parseToJsonElement(old.argumentsJson) == call.arguments))
            if (old?.status in setOf(AgentToolStatus.Committed, AgentToolStatus.Denied, AgentToolStatus.Failed)) return@transaction null
            val context = notes.toolContext(run)
            if (!registry.authorized(context, call)) {
                require(old?.status !in setOf(AgentToolStatus.Executing, AgentToolStatus.Unknown)) { "外部工具授权已撤销，原调用结果尚未核实。" }
                val denied = old ?: AgentToolEventEntity(id(), runId, call.id, call.name, call.arguments.toString(), null,
                    AgentToolStatus.Requested, context.access.permission.revision, now())
                database.agent().saveToolEvent(denied.copy(status = AgentToolStatus.Denied, committedAtEpochMs = now(),
                    resultJson = "{\"error\":\"external_permission_required\"}", decisionsJson = decisions(denied, run, "外部工具独立授权不足，未执行。")))
                return@transaction null
            }
            fresh = old == null || old.status == AgentToolStatus.Requested
            require(!recovering || !fresh)
            database.agent().saveToolEvent((old ?: AgentToolEventEntity(id(), runId, call.id, call.name, call.arguments.toString(), null,
                AgentToolStatus.Requested, context.access.permission.revision, now())).copy(status = AgentToolStatus.Executing))
            context
        } ?: return executeLocal(runId, call, allowInactive = recovering)
        try {
            // External I/O never holds a database transaction. Providers use runId + callId as their operation identity.
            val result = if (fresh) registry.execute(context, call) else registry.reconcile(context, call)
            require(result is AgentToolResult.Finished) { "外部工具结果尚未核实，保留调用记录，不能重复执行。" }
            return withContext(NonCancellable) {
                executeLocal(runId, call, result, allowInactive = true)
            }
        } catch (failure: Exception) {
            withContext(NonCancellable) { transaction {
                val event = requireNotNull(database.agent().toolEvent(runId, call.id))
                if (event.status == AgentToolStatus.Executing) database.agent().saveToolEvent(event.copy(status = AgentToolStatus.Unknown))
            } }
            throw failure
        }
    }

    private suspend fun executeLocal(runId: String, call: ModelToolCall, externalResult: AgentToolResult.Finished? = null,
        allowInactive: Boolean = false): AgentToolResult = transaction {
        currentCoroutineContext().ensureActive()
        val run = requireNotNull(database.agent().run(runId))
        require(allowInactive || run.status == AgentRunStatus.Running) { "运行已暂停或停止，不能继续派发工具。" }
        val old = database.agent().toolEvent(runId, call.id)
        require(old == null || (old.name == call.name && Json.parseToJsonElement(old.argumentsJson) == call.arguments)) { "工具调用 ID 不能复用为不同操作。" }
        if (old?.status in setOf(AgentToolStatus.Committed, AgentToolStatus.Denied, AgentToolStatus.Failed)) {
            val sources = Json.decodeFromString<List<AgentMessageSource>>(checkNotNull(old).sourcesJson)
            return@transaction if (registry.authorized(notes.toolContext(run), call) && old.permissionRevision == AgentPermissionStore(database).current().revision && notes.canUseSources(run, sources) && AgentMemoryProvenance(database).canUse(old.id, run)) AgentToolResult.Finished(
                ModelToolResult(call.id, call.name, checkNotNull(old.resultJson)), sources, database.memory().sourceIds(old.id),
            ) else unavailable(call)
        }
        val permission = AgentPermissionStore(database).current()
        val event = old ?: AgentToolEventEntity(id(), runId, call.id, call.name, call.arguments.toString(), null,
            AgentToolStatus.Requested, permission.revision, now())
        database.agent().saveToolEvent(event)
        val result = try {
            if (externalResult != null) {
                if (registry.authorized(notes.toolContext(run), call) && notes.canUseSources(run, externalResult.sources)) externalResult
                else finished(call, buildJsonObject { put("error", "external_result_unavailable"); put("executed", true) })
            } else registry.execute(notes.toolContext(run), call)
        } catch (_: IllegalArgumentException) {
            finished(call, buildJsonObject { put("error", "invalid_arguments") })
        }
        if (result is AgentToolResult.Finished) {
            AgentMemoryProvenance(database).save(event.id, result.sourceMessageIds)
            val error = Json.parseToJsonElement(result.result.content).jsonObject["error"]?.jsonPrimitive?.content
            val reason = when (error) {
                "invalid_arguments" -> "参数未通过结构或范围校验，未执行操作。"
                "read_required" -> "当前话题没有该版本的读取基线，须先读取再修改。"
                "selection_scope" -> "选区润色只能修改用户选中的文字，不能创建、删除或修改其他笔记。"
                "unsupported_tool" -> "该工具未开放，未执行操作。"
                "unavailable_under_current_permission" -> "指定快照不存在、已失效或当前权限不允许读取，未替换为当前正文。"
                else -> registry.reason(call.name)
            }
            database.agent().saveToolEvent(event.copy(resultJson = result.result.content,
                sourcesJson = Json.encodeToString(result.sources), status = when (error) {
                    null -> AgentToolStatus.Committed
                    "invalid_arguments" -> AgentToolStatus.Failed
                    else -> AgentToolStatus.Denied
                }, decisionsJson = decisions(event, requireNotNull(database.agent().run(run.id)), reason),
                permissionRevision = permission.revision, committedAtEpochMs = now()))
        } else if (result is AgentToolResult.Conflict) {
            database.agent().saveToolEvent(event.copy(permissionRevision = permission.revision,
                resultJson = buildJsonObject { put("error", "edit_conflict"); put("location", result.location); put("next_step", "read_current_and_replan") }.toString(),
                sourcesJson = Json.encodeToString(listOf(AgentMessageSource(call.arguments.getValue("note_id").jsonPrimitive.content))),
                decisionsJson = decisions(event, run, "写入与当前内容冲突，正文未改变；等待用户后重新读取并调整。")))
            database.agent().saveRun(run.copy(status = AgentRunStatus.WaitingConflict, updatedAtEpochMs = now()))
        } else {
            database.agent().saveToolEvent(event.copy(permissionRevision = permission.revision,
                decisionsJson = decisions(event, run, "当前权限与范围不足以执行请求，等待用户授权；队列不继续。")))
            database.agent().saveRun(run.copy(status = AgentRunStatus.WaitingPermission, updatedAtEpochMs = now()))
        }
        result
    }

    private suspend fun decisions(event: AgentToolEventEntity, run: AgentRunEntity, reason: String): String {
        val access = notes.access(run)
        val grant = access.grant?.takeIf { it.runId == run.id && it.permissionRevision == access.permission.revision }
        return Json.encodeToString(Json.decodeFromString<List<AgentToolDecision>>(event.decisionsJson) +
            AgentToolDecision(now(), access.permission, grant, access.attachedNoteIds, reason))
    }

    private fun unavailable(call: ModelToolCall) = AgentToolResult.Finished(
        ModelToolResult(call.id, call.name, "{\"error\":\"unavailable_under_current_permission\"}"), emptyList())

    private fun finished(call: ModelToolCall, value: JsonObject) = AgentToolResult.Finished(ModelToolResult(call.id, call.name, value.toString()), emptyList())
    private suspend fun <T> transaction(block: suspend () -> T): T = database.useWriterConnection { it.immediateTransaction { block() } }
    private fun id(): String = UUID.randomUUID().toString()
    private fun now(): Long = System.currentTimeMillis()
}
