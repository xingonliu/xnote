package com.xnote.app.domain.document

import org.junit.Assert.*
import org.junit.Test

// -- Tests

class DocumentSelectionTest {
    // -- State and Variables

    private val document = NoteDocument(blocks = listOf(
        TextBlock("a", inlines = listOf(InlineRun("前甲", bold = true))),
        TextBlock("b", inlines = listOf(InlineRun("中😀文"))),
        TextBlock("c", inlines = listOf(InlineRun("乙后", italic = true))),
    ))
    private val range = EditorSelection("a", 1, 1, endBlockId = "c")

    // -- Functions

    @Test fun forwardAndBackwardSelectionsCopyTheSameParagraphs() {
        assertFalse(range.isCollapsed)
        assertEquals("甲\n中😀文\n乙", document.selectedText(range))
        val backward = selectionBetween(range.focus(), range.anchor())
        assertEquals(document.selectionParts(range), document.selectionParts(backward))
        assertEquals(document.selectedText(range), document.selectedText(backward))
    }

    @Test fun selectAllIncludesEveryParagraphAndUnicodeCharacter() {
        assertEquals("前甲\n中😀文\n乙后", document.selectedText(requireNotNull(document.selectAllText())))
        val caret = selectionBetween(range.anchor(), range.anchor())
        assertTrue(caret.isCollapsed)
        assertFalse(caret.isCrossField)
    }

    @Test fun replacementMergesBoundaryParagraphsAndPreservesOutsideStyles() {
        val change = document.replaceSelectedText(range, "替换", InlineMarks(underline = true))
        assertEquals(1, change.document.blocks.size)
        assertEquals(listOf(InlineRun("前", bold = true), InlineRun("替换", underline = true),
            InlineRun("后", italic = true)), (change.document.blocks.single() as TextBlock).inlines)
        assertEquals(EditorSelection("a", 3, 3), change.selection)
    }

    @Test fun multilinePasteSplitsParagraphsAndRetainsUnselectedSuffix() {
        val change = document.replaceSelectedText(range, "新一\n新二", InlineMarks())
        assertEquals(listOf("前新一", "新二后"), change.document.blocks.filterIsInstance<TextBlock>().map { it.inlines.plainText() })
        assertEquals(2, change.selection.start)
        assertFalse(change.selection.isCrossField)
    }

    @Test fun deleteAllLeavesAnEditableEmptyParagraphAndUndoRestoresRange() {
        val all = requireNotNull(document.selectAllText())
        val history = EditorHistory()
        history.capture(EditorSnapshot("标题", document, all))
        val deleted = document.deleteBackward(all)
        assertEquals("", (deleted.document.blocks.single() as TextBlock).inlines.plainText())
        assertTrue(deleted.selection.isCollapsed)
        val restored = history.undo(EditorSnapshot("标题", deleted.document, deleted.selection))!!
        assertEquals(document, restored.document)
        assertEquals(all, restored.selection)
    }

    @Test fun inlineFormattingUsesOneToggleAcrossMixedParagraphs() {
        val marked = document.applyInlineMark(range, InlineMark.Bold, InlineMarks()).first.document
        assertTrue(marked.selectedRuns(range).all { it.bold })
        assertTrue((marked.blocks.first() as TextBlock).inlines.first().bold)
        assertFalse((marked.blocks.last() as TextBlock).inlines.last().bold)
        val unmarked = marked.applyInlineMark(range, InlineMark.Bold, InlineMarks()).first.document
        assertTrue(unmarked.selectedRuns(range).none { it.bold })
    }

    @Test fun linkEndingAtNextParagraphStartDoesNotInsertUrlThere() {
        val boundary = EditorSelection("a", 1, 0, endBlockId = "b")
        val changed = document.setLink(boundary, "https://example.com", InlineMarks()).first.document
        assertEquals(document.blocks[1], changed.blocks[1])
        assertEquals("https://example.com", (changed.blocks[0] as TextBlock).inlines.last().linkUrl)
    }

    @Test fun paragraphActionsCoverSelectionAndExcludeUnselectedEndParagraph() {
        val range = EditorSelection("a", 0, 0, endBlockId = "c")
        val centered = document.setAlignment(range, TextAlignment.Center).document
        assertEquals(listOf(TextAlignment.Center, TextAlignment.Center, TextAlignment.Left),
            centered.blocks.filterIsInstance<TextBlock>().map { it.alignment })
        val mixed = document.replaceBlock((document.blocks[0] as TextBlock).copy(quoted = true, listMarker = ListMarker.Bullet))
        assertTrue(mixed.toggleQuoted(this.range).document.blocks.filterIsInstance<TextBlock>().all { it.quoted })
        assertTrue(mixed.setListMarker(this.range, ListMarker.Bullet).document.blocks.filterIsInstance<TextBlock>().all { it.listMarker == ListMarker.Bullet })
    }

    @Test fun tableAndMediaKeepTheirStructureDuringTextReplacement() {
        val table = TableBlock("table", listOf(TableRow(listOf(TableCell(listOf(InlineRun("左"))), TableCell(listOf(InlineRun("右")))))))
        val image = ImageBlock("image", "attachment")
        val mixed = document.copy(blocks = listOf(document.blocks[0], image, table, document.blocks[2]))
        val all = requireNotNull(mixed.selectAllText())
        assertEquals("前甲\n左\t右\n乙后", mixed.selectedText(all))
        val replaced = mixed.replaceSelectedText(all, "新", InlineMarks()).document
        assertEquals(image, replaced.block("image"))
        val remainingTable = replaced.block("table") as TableBlock
        assertEquals(2, remainingTable.rows.single().cells.size)
        assertTrue(remainingTable.rows.single().cells.all { it.inlines.isEmpty() })
        assertEquals("新", (replaced.block("a") as TextBlock).inlines.plainText())
    }

    @Test fun rangeAcrossCellsInSameTableIsNotCollapsed() {
        val range = selectionBetween(TextPosition(TextAddress("table", 0, 0), 1), TextPosition(TextAddress("table", 0, 1), 1))
        assertTrue(range.isCrossField)
        assertFalse(range.isCollapsed)
        assertEquals(TextAddress("table", 0, 1), range.focus().address)
    }

    @Test fun emptyParagraphsInsideRangeReceiveParagraphStyle() {
        val withEmpty = document.replaceBlock(TextBlock("b"))
        assertTrue(withEmpty.setParagraphStyle(range, ParagraphStyle.Heading).document.blocks.filterIsInstance<TextBlock>()
            .all { it.paragraphStyle == ParagraphStyle.Heading })
    }
    private fun NoteDocument.selectedRuns(selection: EditorSelection): List<InlineRun> = selectionParts(selection).flatMap {
        selectedInlines(it).orEmpty().runsInRange(it.min, it.max)
    }

}
