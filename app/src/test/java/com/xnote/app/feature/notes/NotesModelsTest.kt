package com.xnote.app.feature.notes

import com.xnote.app.domain.model.NoteListSort
import org.junit.Assert.assertEquals
import org.junit.Test

// -- Tests

class NotesModelsTest {
    @Test
    fun notesScopeRoundTripsThroughSaveableEncoding() {
        assertEquals(NotesScope.All, decodeNotesScope(encodeNotesScope(NotesScope.All)))
        assertEquals(NotesScope.Unfiled, decodeNotesScope(encodeNotesScope(NotesScope.Unfiled)))
        assertEquals(
            NotesScope.Notebook("nb-1"),
            decodeNotesScope(encodeNotesScope(NotesScope.Notebook("nb-1"))),
        )
    }

    @Test
    fun sortNotesOrdersByTitleWithoutChangingIdentity() {
        val first = sampleNote("b", "Beta")
        val second = sampleNote("a", "alpha")
        val sorted = sortNotes(listOf(first, second), NoteListSort.Title)
        assertEquals(listOf("a", "b"), sorted.map { it.id })
    }
}

// -- Fixtures

private fun sampleNote(id: String, title: String) = com.xnote.app.domain.model.Note(
    id = id,
    notebookId = null,
    title = title,
    kind = com.xnote.app.domain.model.NoteKind.Rich,
    document = com.xnote.app.domain.document.emptyNoteDocument(),
    markdownText = null,
    backgroundKey = null,
    sortIndex = 0L,
    visibleCharacterCount = 0,
    latinWordCount = 0,
    summary = "",
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 1L,
    deletedAtEpochMs = null,
    originalNotebookName = null,
)
