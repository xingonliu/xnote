package com.xnote.app.domain.model

// -- Type Definitions

data class NoteSearchResult(
    val note: Note,
    val matchedText: String,
)
