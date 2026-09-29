package com.xnote.app.data.agent

import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.currentCoroutineContext

// -- Type Definitions

class AgentMemoryProvenance(private val database: XNoteDatabase) {
    // -- Functions

    suspend fun save(ownerId: String, sourceIds: Collection<String>) {
        val pending = ArrayDeque(sourceIds)
        val visited = mutableSetOf(ownerId)
        while (pending.isNotEmpty()) {
            val sourceId = pending.removeFirst()
            if (!visited.add(sourceId)) continue
            database.memory().saveSource(AgentMemorySourceRefEntity(ownerId, sourceId))
            pending.addAll(database.memory().sourceIds(sourceId))
            database.profileMemory().references(sourceId).forEach { database.profileMemory().saveReference(AgentMessageProfileRefEntity(ownerId, it.factId)) }
        }
    }

    suspend fun canUse(ownerId: String, run: AgentRunEntity? = null): Boolean {
        val profiles = AgentProfileMemoryStore(database)
        if (!profiles.canUseMessage(ownerId) || !canUseResult(database.agent().message(ownerId))) return false
        val permission = AgentPermissionStore(database).current()
        val approvedSources = if (run != null && permission.mode == AgentPermissionMode.RequestApproval)
            database.agent().toolEvents(run.id).filter { it.status == AgentToolStatus.Committed && it.permissionRevision == permission.revision }
                .flatMap { database.memory().sourceIds(it.id) }.toSet() else emptySet()
        for (sourceId in database.memory().sourceIds(ownerId)) {
            val source = database.agent().message(sourceId) ?: return false
            val sourceRun = source.runId?.let { database.agent().run(it) } ?: return false
            if (!canUseResult(source, sourceId in approvedSources)) return false
            if (sourceRun.errorCode == "history_removed" || !profiles.canUseMessage(sourceId)) return false
            // A resumed answer may depend on a saved partial reply; only mutable output is unavailable.
            if (source.status in setOf(AgentMessageStatus.Pending, AgentMessageStatus.Streaming)) return false
            // Current-run note sources are projected by AgentNoteStore, including safe deletion receipts.
            if (run?.id == sourceRun.id) continue
            val accessRun = run ?: sourceRun.copy(segmentId = "derived-memory")
            if (!AgentNoteStore(database).canUseSources(accessRun, Json.decodeFromString(source.sourcesJson))) return false
        }
        return true
    }

    private suspend fun canUseResult(message: AgentMessageEntity?, approvedSource: Boolean = false): Boolean {
        val runId = message?.runId ?: return true
        val permission = AgentPermissionStore(database).current()
        val execution = currentCoroutineContext()[AgentCallExecution]
        val approvedRead = execution != null && execution.permissionRevision == permission.revision
        if (message.role != AgentMessageRole.User && permission.mode != AgentPermissionMode.FullAccess && !approvedRead && !approvedSource &&
            message.contextPermissionRevision != permission.revision) return false
        val model = message.modelJson?.let { Json.decodeFromString<ModelMessage>(it) } ?: return true
        for (result in model.results) {
            val event = database.agent().toolEvent(runId, result.id) ?: return false
            if (event.status == AgentToolStatus.Committed && (permission.mode == AgentPermissionMode.Private || event.permissionRevision != permission.revision)) return false
            if (result.name != "read" || Json.parseToJsonElement(event.argumentsJson).jsonObject["note_id"]?.jsonPrimitive?.contentOrNull != null) continue
            val payload = Json.parseToJsonElement(result.content).jsonObject
            for (entry in payload["entries"]?.jsonArray.orEmpty()) {
                val value = entry.jsonObject
                if (value["kind"]?.jsonPrimitive?.content == "notebook" &&
                    database.notebooks().get(value.getValue("notebook_id").jsonPrimitive.content) == null) return false
            }
        }
        return true
    }

}
