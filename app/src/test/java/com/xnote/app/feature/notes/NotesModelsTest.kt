package com.xnote.app.feature.notes

import com.xnote.app.domain.document.emptyNoteDocument
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteListSort
import com.xnote.app.domain.model.Notebook
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// -- Tests

class NotesModelsTest {
    @Test
    fun sortNotesOrdersByTitleWithoutChangingIdentity() {
        val first = sampleNote("b", "Beta")
        val second = sampleNote("a", "alpha")
        val sorted = sortNotes(listOf(first, second), NoteListSort.Title)
        assertEquals(listOf("a", "b"), sorted.map { it.id })
    }

    @Test
    fun sortNotesOrdersByUpdatedAtDescending() {
        val oldNote = sampleNote("1", "旧笔记", updatedAt = 100L)
        val newNote = sampleNote("2", "新笔记", updatedAt = 500L)
        val sorted = sortNotes(listOf(oldNote, newNote), NoteListSort.UpdatedAt)
        assertEquals(listOf("2", "1"), sorted.map { it.id })
    }

    @Test
    fun sortNotesOrdersByCreatedAtDescending() {
        val earlyNote = sampleNote("1", "早创建", createdAt = 200L)
        val lateNote = sampleNote("2", "晚创建", createdAt = 800L)
        val sorted = sortNotes(listOf(earlyNote, lateNote), NoteListSort.CreatedAt)
        assertEquals(listOf("2", "1"), sorted.map { it.id })
    }

    @Test
    fun sortNotesOrdersByManualSortIndexAscending() {
        val first = sampleNote("1", "第一", sortIndex = 10L)
        val second = sampleNote("2", "第二", sortIndex = 5L)
        val third = sampleNote("3", "第三", sortIndex = 20L)
        val sorted = sortNotes(listOf(first, second, third), NoteListSort.Manual)
        assertEquals(listOf("2", "1", "3"), sorted.map { it.id })
    }

    @Test
    fun displayTitleReturnsTitleWhenNotBlank() {
        val note = sampleNote("1", "已有标题")
        assertEquals("已有标题", note.displayTitle("无标题"))
    }

    @Test
    fun displayTitleFallsBackToDefaultWhenBlankOrEmpty() {
        val emptyNote = sampleNote("1", "")
        val blankNote = sampleNote("2", "   ")
        assertEquals("默认无标题", emptyNote.displayTitle("默认无标题"))
        assertEquals("默认无标题", blankNote.displayTitle("默认无标题"))
    }

    @Test
    fun editorDateUsesChineseCalendarFormatInTheRequestedTimeZone() {
        val epochMs = Instant.parse("2026-09-06T16:00:00Z").toEpochMilli()

        assertEquals(
            "2026年9月7日",
            formatNoteEditorDate(epochMs, ZoneId.of("Asia/Shanghai")),
        )
    }

    @Test
    fun notesMatchingFiltersByAllUnfiledAndSpecificNotebook() {
        val noteA = sampleNote("a", "笔记A", notebookId = "nb-1")
        val noteB = sampleNote("b", "笔记B", notebookId = "nb-2")
        val unfiledNote = sampleNote("c", "收集箱", notebookId = null)
        val allNotes = listOf(noteA, noteB, unfiledNote)

        assertEquals(listOf("a", "b", "c"), notesMatching(allNotes, NotesScope.All).map { it.id })
        assertEquals(listOf("c"), notesMatching(allNotes, NotesScope.Unfiled).map { it.id })
        assertEquals(listOf("a"), notesMatching(allNotes, NotesScope.Notebook("nb-1")).map { it.id })
        assertEquals(listOf("b"), notesMatching(allNotes, NotesScope.Notebook("nb-2")).map { it.id })
        assertTrue(notesMatching(allNotes, NotesScope.Notebook("unknown")).isEmpty())
    }

