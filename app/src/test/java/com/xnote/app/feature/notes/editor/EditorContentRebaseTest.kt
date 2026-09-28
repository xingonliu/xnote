package com.xnote.app.feature.notes.editor

import com.xnote.app.domain.document.*
import org.junit.Assert.assertEquals
import org.junit.Test

// -- Tests

class EditorContentRebaseTest {
    @Test fun selectionTracksUnicodeInsertionWithoutSplittingSurrogates() {
        val before = document("甲乙丙")
        val after = document("😀甲乙丙")
        assertEquals(EditorSelection("body", 5, 3), rebaseEditorSelection(before, after, EditorSelection("body", 3, 1)))
    }

    @Test fun removedBlockMovesCaretToRemainingText() {
        val after = NoteDocument(blocks = listOf(TextBlock("remaining")))
        assertEquals(EditorSelection("remaining"), rebaseEditorSelection(document("abc"), after, EditorSelection("body", 2, 2)))
    }

    @Test fun tableCaretTracksItsOwnCell() {
        fun table(text: String) = NoteDocument(blocks = listOf(TableBlock("t", listOf(TableRow(listOf(TableCell(listOf(InlineRun(text)))))))))
        assertEquals(EditorSelection("t", 4, 4, 0, 0), rebaseEditorSelection(table("ab"), table("😀ab"), EditorSelection("t", 2, 2, 0, 0)))
    }

    private fun document(text: String) = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(text)))))
}
