package com.xnote.app.domain.rules

import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.Notebook

// -- Type Definitions

data class NotebookDeletionPatch(
    val noteId: String,
    val originalNotebookName: String,
    val deletedAtEpochMs: Long,
)

// -- Functions

fun patchesForDeletedNotebook(
    notebook: Notebook,
    notesInNotebook: List<Note>,
    nowMs: Long,
): List<NotebookDeletionPatch> {
    return notesInNotebook.map { note ->
        NotebookDeletionPatch(
            noteId = note.id,
            originalNotebookName = notebook.name,
            deletedAtEpochMs = note.deletedAtEpochMs ?: nowMs,
        )
    }
}

fun notebookIdAfterRestore(note: Note, notebookExists: (String) -> Boolean): String? {
    val notebookId = note.notebookId ?: return null
    return if (notebookExists(notebookId)) notebookId else null
}
