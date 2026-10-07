package com.xnote.app.feature.notes.editor

import com.xnote.app.design.XNoteParagraphStyle
import com.xnote.app.design.XNoteRichTextAction
import com.xnote.app.domain.document.EditorSelection
import com.xnote.app.domain.document.ImageBlock
import com.xnote.app.domain.document.InlineMarks
import com.xnote.app.domain.document.InlineRun
import com.xnote.app.domain.document.ListMarker
import com.xnote.app.domain.document.MaxTextIndent
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.ParagraphStyle
import com.xnote.app.domain.document.TableCell
import com.xnote.app.domain.document.TableRow
import com.xnote.app.domain.document.TableBlock
import com.xnote.app.domain.document.TextAlignment
import com.xnote.app.domain.document.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// -- Tests

class NoteToolbarStateTest {
    @Test
    fun toolbarStateForCollapsedSelectionUsesTypingMarksAndBlockFormatting() {
        val block = TextBlock(
            id = "t1",
            inlines = listOf(InlineRun("测试内容")),
            paragraphStyle = ParagraphStyle.Body,
            listMarker = ListMarker.Bullet,
            quoted = true,
            alignment = TextAlignment.Center,
            indent = 1,
        )
        val doc = NoteDocument(blocks = listOf(block))
        val selection = EditorSelection(blockId = "t1", start = 2, end = 2)
        val typingMarks = InlineMarks(bold = true, italic = true)

        val state = toolbarStateFor(doc, selection, typingMarks)

        assertEquals(XNoteParagraphStyle.Body, state.paragraphStyle)
        assertTrue(state.selectedActions.contains(XNoteRichTextAction.Bold))
        assertTrue(state.selectedActions.contains(XNoteRichTextAction.Italic))
        assertFalse(state.selectedActions.contains(XNoteRichTextAction.Underline))
        assertTrue(state.selectedActions.contains(XNoteRichTextAction.BulletedList))
        assertTrue(state.selectedActions.contains(XNoteRichTextAction.Quote))
        assertTrue(state.selectedActions.contains(XNoteRichTextAction.AlignCenter))
        assertFalse(state.disabledActions.contains(XNoteRichTextAction.DecreaseIndent))
        assertFalse(state.disabledActions.contains(XNoteRichTextAction.IncreaseIndent))
        assertTrue(state.disabledActions.contains(XNoteRichTextAction.ToggleHeadingCollapse))
    }

    @Test
    fun toolbarStateReflectsVariousListMarkersAndAlignments() {
        val dashBlock = TextBlock("t1", listMarker = ListMarker.Dash, alignment = TextAlignment.Left)
        val numberedBlock = TextBlock("t2", listMarker = ListMarker.Numbered, alignment = TextAlignment.Right)
        val checklistBlock = TextBlock("t3", listMarker = ListMarker.Checklist)
        val plainBlock = TextBlock("t4", listMarker = ListMarker.None)
        val doc = NoteDocument(blocks = listOf(dashBlock, numberedBlock, checklistBlock, plainBlock))

        val dashState = toolbarStateFor(doc, EditorSelection("t1"), InlineMarks())
        assertTrue(dashState.selectedActions.contains(XNoteRichTextAction.DashedList))
        assertTrue(dashState.selectedActions.contains(XNoteRichTextAction.AlignStart))

        val numberedState = toolbarStateFor(doc, EditorSelection("t2"), InlineMarks())
        assertTrue(numberedState.selectedActions.contains(XNoteRichTextAction.NumberedList))
        assertTrue(numberedState.selectedActions.contains(XNoteRichTextAction.AlignEnd))

        val checklistState = toolbarStateFor(doc, EditorSelection("t3"), InlineMarks())
        assertTrue(checklistState.selectedActions.contains(XNoteRichTextAction.Checklist))

        val plainState = toolbarStateFor(doc, EditorSelection("t4"), InlineMarks())
        assertFalse(plainState.selectedActions.contains(XNoteRichTextAction.BulletedList))
        assertFalse(plainState.selectedActions.contains(XNoteRichTextAction.DashedList))
        assertFalse(plainState.selectedActions.contains(XNoteRichTextAction.NumberedList))
        assertFalse(plainState.selectedActions.contains(XNoteRichTextAction.Checklist))
    }

