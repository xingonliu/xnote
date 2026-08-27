package com.xnote.app.domain.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// -- Tests

class EditorHistoryTest {
    @Test
    fun undoRestoresTheCapturedSnapshotAndRedoReturnsToCurrent() {
        val history = EditorHistory()
        val first = snapshot("一", "a")
        val second = snapshot("二", "b")
        history.capture(first)
        val undone = history.undo(second)
        assertEquals(first, undone)
        val redone = history.redo(first)
        assertEquals(second, redone)
    }

    @Test
    fun typingInTheSameBlockCoalescesIntoOneUndoStep() {
        val history = EditorHistory()
        history.capture(snapshot("一", "a"), key = "type:t1")
        history.capture(snapshot("一二", "a"), key = "type:t1")
        history.capture(snapshot("一二三", "a"), key = "type:t1")
        assertTrue(history.canUndo)
        val undone = history.undo(snapshot("一二三", "a"))
        assertEquals("一", undone?.title)
        assertFalse(history.canUndo)
    }

    @Test
    fun aDifferentCommandBreaksTheTypingCoalesce() {
        val history = EditorHistory()
        history.capture(snapshot("一", "a"), key = "type:t1")
        history.capture(snapshot("一", "a"))
        assertEquals("一", history.undo(snapshot("加粗", "a"))?.title)
        assertEquals("一", history.undo(snapshot("一", "a"))?.title)
    }
}

// -- Fixtures

private fun snapshot(title: String, blockId: String): EditorSnapshot = EditorSnapshot(
    title = title,
    document = NoteDocument(blocks = listOf(TextBlock(id = blockId))),
    selection = EditorSelection(blockId),
)
