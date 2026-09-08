package com.xnote.app.domain.markdown

import com.xnote.app.domain.document.EditorChange
import com.xnote.app.domain.document.EditorSelection
import com.xnote.app.domain.document.InlineMarks
import com.xnote.app.domain.document.InlineRun
import com.xnote.app.domain.document.ListMarker
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.ParagraphStyle
import com.xnote.app.domain.document.TableBlock
import com.xnote.app.domain.document.TableCell
import com.xnote.app.domain.document.TableRow
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.document.plainText
import com.xnote.app.domain.document.replaceSelectedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// -- Tests

class MarkdownShortcutsTest {
    @Test
    fun headingAndSubheadingTriggerOnTrailingSpaceAtBlockStart() {
        val heading = type("# ")
        assertEquals(ParagraphStyle.Heading, heading.text().paragraphStyle)
        assertEquals("", heading.text().inlines.plainText())

        val subheading = type("## ")
        assertEquals(ParagraphStyle.Subheading, subheading.text().paragraphStyle)
    }

    @Test
    fun headingThreeRemainsLiteralCharacters() {
        val change = type("### ")
        assertEquals(ParagraphStyle.Body, change.text().paragraphStyle)
        assertEquals("### ", change.text().inlines.plainText())
    }

    @Test
    fun listsQuotesAndChecklistFollowPriority() {
        assertEquals(ListMarker.Dash, type("- ").text().listMarker)
        assertEquals(ListMarker.Bullet, type("* ").text().listMarker)
        assertEquals(ListMarker.Bullet, type("+ ").text().listMarker)
        assertEquals(ListMarker.Numbered, type("1. ").text().listMarker)
        assertTrue(type("> ").text().quoted)

        val checklist = type("- [ ] ")
        assertEquals(ListMarker.Checklist, checklist.text().listMarker)
        assertFalse(checklist.text().checked)
        assertEquals("", checklist.text().inlines.plainText())

        val checked = type("- [x] ")
        assertEquals(ListMarker.Checklist, checked.text().listMarker)
        assertTrue(checked.text().checked)
    }

    @Test
    fun dashListCanUpgradeToChecklist() {
        val afterDash = type("- ")
        val afterChecklist = type("[ ] ", afterDash)
        assertEquals(ListMarker.Checklist, afterChecklist.text().listMarker)
        assertEquals("", afterChecklist.text().inlines.plainText())
    }

    @Test
    fun inlineMarksApplyOnClosingDelimiterAndPreferLongerTokens() {
        val bold = type("**重点**")
        assertEquals("重点", bold.text().inlines.plainText())
        assertTrue(bold.text().inlines.single().bold)

        val italic = type("*斜体*")
        assertTrue(italic.text().inlines.single().italic)

        val strike = type("~~删除~~")
        assertTrue(strike.text().inlines.single().strikethrough)

        val highlight = type("==高亮==")
        assertTrue(highlight.text().inlines.single().highlight)

        val underscoreBold = type("__粗体__")
        assertTrue(underscoreBold.text().inlines.single().bold)
    }

    @Test
    fun nestedBoldAndItalicCanCombine() {
        val change = type("**粗 *斜* 体**")
        assertEquals("粗 斜 体", change.text().inlines.plainText())
        assertTrue(change.text().inlines.all { it.bold })
        assertTrue(change.text().inlines.any { it.italic && it.text == "斜" })
    }

    @Test
    fun linkAppliesOnClosingParenthesisAndKeepsEmptyUrlLiteral() {
        val linked = type("[文档](https://example.com)")
        val run = linked.text().inlines.single()
        assertEquals("文档", run.text)
        assertEquals("https://example.com", run.linkUrl)

        val emptyUrl = type("[文档]()")
        assertEquals("[文档]()", emptyUrl.text().inlines.plainText())
        assertTrue(emptyUrl.text().inlines.all { it.linkUrl == null })
    }