    @Test
    fun toolbarStateForIndentBoundariesDisablesExpectedActions() {
        val minIndentBlock = TextBlock("t1", indent = 0)
        val maxIndentBlock = TextBlock("t2", indent = MaxTextIndent)
        val doc = NoteDocument(blocks = listOf(minIndentBlock, maxIndentBlock))

        val minState = toolbarStateFor(doc, EditorSelection("t1"), InlineMarks())
        assertTrue(minState.disabledActions.contains(XNoteRichTextAction.DecreaseIndent))
        assertFalse(minState.disabledActions.contains(XNoteRichTextAction.IncreaseIndent))

        val maxState = toolbarStateFor(doc, EditorSelection("t2"), InlineMarks())
        assertFalse(maxState.disabledActions.contains(XNoteRichTextAction.DecreaseIndent))
        assertTrue(maxState.disabledActions.contains(XNoteRichTextAction.IncreaseIndent))
    }

    @Test
    fun toolbarStateForHeadingCollapseToggle() {
        val collapsedHeading = TextBlock(
            id = "h1",
            paragraphStyle = ParagraphStyle.Heading,
            collapsed = true,
        )
        val expandedSubheading = TextBlock(
            id = "h2",
            paragraphStyle = ParagraphStyle.Subheading,
            collapsed = false,
        )
        val doc = NoteDocument(blocks = listOf(collapsedHeading, expandedSubheading))

        val collapsedState = toolbarStateFor(doc, EditorSelection("h1"), InlineMarks())
        assertEquals(XNoteParagraphStyle.Heading, collapsedState.paragraphStyle)
        assertFalse(collapsedState.disabledActions.contains(XNoteRichTextAction.ToggleHeadingCollapse))
        assertTrue(collapsedState.selectedActions.contains(XNoteRichTextAction.ToggleHeadingCollapse))

        val expandedState = toolbarStateFor(doc, EditorSelection("h2"), InlineMarks())
        assertEquals(XNoteParagraphStyle.Subheading, expandedState.paragraphStyle)
        assertFalse(expandedState.disabledActions.contains(XNoteRichTextAction.ToggleHeadingCollapse))
        assertFalse(expandedState.selectedActions.contains(XNoteRichTextAction.ToggleHeadingCollapse))
    }

    @Test
    fun toolbarStateForExpandedSelectionCalculatesMarksFromInlines() {
        val inlines = listOf(
            InlineRun("平淡文字"),
            InlineRun("加粗加下划线", bold = true, underline = true),
            InlineRun("链接文字", linkUrl = "https://example.com"),
        )
        val block = TextBlock("t1", inlines = inlines)
        val doc = NoteDocument(blocks = listOf(block))

        val selection = EditorSelection("t1", start = 4, end = 10)
        val state = toolbarStateFor(doc, selection, InlineMarks())

        assertTrue(state.selectedActions.contains(XNoteRichTextAction.Bold))
        assertTrue(state.selectedActions.contains(XNoteRichTextAction.Underline))
        assertFalse(state.selectedActions.contains(XNoteRichTextAction.Italic))
        assertFalse(state.selectedActions.contains(XNoteRichTextAction.Link))

        val linkSelection = EditorSelection("t1", start = 10, end = 14)
        val linkState = toolbarStateFor(doc, linkSelection, InlineMarks())
        assertTrue(linkState.selectedActions.contains(XNoteRichTextAction.Link))
    }

