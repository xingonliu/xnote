package com.xnote.app.domain.text

import com.xnote.app.domain.document.DrawingBlock
import com.xnote.app.domain.document.ImageBlock
import com.xnote.app.domain.document.InlineRun
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.TableCell
import com.xnote.app.domain.document.TableRow
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteKind
import org.junit.Assert.assertEquals
import org.junit.Test

// -- Tests

class VisibleTextStatsTest {
    @Test
    fun countsTextBlocksAndTableCellsButNotTitleOrMedia() {
        val document = NoteDocument(
            blocks = listOf(
                TextBlock(id = "t1", inlines = listOf(InlineRun("你好 Hello"))),
                com.xnote.app.domain.document.TableBlock(
                    id = "table",
                    rows = listOf(
                        TableRow(
                            cells = listOf(
                                TableCell(inlines = listOf(InlineRun("世界"))),
                                TableCell(inlines = listOf(InlineRun("world"))),
                            ),
                        ),
                    ),
                ),
                ImageBlock(id = "img", attachmentId = "photo.png"),
                DrawingBlock(id = "draw", attachmentId = "ink", width = 10f, height = 10f),
            ),
        )
        val note = Note(
            id = "n-1",
            notebookId = null,
            title = "这篇标题不应计入",
            kind = NoteKind.Rich,
            document = document,
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
        val stats = visibleTextStats(note)
        assertEquals(14, stats.characterCount)
        assertEquals(2, stats.latinWordCount)
    }

    @Test
    fun markdownStripsSyntaxAndLeadingTitleHeading() {
        val markdown = """
            # 笔记标题
            这是 **正文** 和 [链接](https://example.com)
            ```
            code
            ```
        """.trimIndent()
        val visible = MarkdownVisibleText.extract(markdown)
        val stats = visibleTextStats(visible)
        assertEquals(11, stats.characterCount)
    }

    @Test
    fun markdownKeepsEscapedSyntaxAsVisibleText() {
        val markdown = "# 标题\n\n字面 \\* 星号与 A\\|B"

        assertEquals("\n字面 * 星号与 A|B", MarkdownVisibleText.extract(markdown))
    }

    @Test
    fun whitespaceIsNotCountedAsCharacters() {
        assertEquals(0, visibleTextStats(" \n\t").characterCount)
        assertEquals(2, visibleTextStats("你好").characterCount)
    }
}
