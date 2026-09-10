package com.xnote.app.domain.model

import com.xnote.app.domain.document.*
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class LibraryStatisticsTest {
    @Test fun countsVisibleBodyAndMediaBlocksButExcludesTitlesAndTrash() {
        val body = listOf(
            TextBlock("text", inlines = listOf(InlineRun("中文 hello", bold = true))),
            TableBlock("table", listOf(TableRow(listOf(TableCell(listOf(InlineRun("世界"))))))),
            ImageBlock("i1", "shared"), ImageBlock("i2", "shared"), StickerBlock("s", "sticker"), DrawingBlock("d", "drawing", 100f, 100f),
        )
        val stats = libraryStatistics(listOf(note("a", body).copy(notebookId = "book"),
            note("b", listOf(TextBlock("body", inlines = listOf(InlineRun("未归档"))))),
            note("trash", body).copy(deletedAtEpochMs = 100)))
        assertEquals(2, stats.noteCount)
        assertEquals(12L, stats.characterCount)
        assertEquals(1L, stats.latinWordCount)
        assertEquals(mapOf("book" to 9L, null to 3L), stats.notebookCharacters)
        assertEquals(1, stats.tableNoteCount)
        assertEquals(1, stats.imageNoteCount)
        assertEquals(1, stats.stickerNoteCount)
        assertEquals(1, stats.drawingNoteCount)
        assertEquals(2, stats.imageCount)
        assertEquals(1, stats.stickerCount)
        assertEquals(1, stats.drawingCount)
    }

    @Test fun recentListsUseTheirOwnTimestampAndStableTieBreaker() {
        val notes = (0..6).map { index -> note("$index", emptyList()).copy(createdAtEpochMs = index.toLong(), updatedAtEpochMs = (6 - index).toLong()) }
        val stats = libraryStatistics(notes.reversed())
        assertEquals(listOf("6", "5", "4", "3", "2"), stats.recentlyCreated.map { it.id })
        assertEquals(listOf("0", "1", "2", "3", "4"), stats.recentlyUpdated.map { it.id })
        assertEquals(listOf("a", "b"), libraryStatistics(listOf(note("b", emptyList()), note("a", emptyList()))).recentlyCreated.map { it.id })
        assertEquals(0L, libraryStatistics(emptyList()).characterCount)
    }

    // -- Functions
    private fun note(id: String, blocks: List<NoteBlock>) = Note(id, null, "不计入的标题", NoteDocument(blocks = blocks), null,
        0, 999, 999, "不使用陈旧缓存统计", 1, 1, null, null)
}
