package com.xnote.app.feature.reader

import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteListSort
import com.xnote.app.feature.notes.sortNotes
import com.xnote.app.navigation.NotesRoute

// -- Type Definitions

data class ReadingUnit<T>(
    val noteId: String,
    val blockId: String,
    val offset: Int,
    val height: Float,
    val content: T,
)

data class ReadingPage<T>(val units: List<ReadingUnit<T>>) {
    val noteId: String get() = units.first().noteId
}

// -- Functions

fun readingNotes(notes: List<Note>, route: NotesRoute.Reader, sort: NoteListSort): List<Note> =
    sortNotes(notes.filter { note ->
        !note.isTrashed && if (route.noteId != null) note.id == route.noteId
        else route.notebookId != null && note.notebookId == route.notebookId
    }, sort)

fun <T> paginateReadingUnits(units: List<ReadingUnit<T>>, pageHeight: Float): List<ReadingPage<T>> {
    require(pageHeight > 0 && pageHeight.isFinite())
    val pages = mutableListOf<ReadingPage<T>>()
    var page = mutableListOf<ReadingUnit<T>>()
    var used = 0f
    for (unit in units) {
        require(unit.height > 0 && unit.height.isFinite())
        if (page.isNotEmpty() && (page.first().noteId != unit.noteId || used + unit.height > pageHeight)) {
            pages += ReadingPage(page)
            page = mutableListOf()
            used = 0f
        }
        page += unit
        used += unit.height
    }
    if (page.isNotEmpty()) pages += ReadingPage(page)
    return pages
}

fun <T> readingPageIndex(pages: List<ReadingPage<T>>, noteId: String?, blockId: String?, offset: Int): Int {
    val matching = pages.indices.filter { pages[it].noteId == noteId }
    if (matching.isEmpty()) return 0
    return matching.lastOrNull { index ->
        pages[index].units.any { it.blockId == blockId && it.offset <= offset }
    } ?: matching.first()
}