    @Test
    fun escapedUnclosedAndWordInternalMarkersStayLiteral() {
        assertEquals("\\*星\\*", type("\\*星\\*").text().inlines.plainText())
        assertEquals("**未闭合", type("**未闭合").text().inlines.plainText())
        assertEquals("foo_bar_", type("foo_bar_").text().inlines.plainText())
        assertEquals("`code`", type("`code`").text().inlines.plainText())
    }

    @Test
    fun composingDisabledAndMultilinePasteDoNotConvert() {
        val composing = apply("body", "# ", composing = true)
        assertEquals("# ", composing.text().inlines.plainText())
        assertEquals(ParagraphStyle.Body, composing.text().paragraphStyle)

        val disabled = apply("body", "# ", enabled = false)
        assertEquals("# ", disabled.text().inlines.plainText())

        val pasted = apply("body", "# 标题\n下一行")
        assertEquals(ParagraphStyle.Body, pasted.text().paragraphStyle)
        assertTrue(pasted.document.blocks.size >= 1)
    }

    @Test
    fun tableCellsAllowInlineMarksButNotBlockPrefixes() {
        val table = tableDocument()
        val selection = EditorSelection(blockId = "table", tableRow = 0, tableColumn = 0)
        val heading = type("# ", EditorChange(table, selection))
        val cell = (heading.document.blocks.single() as TableBlock).rows[0].cells[0]
        assertEquals("# ", cell.inlines.plainText())

        val bold = type("**格**", EditorChange(table, selection))
        val boldCell = (bold.document.blocks.single() as TableBlock).rows[0].cells[0]
        assertEquals("格", boldCell.inlines.plainText())
        assertTrue(boldCell.inlines.single().bold)
    }

    @Test
    fun headingPrefixAtStartKeepsFollowingText() {
        val seeded = EditorChange(
            document = NoteDocument(blocks = listOf(TextBlock(id = "body", inlines = listOf(InlineRun("正文"))))),
            selection = EditorSelection("body"),
        )
        val withPrefix = seeded.document.replaceSelectedText(
            EditorSelection("body", 0, 0),
            "# ",
            InlineMarks(),
        )
        val converted = applyMarkdownShortcut(
            document = withPrefix.document,
            selection = withPrefix.selection,
            inserted = "# ",
            enabled = true,
            composing = false,
        )
        assertEquals(ParagraphStyle.Heading, converted?.let { it.text() }?.paragraphStyle)
        assertEquals("正文", converted?.text()?.inlines?.plainText())
        assertEquals(0, converted?.selection?.start)
    }
}

// -- Functions

private fun type(input: String, start: EditorChange = emptyBody()): EditorChange {
    var current = start
    for (character in input) {
        current = apply(current.selection.blockId, character.toString(), current, table = current.selection.isTable)
    }
    return current
}

private fun apply(
    blockId: String,
    inserted: String,
    start: EditorChange = emptyBody(),
    enabled: Boolean = true,
    composing: Boolean = false,
    table: Boolean = false,
): EditorChange {
    val selection = if (table) start.selection else start.selection.copy(blockId = blockId)
    val written = start.document.replaceSelectedText(selection, inserted, InlineMarks())
    return applyMarkdownShortcut(
        document = written.document,
        selection = written.selection,
        inserted = inserted,
        enabled = enabled,
        composing = composing,
    ) ?: written
}

private fun emptyBody(): EditorChange {
    val document = NoteDocument(blocks = listOf(TextBlock(id = "body")))
    return EditorChange(document, EditorSelection("body"))
}

private fun tableDocument(): NoteDocument = NoteDocument(
    blocks = listOf(
        TableBlock(
            id = "table",
            rows = listOf(TableRow(cells = listOf(TableCell()))),
        ),
    ),
)

private fun EditorChange.text(): TextBlock = document.blocks.filterIsInstance<TextBlock>().first()
