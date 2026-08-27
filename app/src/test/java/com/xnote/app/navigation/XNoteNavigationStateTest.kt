package com.xnote.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// -- Tests

class XNoteNavigationStateTest {
    @Test
    fun openSearchPreservesCurrentDestination() {
        val state = XNoteNavigationState(destination = AppDestination.Agent)

        val result = state.openSearch()

        assertEquals(AppDestination.Agent, result.destination)
        assertTrue(result.isSearchOpen)
    }

    @Test
    fun selectingDestinationClosesSearch() {
        val state = XNoteNavigationState(isSearchOpen = true)

        val result = state.openDestination(AppDestination.Profile)

        assertEquals(AppDestination.Profile, result.destination)
        assertFalse(result.isSearchOpen)
    }

    @Test
    fun closeSearchReturnsToCurrentDestination() {
        val state = XNoteNavigationState(
            destination = AppDestination.Notes,
            isSearchOpen = true,
        )

        val result = state.closeSearch()

        assertEquals(AppDestination.Notes, result.destination)
        assertFalse(result.isSearchOpen)
    }

    @Test
    fun openingANotebookThenANoteKeepsABackStack() {
        val state = XNoteNavigationState()
            .openNotebook("nb-1")
            .openEditor("note-1")

        assertEquals(NotesRoute.Editor("note-1"), state.notesRoute)
        assertFalse(state.showsPrimaryChrome)

        val notebook = state.popNotes()
        assertEquals(NotesRoute.Notebook("nb-1"), notebook.notesRoute)
        assertEquals(NotesRoute.Home, notebook.popNotes().notesRoute)
    }

    @Test
    fun notesStackRoundTripsThroughTheSaveableEncoding() {
        val stack = listOf(
            NotesRoute.Notebook("nb-1"),
            NotesRoute.Editor("note-2"),
        )
        assertEquals(stack, decodeNotesStack(encodeNotesStack(stack)))
        assertTrue(decodeNotesStack("").isEmpty())
    }
}
