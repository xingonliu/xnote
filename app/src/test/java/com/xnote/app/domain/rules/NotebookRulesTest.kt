package com.xnote.app.domain.rules

import com.xnote.app.domain.document.emptyNoteDocument
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteKind
import com.xnote.app.domain.model.Notebook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// -- Tests

class NotebookRulesTest {
    @Test
    fun deletingEmptyNotebookProducesNoPatches() {
        val patches = patchesForDeletedNotebook(
            notebook = notebook("nb-1", "工作"),
            notesInNotebook = emptyList(),
            nowMs = 50L,
        )
        assertTrue(patches.isEmpty())
    }

    @Test
    fun deletingNotebookTrashesActiveNotesAndSnapshotsName() {
        val note = note(id = "n-1", notebookId = "nb-1", deletedAt = null)
        val patches = patchesForDeletedNotebook(
            notebook = notebook("nb-1", "工作"),
            notesInNotebook = listOf(note),
            nowMs = 50L,
        )
        assertEquals(1, patches.size)
        assertEquals("n-1", patches[0].noteId)
        assertEquals("工作", patches[0].originalNotebookName)
        assertEquals(50L, patches[0].deletedAtEpochMs)
    }

    @Test
    fun deletingNotebookKeepsExistingDeletedAt() {
        val note = note(id = "n-2", notebookId = "nb-1", deletedAt = 20L)
        val patches = patchesForDeletedNotebook(
            notebook = notebook("nb-1", "工作"),
            notesInNotebook = listOf(note),
            nowMs = 50L,
        )
        assertEquals(20L, patches[0].deletedAtEpochMs)
        assertEquals("工作", patches[0].originalNotebookName)
    }

    @Test
    fun restoreReturnsOriginalNotebookWhenItStillExists() {
        val note = note(id = "n-1", notebookId = "nb-1", deletedAt = 20L)
        val restored = notebookIdAfterRestore(note) { id -> id == "nb-1" }
        assertEquals("nb-1", restored)
    }

    @Test
    fun restoreBecomesUnfiledWhenOriginalNotebookIsGone() {
        val note = note(id = "n-1", notebookId = "nb-1", deletedAt = 20L)
        val restored = notebookIdAfterRestore(note) { false }
        assertNull(restored)
    }

    @Test
    fun restoreOfNotebookDeletedNoteStaysUnfiled() {
        val note = note(id = "n-1", notebookId = null, deletedAt = 20L)
            .copy(originalNotebookName = "工作")
        val restored = notebookIdAfterRestore(note) { true }
        assertNull(restored)
    }
}

// -- Fixtures

private fun notebook(id: String, name: String): Notebook = Notebook(
    id = id,
    name = name,
    sortIndex = 0L,
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 1L,
)

private fun note(id: String, notebookId: String?, deletedAt: Long?): Note = Note(
    id = id,
    notebookId = notebookId,
    title = "标题",
    kind = NoteKind.Rich,
    document = emptyNoteDocument(),
    markdownText = null,
    backgroundKey = null,
    sortIndex = 0L,
    visibleCharacterCount = 0,
    latinWordCount = 0,
    summary = "",
    createdAtEpochMs = 1L,
    updatedAtEpochMs = 1L,
    deletedAtEpochMs = deletedAt,
    originalNotebookName = null,
)
