package com.xnote.app.data.agent

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.xnote.app.data.db.AgentPermissionEntity
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.domain.agent.AgentPermission
import kotlinx.serialization.json.Json

// -- Type Definitions

class AgentPermissionStore(private val database: XNoteDatabase) {
    // -- Functions

    suspend fun current(): AgentPermission = database.agent().permission()?.let {
        Json.decodeFromString<AgentPermission>(it.permissionJson)
    } ?: AgentPermission()

    /** Only user settings call this; every change invalidates older run and snapshot grants. */
    suspend fun saveFromUser(value: AgentPermission): AgentPermission = database.useWriterConnection { connection ->
        connection.immediateTransaction {
            val saved = value.copy(revision = current().revision + 1)
            database.agent().savePermission(AgentPermissionEntity(permissionJson = Json.encodeToString(saved)))
            saved
        }
    }
}
