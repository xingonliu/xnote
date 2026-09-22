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

    @Test fun undoRetentionHasExactThirtyDayBoundary() {
        assertFalse(canUndoAcceptedAgentReview(null, 0))
        assertFalse(canUndoAcceptedAgentReview(100, 99))
        assertTrue(canUndoAcceptedAgentReview(100, 100 + AgentAcceptedUndoRetentionMs - 1))
        assertFalse(canUndoAcceptedAgentReview(100, 100 + AgentAcceptedUndoRetentionMs))
    }

    @Test fun restoringNeverOverwritesUserRestoration() {
        assertEquals(AgentRestoreDecision.KeepUserRestoredVersion, agentRestoreDecision(true, false, true))
        assertEquals(AgentRestoreDecision.RestoreOriginalNotebook, agentRestoreDecision(true, true, true))
        assertEquals(AgentRestoreDecision.RestoreUnfiled, agentRestoreDecision(true, true, false))
        assertEquals(AgentRestoreDecision.Unrecoverable, agentRestoreDecision(false, true, true))
    }
}
