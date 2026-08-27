package com.xnote.app.domain.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// -- Tests

class NoteDocumentEditingTest {
    @Test
    fun typingReplacesTheSelectedRangeAndMovesTheCaret() {
        val document = document(text("t1", "你好"))
        val change = document.replaceSelectedText(
            selection = EditorSelection("t1", start = 2, end = 2),
            newText = "世界",
            marks = InlineMarks(),
        )
        val block = change.document.block("t1") as TextBlock
        assertEquals("你好世界", block.inlines.plainText())
        assertEquals(4, change.selection.start)
    }

    @Test
    fun enterSplitsAHeadingIntoABodyBlock() {
        val document = document(
            TextBlock(
                id = "h1",
                paragraphStyle = ParagraphStyle.Heading,
                inlines = listOf(InlineRun("标题后接正文")),
            ),
        )
        val change = document.splitAt(EditorSelection("h1", start = 2, end = 2), "b1")
        val heading = change.document.block("h1") as TextBlock
        val body = change.document.block("b1") as TextBlock
        assertEquals("标题", heading.inlines.plainText())
        assertEquals(ParagraphStyle.Heading, heading.paragraphStyle)
        assertEquals("后接正文", body.inlines.plainText())
        assertEquals(ParagraphStyle.Body, body.paragraphStyle)
        assertEquals("b1", change.selection.blockId)
    }

    @Test
    fun enterOnEmptyListItemRemovesTheMarker() {
        val document = document(
            TextBlock(
                id = "l1",
                listMarker = ListMarker.Bullet,
                inlines = emptyList(),
            ),
        )
        val change = document.splitAt(EditorSelection("l1"), "unused")
        val block = change.document.block("l1") as TextBlock
        assertEquals(ListMarker.None, block.listMarker)
        assertEquals(1, change.document.blocks.size)
    }

    @Test
    fun backspaceAtStartMergesWithThePreviousParagraph() {
        val document = document(
            text("a", "你好"),
            text("b", "世界"),
        )
        val change = document.deleteBackward(EditorSelection("b"))
        val merged = change.document.block("a") as TextBlock
        assertEquals(1, change.document.blocks.size)
        assertEquals("你好世界", merged.inlines.plainText())
        assertEquals(2, change.selection.start)
    }

    @Test
    fun backspaceAtStartOfIndentedListDecreasesIndentFirst() {
        val document = document(
            TextBlock(id = "l1", listMarker = ListMarker.Bullet, indent = 2, inlines = listOf(InlineRun("项"))),
        )
        val afterIndent = document.deleteBackward(EditorSelection("l1"))
        val indented = afterIndent.document.block("l1") as TextBlock
        assertEquals(1, indented.indent)
        val afterMarker = afterIndent.document.deleteBackward(EditorSelection("l1"))
        val flattened = afterMarker.document.block("l1") as TextBlock
        assertEquals(0, flattened.indent)
        val afterExit = afterMarker.document.deleteBackward(EditorSelection("l1"))
        val body = afterExit.document.block("l1") as TextBlock
        assertEquals(ListMarker.None, body.listMarker)
    }

    @Test
    fun togglingTheSameListMarkerTurnsItOff() {
        val document = document(TextBlock(id = "t1", listMarker = ListMarker.Numbered, inlines = listOf(InlineRun("一"))))
        val change = document.setListMarker(EditorSelection("t1"), ListMarker.Numbered)
        val block = change.document.block("t1") as TextBlock
        assertEquals(ListMarker.None, block.listMarker)
    }

    @Test
    fun collapsedHeadingHidesUntilTheNextHeading() {
        val document = document(
            TextBlock(id = "h1", paragraphStyle = ParagraphStyle.Heading, collapsed = true, inlines = listOf(InlineRun("一"))),
            text("b1", "隐藏"),
            emptyTableBlock("table"),
            TextBlock(id = "h2", paragraphStyle = ParagraphStyle.Heading, inlines = listOf(InlineRun("二"))),
            text("b2", "可见"),
        )
        assertEquals(setOf("b1", "table"), document.hiddenBlockIds())
    }

