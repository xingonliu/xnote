package com.xnote.app.data.agent

import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.serialization.json.*

// -- Type Definitions

class AgentBuiltInToolProvider(private val database: XNoteDatabase, private val notes: AgentNoteStore,
    private val files: AgentFileStore?) : AgentToolProvider {
    // -- Functions

    override fun registrations(): List<AgentToolRegistration> {
        val definitions = (AgentNoteTools + AgentMemoryTools + AgentFileTools).associateBy { it.name }
        fun registration(name: String, level: AgentPermissionLevel? = null, max: Int = AgentNoteLimits.MaxToolArgumentCharacters,
            selection: Boolean = true, handler: suspend (AgentRunEntity, ModelToolCall) -> AgentToolResult) = AgentToolRegistration(
            definition = definitions.getValue(name),
            available = { context ->
                val access = context.access
                val grant = access.grant?.takeIf { it.permissionRevision == access.permission.revision && it.runId == access.runId }
                level == null || access.permission.level >= level ||
                    (grant != null && (grant.level >= level || grant.createdNoteIds.isNotEmpty())) ||
                    (name == "read" && context.canReadSnapshots)
            },
            execute = { context, call -> handler(requireNotNull(database.agent().run(context.access.runId)), call) },
            maxArgumentCharacters = max, allowedInSelection = selection,
            auditReason = "工具 $name 已通过当前权限及资源校验。",
            // Resource handlers check exact IDs, fixed snapshots and per-call creation grants.
            authorize = { _, _ -> true },
        )
        return buildList {
            add(registration("read", AgentPermissionLevel.Read) { run, call -> notes.read(run, call, Json.decodeFromJsonElement(call.arguments)) })
            add(registration("note_search", AgentPermissionLevel.Read) { run, call -> notes.search(run, call, Json.decodeFromJsonElement(call.arguments)) })
            add(registration("write", AgentPermissionLevel.Edit, AgentNoteLimits.MaxWriteArgumentCharacters) { run, call -> notes.write(run, call, Json.decodeFromJsonElement(call.arguments)) })
            add(registration("create", AgentPermissionLevel.Edit, AgentNoteLimits.MaxWriteArgumentCharacters, false) { run, call -> notes.create(run, call, Json.decodeFromJsonElement(call.arguments)) })
            add(registration("delete", AgentPermissionLevel.Edit, selection = false) { run, call -> notes.trash(run, call, Json.decodeFromJsonElement(call.arguments)) })
            add(registration("memory_remember") { run, call -> notes.remember(run, call, Json.decodeFromJsonElement(call.arguments)) })
            for (name in listOf("memory_search", "memory_read")) add(registration(name) { run, call -> AgentHistoryMemoryStore(database).execute(run, call) })
            if (files != null) add(registration("output_file", max = AgentNoteLimits.MaxWriteArgumentCharacters) { run, call -> files.output(run, call, Json.decodeFromJsonElement(call.arguments)) })
        }
    }

}