    @Test
    fun toolbarStateForTableBlockDisablesUnsupportedFormattingAndReflectsTableSelection() {
        val table = TableBlock(
            id = "tb1",
            rows = listOf(
                TableRow(
                    cells = listOf(
                        TableCell(inlines = listOf(InlineRun("单元格", bold = true))),
                    ),
                ),
            ),
        )
        val doc = NoteDocument(blocks = listOf(table))
        val selection = EditorSelection(blockId = "tb1", start = 0, end = 3, tableRow = 0, tableColumn = 0)

        val state = toolbarStateFor(doc, selection, InlineMarks())

        assertEquals(XNoteParagraphStyle.Body, state.paragraphStyle)
        assertTrue(state.selectedActions.contains(XNoteRichTextAction.Table))
        assertTrue(state.selectedActions.contains(XNoteRichTextAction.Bold))

        val expectedDisabled = setOf(
            XNoteRichTextAction.ParagraphStyle,
            XNoteRichTextAction.BulletedList,
            XNoteRichTextAction.DashedList,
            XNoteRichTextAction.NumberedList,
            XNoteRichTextAction.Checklist,
            XNoteRichTextAction.Quote,
            XNoteRichTextAction.DecreaseIndent,
            XNoteRichTextAction.IncreaseIndent,
            XNoteRichTextAction.AlignStart,
            XNoteRichTextAction.AlignCenter,
            XNoteRichTextAction.AlignEnd,
            XNoteRichTextAction.ToggleHeadingCollapse,
        )
        assertEquals(expectedDisabled, state.disabledActions)

        val collapsedTableSelection = EditorSelection(blockId = "tb1", start = 1, end = 1, tableRow = 0, tableColumn = 0)
        val collapsedState = toolbarStateFor(doc, collapsedTableSelection, InlineMarks(italic = true))
        assertTrue(collapsedState.selectedActions.contains(XNoteRichTextAction.Italic))
    }

    @Test
    fun toolbarStateForCrossFieldSelectionIntersectsActions() {
        val blockA = TextBlock(
            id = "a",
            inlines = listOf(InlineRun("第一段加粗", bold = true)),
            quoted = true,
        )
        val blockB = TextBlock(
            id = "b",
            inlines = listOf(InlineRun("第二段加粗", bold = true)),
            quoted = false,
        )
        val doc = NoteDocument(blocks = listOf(blockA, blockB))
        val selection = EditorSelection(blockId = "a", start = 0, end = 5, endBlockId = "b")

        val state = toolbarStateFor(doc, selection, InlineMarks())

        assertTrue(state.selectedActions.contains(XNoteRichTextAction.Bold))
        assertFalse(state.selectedActions.contains(XNoteRichTextAction.Quote))
    }

    @Test
    fun toolbarStateForNonTextBlockDisablesAllActions() {
        val image = ImageBlock(id = "img-1", attachmentId = "att-1")
        val doc = NoteDocument(blocks = listOf(image))
        val selection = EditorSelection(blockId = "img-1")

        val state = toolbarStateFor(doc, selection, InlineMarks())

        assertEquals(XNoteRichTextAction.entries.toSet(), state.disabledActions)
    }

    @Test
    fun paragraphStyleConversionsBetweenDomainAndToolbar() {
        assertEquals(XNoteParagraphStyle.Body, ParagraphStyle.Body.toToolbar())
        assertEquals(XNoteParagraphStyle.Heading, ParagraphStyle.Heading.toToolbar())
        assertEquals(XNoteParagraphStyle.Subheading, ParagraphStyle.Subheading.toToolbar())
        assertEquals(XNoteParagraphStyle.Monospace, ParagraphStyle.Monospace.toToolbar())

        assertEquals(ParagraphStyle.Body, XNoteParagraphStyle.Body.toDomain())
        assertEquals(ParagraphStyle.Heading, XNoteParagraphStyle.Heading.toDomain())
        assertEquals(ParagraphStyle.Subheading, XNoteParagraphStyle.Subheading.toDomain())
        assertEquals(ParagraphStyle.Monospace, XNoteParagraphStyle.Monospace.toDomain())
    }

    @Test
    fun visibleBlocksExcludesHiddenChildrenOfCollapsedHeadings() {
        val heading = TextBlock(id = "h1", paragraphStyle = ParagraphStyle.Heading, collapsed = true)
        val child = TextBlock(id = "c1", indent = 1)
        val nextSection = TextBlock(id = "h2", paragraphStyle = ParagraphStyle.Heading, collapsed = false)
        val doc = NoteDocument(blocks = listOf(heading, child, nextSection))

        val visible = doc.visibleBlocks()

        assertEquals(listOf("h1", "h2"), visible.map { it.id })
    }
}