    @Test
    fun numberedLabelsRestartAfterABreakAndNestByIndent() {
        val document = document(
            TextBlock(id = "n1", listMarker = ListMarker.Numbered, inlines = listOf(InlineRun("一"))),
            TextBlock(id = "n2", listMarker = ListMarker.Numbered, indent = 1, inlines = listOf(InlineRun("子"))),
            TextBlock(id = "n3", listMarker = ListMarker.Numbered, inlines = listOf(InlineRun("二"))),
            text("gap", "打断"),
            TextBlock(id = "n4", listMarker = ListMarker.Numbered, inlines = listOf(InlineRun("重新"))),
        )
        val labels = document.numberedLabels()
        assertEquals(1, labels["n1"])
        assertEquals(1, labels["n2"])
        assertEquals(2, labels["n3"])
        assertEquals(1, labels["n4"])
    }

    @Test
    fun insertTableAddsATwoByTwoGridAfterTheCurrentBlock() {
        val document = document(text("t1", "上文"))
        val change = document.insertTable(EditorSelection("t1"), "table")
        assertEquals(2, change.document.blocks.size)
        val table = change.document.block("table") as TableBlock
        assertEquals(2, table.rows.size)
        assertEquals(2, table.columnCount())
        assertEquals("table", change.selection.blockId)
        assertEquals(0, change.selection.tableRow)
    }

    @Test
    fun tableCellTextAndRowInsertionStayRectangular() {
        val started = document(emptyTableBlock("table"))
        val typed = started.replaceSelectedText(
            EditorSelection("table", start = 0, end = 0, tableRow = 0, tableColumn = 0),
            "单元格",
            InlineMarks(bold = true),
        )
        val withRow = typed.document.insertTableRow(typed.selection, afterRow = 0)
        val table = withRow.document.block("table") as TableBlock
        assertEquals("单元格", table.cell(0, 0)?.inlines?.plainText())
        assertTrue(table.cell(0, 0)?.inlines?.first()?.bold == true)
        assertEquals(3, table.rows.size)
        assertEquals(2, table.columnCount())
    }

    @Test
    fun deletingTheLastTableRowRemovesTheTable() {
        val document = document(
            text("keep", "保留"),
            TableBlock(id = "table", rows = listOf(TableRow(cells = listOf(TableCell())))),
        )
        val change = document.deleteTableRow(
            EditorSelection("table", tableRow = 0, tableColumn = 0),
            row = 0,
            fallbackTextId = "fallback",
        )
        assertEquals(listOf("keep"), change.document.blocks.map { it.id })
    }

    @Test
    fun inlineMarkOnACollapsedCaretOnlyUpdatesTypingMarks() {
        val document = document(text("t1", "正文"))
        val (change, marks) = document.applyInlineMark(
            selection = EditorSelection("t1", start = 1, end = 1),
            mark = InlineMark.Bold,
            typingMarks = InlineMarks(),
        )
        assertEquals(document, change.document)
        assertTrue(marks.bold)
    }

    @Test
    fun inlineMarkOnARangeRewritesOnlyTheSelection() {
        val document = document(text("t1", "abcdef"))
        val (change, _) = document.applyInlineMark(
            selection = EditorSelection("t1", start = 2, end = 4),
            mark = InlineMark.Italic,
            typingMarks = InlineMarks(),
        )
        val block = change.document.block("t1") as TextBlock
        assertFalse(block.inlines[0].italic)
        assertTrue(block.inlines[1].italic)
        assertEquals("cd", block.inlines[1].text)
    }
}

// -- Fixtures

private fun document(vararg blocks: NoteBlock): NoteDocument = NoteDocument(blocks = blocks.toList())

private fun text(id: String, value: String): TextBlock = TextBlock(id = id, inlines = listOf(InlineRun(value)))
