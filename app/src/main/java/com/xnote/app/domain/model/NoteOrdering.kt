package com.xnote.app.domain.model

// -- Functions

fun noteComparator(sort: NoteListSort): Comparator<Note> = when (sort) {
    NoteListSort.UpdatedAt -> compareByDescending<Note> { it.updatedAtEpochMs }
    NoteListSort.CreatedAt -> compareByDescending<Note> { it.createdAtEpochMs }
    NoteListSort.Title -> compareBy<Note> { it.title.lowercase() }
    NoteListSort.Manual -> compareBy<Note> { it.sortIndex }
}.thenBy { it.id }
