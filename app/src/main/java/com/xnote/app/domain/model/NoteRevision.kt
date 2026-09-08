package com.xnote.app.domain.model

import com.xnote.app.domain.document.NoteDocument

// -- Type Definitions

enum class RevisionReason {
    AgentPolish,
}

data class NoteRevision(
    val id: String,
    val noteId: String,
    val reason: RevisionReason,
    val title: String,
    val document: NoteDocument,
    val createdAtEpochMs: Long,
)
