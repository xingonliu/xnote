package com.xnote.app.domain.rules

import com.xnote.app.domain.document.DrawingBlock
import com.xnote.app.domain.document.ImageBlock
import com.xnote.app.domain.document.InlineRun
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.StickerBlock
import com.xnote.app.domain.document.TableCell
import com.xnote.app.domain.document.TableRow
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.document.emptyNoteDocument
import com.xnote.app.domain.document.emptyTableBlock
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// -- Tests

class MarkdownConversionRulesTest {
    @Test
    fun textAndTableCanConvert() {
        val document = NoteDocument(
            blocks = listOf(
                TextBlock(id = "t", inlines = listOf(InlineRun("hello"))),
                emptyTableBlock("table"),
            ),
        )
        assertTrue(conversionBlockers(document).isEmpty())
    }

    @Test
    fun imageStickerAndDrawingBlockConversion() {
        val document = NoteDocument(
            blocks = listOf(
                ImageBlock(id = "img", attachmentId = "a-1"),
                StickerBlock(id = "stk", attachmentId = "a-2"),
                DrawingBlock(id = "draw", attachmentId = "a-3", width = 100f, height = 80f),
            ),
        )
        assertEquals(
            setOf(
                ConversionBlocker.Image,
                ConversionBlocker.Sticker,
                ConversionBlocker.Drawing,
            ),
            conversionBlockers(document),
        )
    }

    @Test
    fun markdownNoteCannotConvertAgain() {
        val note = Note(
            id = "n-1",
            notebookId = null,
            title = "标题",
            kind = NoteKind.Markdown,
            document = null,
            markdownText = "# 标题\n正文",
            backgroundKey = null,
            sortIndex = 0L,
            visibleCharacterCount = 2,
            latinWordCount = 0,
            summary = "正文",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            deletedAtEpochMs = null,
            originalNotebookName = null,
        )
        assertEquals(setOf(ConversionBlocker.AlreadyMarkdown), conversionBlockers(note))
        assertFalse(canConvertToMarkdown(note))
    }

    @Test
    fun emptyRichNoteCanConvert() {
        val note = Note(
            id = "n-1",
            notebookId = null,
            title = "",
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
            deletedAtEpochMs = null,
            originalNotebookName = null,
        )
        assertTrue(canConvertToMarkdown(note))
    }

    @Test
    fun tableCellsAreNotMediaBlockers() {
        val document = NoteDocument(
            blocks = listOf(
                com.xnote.app.domain.document.TableBlock(
                    id = "table",
                    rows = listOf(
                        TableRow(cells = listOf(TableCell(inlines = listOf(InlineRun("单元格"))))),
                    ),
                ),
            ),
        )
        assertTrue(conversionBlockers(document).isEmpty())
    }
}