    @Test
    fun notebookStatsFromAggregatesCountsAndExcludesUnfiled() {
        val notes = listOf(
            sampleNote("1", "笔记1", notebookId = "nb-1", visibleChars = 120),
            sampleNote("2", "笔记2", notebookId = "nb-1", visibleChars = 80),
            sampleNote("3", "笔记3", notebookId = "nb-2", visibleChars = 300),
            sampleNote("4", "未归档", notebookId = null, visibleChars = 500),
        )

        val stats = notebookStatsFrom(notes)
        assertEquals(2, stats.size)
        assertEquals(2, stats["nb-1"]?.noteCount)
        assertEquals(200, stats["nb-1"]?.characterCount)
        assertEquals(1, stats["nb-2"]?.noteCount)
        assertEquals(300, stats["nb-2"]?.characterCount)
        assertEquals(setOf("nb-1", "nb-2"), stats.keys)
        assertNull(stats["unknown"])
    }

    @Test
    fun unfiledStatsFromComputesUnfiledNotes() {
        val notes = listOf(
            sampleNote("1", "笔记1", notebookId = "nb-1", visibleChars = 120),
            sampleNote("2", "未归档1", notebookId = null, visibleChars = 50),
            sampleNote("3", "未归档2", notebookId = null, visibleChars = 75),
        )

        val stats = unfiledStatsFrom(notes)
        assertEquals(2, stats.noteCount)
        assertEquals(125, stats.characterCount)
    }

    @Test
    fun notebookNameResolvesExistingNameOrReturnsNull() {
        val notebooks = listOf(
            Notebook("nb-1", "工作笔记本", 0L, 1L, 1L),
            Notebook("nb-2", "生活日记", 1L, 2L, 2L),
        )

        assertEquals("工作笔记本", notebookName(notebooks, "nb-1"))
        assertEquals("生活日记", notebookName(notebooks, "nb-2"))
        assertNull(notebookName(notebooks, null))
        assertNull(notebookName(notebooks, "non-existent"))
    }

    @Test
    fun notesUiStateInitialDefaultsAndSelectionTransitions() {
        val state = NotesUiState()

        assertEquals(NoteListSort.UpdatedAt, state.collectionSort)
        assertEquals(NoteListSort.Manual, state.notebookSort)
        assertTrue(state.selectedIds.isEmpty())
        assertFalse(state.createNotebookVisible)
        assertFalse(state.sortMenuVisible)
        assertFalse(state.moreVisible)
        assertFalse(state.moveVisible)
        assertFalse(state.trashConfirmVisible)
        assertFalse(state.deleteNotebookVisible)
        assertFalse(state.renameVisible)
        assertFalse(state.linkDialogVisible)
        assertFalse(state.backgroundPickerVisible)

        state.selectedIds = setOf("note-1", "note-2")
        assertEquals(2, state.selectedIds.size)
        assertTrue(state.selectedIds.contains("note-1"))

        state.selectedIds = emptySet()
        assertTrue(state.selectedIds.isEmpty())

        state.createNotebookName = "新笔记本"
        state.renameDraft = "重命名草稿"
        state.linkDraft = "https://example.com"
        assertEquals("新笔记本", state.createNotebookName)
        assertEquals("重命名草稿", state.renameDraft)
        assertEquals("https://example.com", state.linkDraft)
    }
}

// -- Functions

private fun sampleNote(
    id: String,
    title: String,
    notebookId: String? = null,
    sortIndex: Long = 0L,
    visibleChars: Int = 0,
    createdAt: Long = 1L,
    updatedAt: Long = 1L,
) = Note(
    id = id,
    notebookId = notebookId,
    title = title,
    document = emptyNoteDocument(),
    backgroundKey = null,
    sortIndex = sortIndex,
    visibleCharacterCount = visibleChars,
    latinWordCount = 0,
    summary = "",
    createdAtEpochMs = createdAt,
    updatedAtEpochMs = updatedAt,
    deletedAtEpochMs = null,
    originalNotebookName = null,
)
