package com.xnote.app.data.agent

import com.xnote.app.data.db.NoteEntity
import com.xnote.app.data.db.AgentSnapshotEntity
import com.xnote.app.domain.agent.AgentEditableContent
import com.xnote.app.domain.document.decodeNoteDocument
import kotlinx.serialization.json.*
import java.security.MessageDigest

// -- Type Definitions

data class AgentEditBase(val noteId: String, val version: String, val content: AgentEditableContent)

// -- Functions

fun NoteEntity.editBase() = AgentEditBase(id, agentVersion(), AgentEditableContent(title, decodeNoteDocument(documentJson)))
fun AgentSnapshotEntity.editBase() = AgentEditBase(noteId, version, AgentEditableContent(title, decodeNoteDocument(documentJson)))

fun NoteEntity.agentVersion(): String {
    val identity = buildJsonArray {
        add(id); add(title); add(documentJson)
        add(backgroundKey?.let(::JsonPrimitive) ?: JsonNull)
        add(notebookId?.let(::JsonPrimitive) ?: JsonNull)
        add(updatedAtEpochMs)
        add(deletedAtEpochMs?.let(::JsonPrimitive) ?: JsonNull)
    }.toString()
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
