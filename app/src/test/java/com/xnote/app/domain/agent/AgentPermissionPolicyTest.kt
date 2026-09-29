package com.xnote.app.domain.agent

import org.junit.Assert.*
import org.junit.Test

// -- Type Definitions

class AgentPermissionPolicyTest {
    // -- Functions

    @Test fun defaultRequiresApprovalAndAttachmentsNeverGrantAccess() {
        val context = AgentAccessContext(AgentPermission(), "run", "segment", setOf("note"))
        assertEquals(AgentPermissionMode.RequestApproval, context.permission.mode)
        assertFalse(context.canReadCurrent(AgentNoteAccess("note", null)))
        assertFalse(context.canEdit(AgentNoteAccess("note", null)))
    }

    @Test fun everyModeEnforcesItsExecutionBoundary() {
        for (mode in AgentPermissionMode.entries) for (approved in listOf(false, true)) {
            val context = AgentAccessContext(AgentPermission(mode), "run", "segment", executingApprovedCall = approved)
            val allowed = mode == AgentPermissionMode.FullAccess || (mode == AgentPermissionMode.RequestApproval && approved)
            assertEquals(allowed, context.canReadCurrent(AgentNoteAccess("note", "book")))
            assertEquals(allowed, context.canEdit(AgentNoteAccess("note", null)))
            assertFalse(context.canReadCurrent(AgentNoteAccess("note", null, deleted = true)))
            assertEquals(allowed, context.createDecision(emptySet()) is AgentCreateDecision.Allowed)
        }
    }

    @Test fun explicitCreationTargetMustExistAndOmittedTargetIsUnfiled() {
        val context = AgentAccessContext(AgentPermission(AgentPermissionMode.FullAccess), "run", "segment")
        assertEquals(AgentCreateDecision.Allowed(AgentCreateTarget(null)), context.createDecision(setOf("book")))
        assertEquals(AgentCreateDecision.Allowed(AgentCreateTarget("book")), context.createDecision(setOf("book"), AgentCreateTarget("book")))
        assertEquals(AgentCreateDecision.RequiresAuthorization, context.createDecision(emptySet(), AgentCreateTarget("missing")))
    }
}
