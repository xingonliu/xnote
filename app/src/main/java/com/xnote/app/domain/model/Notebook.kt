package com.xnote.app.domain.model

// -- Type Definitions

data class Notebook(
    val id: String,
    val name: String,
    val sortIndex: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class NotebookStats(
    val noteCount: Int,
    val characterCount: Int,
)
