package com.xnote.app.data.agent

import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.serialization.json.Json

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
        if (!profiles.canUseMessage(ownerId)) return false
        for (sourceId in database.memory().sourceIds(ownerId)) {
            val source = database.agent().message(sourceId) ?: return false
            val sourceRun = source.runId?.let { database.agent().run(it) } ?: return false
            if (sourceRun.errorCode == "history_removed" || !profiles.canUseMessage(sourceId)) return false
            if (source.status != AgentMessageStatus.Complete) return false
            // Current-run note sources are projected by AgentNoteStore, including safe deletion receipts.
            if (run?.id == sourceRun.id) continue
            val accessRun = run ?: sourceRun.copy(segmentId = "derived-memory", grantJson = null)
            if (!AgentNoteStore(database).canUseSources(accessRun, Json.decodeFromString(source.sourcesJson))) return false
        }
        return true
    }
}
