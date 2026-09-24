package com.xnote.app.data.agent

import com.xnote.app.data.db.NoteEntity
import kotlinx.serialization.json.*
import java.security.MessageDigest

// -- Functions

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
