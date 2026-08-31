package com.xnote.app.domain.model

import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.rules.RecycleBinPolicy

// -- Type Definitions

enum class NoteKind {
    Rich,
    Markdown,
}

enum class NoteListSort {
    UpdatedAt,
    CreatedAt,
    Title,
    Manual,
}

data class Note(
    val id: String,
    val notebookId: String?,
    val title: String,
    val kind: NoteKind,
    val document: NoteDocument?,
    val markdownText: String?,
    val backgroundKey: BackgroundKey?,
    val sortIndex: Long,
    val visibleCharacterCount: Int,
    val latinWordCount: Int,
    val summary: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val originalNotebookName: String?,
) {
    val isTrashed: Boolean
        get() = deletedAtEpochMs != null

    val isUnfiled: Boolean
        get() = notebookId == null && !isTrashed

    fun remainingRetentionDays(nowMs: Long): Int? {
        val deletedAt = deletedAtEpochMs ?: return null
        return RecycleBinPolicy.remainingDays(deletedAt, nowMs)
    }
}
