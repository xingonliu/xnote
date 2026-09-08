package com.xnote.app.feature.reader

import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteListSort
import com.xnote.app.navigation.NotesRoute
import com.xnote.app.navigation.XNoteNavigationState
import com.xnote.app.navigation.decodeNotesStack
import com.xnote.app.navigation.encodeNotesStack
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class ReadingPaginationTest {
    @Test fun eachNoteStartsOnANewPageAndExactFitsDoNotAddBlankPages() {
        val units = listOf(unit("a", "title", 0, 20f), unit("a", "body", 0, 80f), unit("b", "title", 0, 20f))
        val pages = paginateReadingUnits(units, 100f)
        assertEquals(listOf("a", "b"), pages.map { it.noteId })
        assertEquals(units, pages.flatMap { it.units })
    }

    @Test fun mediaMovesIntactToNextPageWithoutLosingAdjacentText() {
        val units = listOf(unit("a", "text", 0, 60f), unit("a", "image", 0, 80f), unit("a", "after", 0, 20f))
        val pages = paginateReadingUnits(units, 100f)
        assertEquals(listOf(listOf("text"), listOf("image", "after")), pages.map { it.units.map { it.blockId } })
    }

    @Test fun resizeKeepsTheAnchorInItsNewPage() {
        val units = (0..9).map { unit("a", "body", it * 10, 20f) }
        val large = paginateReadingUnits(units, 100f)
        val small = paginateReadingUnits(units, 60f)
        assertEquals(1, readingPageIndex(large, "a", "body", 60))
        assertEquals(2, readingPageIndex(small, "a", "body", 60))
        assertEquals(units, small.flatMap { it.units })
        assertTrue(small.all { it.units.sumOf { unit -> unit.height.toDouble() } <= 60 })
    }

    @Test fun removedBlockFallsBackToCurrentNoteAndRemovedNoteToStart() {
        val pages = paginateReadingUnits(listOf(unit("a", "one", 0, 40f), unit("b", "two", 0, 40f)), 100f)
        assertEquals(1, readingPageIndex(pages, "b", "removed", 42))
        assertEquals(0, readingPageIndex(pages, "removed", "two", 42))
        assertTrue(paginateReadingUnits(emptyList<ReadingUnit<String>>(), 100f).isEmpty())
    }

    @Test fun readingScopeExcludesTrashAndUsesEveryNotebookSort() {
        val a = note("a", "book", 2, "Zulu", 5, 1)
        val b = note("b", "book", 1, "Alpha", 1, 5)
        val notes = listOf(a, b, note("unfiled", null), note("other", "else"), note("trash", "book").copy(deletedAtEpochMs = 1))
        val route = NotesRoute.Reader(notebookId = "book")
        assertEquals(listOf("b", "a"), readingNotes(notes, route, NoteListSort.Manual).map { it.id })
        assertEquals(listOf("b", "a"), readingNotes(notes, route, NoteListSort.Title).map { it.id })
        assertEquals(listOf("a", "b"), readingNotes(notes, route, NoteListSort.CreatedAt).map { it.id })
        assertEquals(listOf("b", "a"), readingNotes(notes, route, NoteListSort.UpdatedAt).map { it.id })
        assertEquals(listOf("a"), readingNotes(notes, NotesRoute.Reader(noteId = "a"), NoteListSort.Title).map { it.id })
        assertTrue(readingNotes(notes, NotesRoute.Reader(), NoteListSort.Manual).isEmpty())
        assertTrue(readingNotes(notes, NotesRoute.Reader(noteId = "trash"), NoteListSort.Manual).isEmpty())
    }

    @Test fun readerEditAndBackPreserveBothReaderAndOriginalEntry() {
        val editor = XNoteNavigationState().openNotebook("book").openEditor("a")
        val reader = editor.openReader(noteId = "a")
        val editAgain = reader.openEditor("a")
        assertEquals(reader, editAgain.popNotes())
        assertEquals(editor, reader.popNotes())
        assertEquals(editAgain.notesStack, decodeNotesStack(encodeNotesStack(editAgain.notesStack)))
        val bookReader = XNoteNavigationState().openNotebook("book").openReader(notebookId = "book")
        assertEquals(bookReader.notesStack, decodeNotesStack(encodeNotesStack(bookReader.notesStack)))
        assertFalse(bookReader.showsPrimaryChrome)
    }

    // -- Functions

    private fun unit(note: String, block: String, offset: Int, height: Float) = ReadingUnit(note, block, offset, height, block)
    private fun note(id: String, notebook: String?, order: Long = 0, title: String = id, created: Long = 1, updated: Long = 1) =
        Note(id, notebook, title, NoteDocument(), null, order, 0, 0, "", created, updated, null, null)
}
