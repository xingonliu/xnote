package com.xnote.app.feature.notes

import android.text.format.DateUtils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteListSort
import com.xnote.app.domain.model.Notebook
import com.xnote.app.domain.model.NotebookStats

// -- Type Definitions

sealed interface NotesScope {
    data object All : NotesScope

    data object Unfiled : NotesScope

    data class Notebook(
        val id: String,
    ) : NotesScope
}

class NotesUiState {
    var scope by mutableStateOf<NotesScope>(NotesScope.All)
    var homeSort by mutableStateOf(NoteListSort.UpdatedAt)
    var notebookSort by mutableStateOf(NoteListSort.Manual)
    var selectedIds by mutableStateOf(emptySet<String>())
    var pickerVisible by mutableStateOf(false)
    var sortMenuVisible by mutableStateOf(false)
    var moreVisible by mutableStateOf(false)
    var moveVisible by mutableStateOf(false)
    var trashConfirmVisible by mutableStateOf(false)
    var deleteNotebookVisible by mutableStateOf(false)
    var renameVisible by mutableStateOf(false)
    var paragraphMenuVisible by mutableStateOf(false)
    var tableMenuVisible by mutableStateOf(false)
    var linkDialogVisible by mutableStateOf(false)
    var createNotebookName by mutableStateOf("")
    var renameDraft by mutableStateOf("")
    var linkDraft by mutableStateOf("")
}

// -- Functions

fun encodeNotesScope(scope: NotesScope): String = when (scope) {
    NotesScope.All -> "all"
    NotesScope.Unfiled -> "unfiled"
    is NotesScope.Notebook -> "notebook:${scope.id}"
}

fun decodeNotesScope(raw: String): NotesScope = when {
    raw == "unfiled" -> NotesScope.Unfiled
    raw.startsWith("notebook:") -> NotesScope.Notebook(raw.removePrefix("notebook:"))
    else -> NotesScope.All
}

fun Note.displayTitle(untitled: String): String = title.ifBlank { untitled }

fun formatNoteTimestamp(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    return DateUtils.getRelativeTimeSpanString(
        epochMs,
        nowMs,
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}

fun notesMatching(notes: List<Note>, scope: NotesScope): List<Note> = when (scope) {
    NotesScope.All -> notes
    NotesScope.Unfiled -> notes.filter { it.notebookId == null }
    is NotesScope.Notebook -> notes.filter { it.notebookId == scope.id }
}

fun sortNotes(notes: List<Note>, sort: NoteListSort): List<Note> = notes.sortedWith(
    when (sort) {
        NoteListSort.UpdatedAt -> compareByDescending(Note::updatedAtEpochMs)
        NoteListSort.CreatedAt -> compareByDescending(Note::createdAtEpochMs)
        NoteListSort.Title -> compareBy { it.title.lowercase() }
        NoteListSort.Manual -> compareBy(Note::sortIndex)
    },
)

fun notebookStatsFrom(notes: List<Note>): Map<String, NotebookStats> {
    return notes
        .filter { it.notebookId != null }
        .groupBy { it.notebookId!! }
        .mapValues { (_, items) ->
            NotebookStats(
                noteCount = items.size,
                characterCount = items.sumOf { it.visibleCharacterCount },
            )
        }
}

fun unfiledStatsFrom(notes: List<Note>): NotebookStats {
    val items = notes.filter { it.notebookId == null }
    return NotebookStats(
        noteCount = items.size,
        characterCount = items.sumOf { it.visibleCharacterCount },
    )
}

fun notebookName(notebooks: List<Notebook>, notebookId: String?): String? {
    if (notebookId == null) return null
    return notebooks.firstOrNull { it.id == notebookId }?.name
}
