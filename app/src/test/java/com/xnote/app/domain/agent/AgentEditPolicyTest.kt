package com.xnote.app.domain.agent

import com.xnote.app.domain.document.*
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentEditPolicyTest {
    private val text = TextBlock("text", inlines = listOf(InlineRun("用户内容")))
    private val media = ImageBlock("image", "attachment")
    private val document = NoteDocument(blocks = listOf(text, media, ImageBlock("second", "other")))

    @Test fun textChangesKeepMediaAndRequireLatestVersion() {
        val proposed = document.copy(blocks = listOf(text.copy(inlines = listOf(InlineRun("新内容")))) + document.blocks.drop(1))
        assertEquals(AgentEditValidation.Valid, validateAgentEdit(document, proposed, "v1", "v1"))
        assertEquals(AgentEditValidation.StaleVersion, validateAgentEdit(document, proposed, "v2", "v1"))
    }

    @Test fun mediaCannotBeChangedRemovedOrReordered() {
        val proposals = listOf(listOf(text), listOf(text, media.copy(scale = 2f), document.blocks.last()), document.blocks.reversed())
        proposals.forEach {
            assertEquals(AgentEditValidation.ProtectedContent, validateAgentEdit(document, document.copy(blocks = it), "v", "v"))
        }
    }

    @Test fun malformedStructureAndSelectionAreRejected() {
        for (blocks in listOf(emptyList(), listOf(text, text), listOf(TableBlock("table")))) {
            assertEquals(AgentEditValidation.InvalidDocument, validateAgentEdit(document, NoteDocument(blocks = blocks), "v", "v"))
        }
        for (selection in listOf(AgentSelection("old", "text", 0, 1), AgentSelection("v", "missing", 0, 1), AgentSelection("v", "text", -1, 2), AgentSelection("v", "text", 0, 99))) {
            assertEquals(AgentEditValidation.InvalidSelection, validateAgentEdit(document, document, "v", "v", selection))
        }
    }

    @Test fun unknownFieldsAreNotSilentlyAccepted() {
        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            decodeAgentDocument("""{"blocks":[],"unexpected":true}""")
        }
    }

    @Test fun selectionPolishPreservesOutsideTextStylesBlocksAndUnicodeBoundaries() {
        val block = TextBlock("selected", inlines = listOf(InlineRun("前缀😀", bold = true), InlineRun("原文"), InlineRun("后缀", italic = true)))
        val base = NoteDocument(blocks = listOf(block, text))
        val selection = AgentSelection("v", "selected", 4, 6)
        val polished = block.copy(inlines = listOf(InlineRun("前缀😀", bold = true), InlineRun("润色后的内容", highlight = true), InlineRun("后缀", italic = true)))
        val proposed = base.copy(blocks = listOf(polished, text))
        assertEquals(AgentEditValidation.Valid, validateAgentEdit(base, proposed, "v", "v", selection))
        assertEquals(AgentEditValidation.InvalidSelection, validateAgentEdit(base, proposed, "v", "v", selection.copy(start = 3)))
        for (invalid in listOf(
            proposed.copy(blocks = listOf(polished.copy(quoted = true), text)),
            proposed.copy(blocks = listOf(polished, text.copy(checked = true))),
            proposed.copy(blocks = listOf(polished.copy(inlines = polished.inlines.map { it.copy(bold = false) }), text)),
        )) assertEquals(AgentEditValidation.InvalidSelection, validateAgentEdit(base, invalid, "v", "v", selection))
    }

    @Test fun undoRetentionHasExactThirtyDayBoundary() {
        assertFalse(canUndoAcceptedAgentReview(null, 0))
        assertFalse(canUndoAcceptedAgentReview(100, 99))
        assertTrue(canUndoAcceptedAgentReview(100, 100 + AgentAcceptedUndoRetentionMs - 1))
        assertFalse(canUndoAcceptedAgentReview(100, 100 + AgentAcceptedUndoRetentionMs))
    }

    @Test fun tableSelectionProtectsOtherCellsAndStructure() {
        val table = TableBlock("table", rows = listOf(TableRow(listOf(TableCell(listOf(InlineRun("前原文后"))), TableCell(listOf(InlineRun("旁边")))))))
        val base = NoteDocument(blocks = listOf(table, text))
        val selection = AgentSelection("v", "table", 1, 3, 0, 0)
        val changed = table.copy(rows = listOf(table.rows.single().copy(cells = listOf(TableCell(listOf(InlineRun("前新内容后"))), table.rows.single().cells[1]))))
        val proposed = base.copy(blocks = listOf(changed, text))
        assertEquals(AgentEditValidation.Valid, validateAgentEdit(base, proposed, "v", "v", selection))
        for (invalid in listOf(
            changed.copy(rows = listOf(TableRow(listOf(changed.rows[0].cells[0])))),
            changed.copy(rows = listOf(TableRow(listOf(changed.rows[0].cells[0], TableCell(listOf(InlineRun("篡改"))))))),
        )) assertEquals(AgentEditValidation.InvalidSelection, validateAgentEdit(base, base.copy(blocks = listOf(invalid, text)), "v", "v", selection))
        assertEquals(AgentEditValidation.InvalidSelection, validateAgentEdit(base, proposed, "v", "v", selection.copy(tableRow = -1)))
        assertEquals(AgentEditValidation.InvalidSelection, validateAgentEdit(base, proposed, "v", "v", selection.copy(tableColumn = null)))
    }

    @Test fun restoringNeverOverwritesUserRestoration() {
        assertEquals(AgentRestoreDecision.KeepUserRestoredVersion, agentRestoreDecision(true, false, true))
        assertEquals(AgentRestoreDecision.RestoreOriginalNotebook, agentRestoreDecision(true, true, true))
        assertEquals(AgentRestoreDecision.RestoreUnfiled, agentRestoreDecision(true, true, false))
        assertEquals(AgentRestoreDecision.Unrecoverable, agentRestoreDecision(false, true, true))
    }
}
