package com.xnote.app.domain.agent

import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentPermissionPolicyTest {
    private val bookNote = AgentNoteAccess("note", "book")
    private val unfiled = AgentNoteAccess("loose", null)
    private val context = AgentAccessContext(runId = "run", segmentId = "segment")

    @Test fun defaultsNeverReadOrWriteLibrary() {
        assertEquals(AgentPermissionLevel.None, context.permission.level)
        assertEquals(AgentScope.Attached, context.permission.scope)
        assertFalse(context.canReadCurrent(bookNote))
        assertFalse(context.canEdit(bookNote))
        assertEquals(AgentCreateDecision.RequiresAuthorization, context.createDecision(setOf("book")))
    }

    @Test fun permissionAndScopeMatrix() {
        for (level in AgentPermissionLevel.entries) for (scope in AgentScope.entries) {
            val candidate = context.copy(permission = AgentPermission(level, scope, setOf("book")))
            val bookInScope = scope == AgentScope.All || scope == AgentScope.Notebooks
            val looseInScope = scope == AgentScope.All || scope == AgentScope.Unfiled
            assertEquals(level >= AgentPermissionLevel.Read && bookInScope, candidate.canReadCurrent(bookNote))
            assertEquals(level == AgentPermissionLevel.Edit && bookInScope, candidate.canEdit(bookNote))
            assertEquals(level >= AgentPermissionLevel.Read && looseInScope, candidate.canReadCurrent(unfiled))
            assertEquals(level == AgentPermissionLevel.Edit && looseInScope, candidate.canEdit(unfiled))
            assertFalse(candidate.canReadCurrent(bookNote.copy(deleted = true)))
            assertFalse(candidate.canEdit(bookNote.copy(deleted = true)))
        }
    }

    @Test fun attachmentAtLevelOneReadsOnlyLiveSegmentSnapshot() {
        val attached = context.copy(attachedNoteIds = setOf("note"))
        val snapshot = AgentSnapshotAccess("note", "segment", 0, true)
        assertTrue(attached.canReadSnapshot(snapshot, bookNote))
        assertFalse(attached.canReadCurrent(bookNote))
        assertFalse(attached.canEdit(bookNote))
        assertFalse(attached.canReadSnapshot(snapshot.copy(segmentOpen = false), bookNote))
        assertFalse(attached.canReadSnapshot(snapshot.copy(segmentId = "old"), bookNote))
        assertFalse(attached.canReadSnapshot(snapshot, bookNote.copy(deleted = true)))
        assertFalse(attached.canReadSnapshot(snapshot, null))
        assertFalse(attached.canReadSnapshot(snapshot, unfiled))
        assertFalse(attached.copy(permission = AgentPermission(revision = 1)).canReadSnapshot(snapshot, bookNote))
    }

    @Test fun multiNotebookScopeAndAttachedEditsAreExplicit() {
        val editable = context.copy(permission = AgentPermission(AgentPermissionLevel.Edit, AgentScope.Notebooks, setOf("book", "other")))
        assertTrue(editable.canEdit(bookNote))
        assertTrue(editable.canEdit(bookNote.copy(notebookId = "other")))
        assertFalse(editable.canEdit(bookNote.copy(notebookId = "outside")))
        val attached = editable.copy(permission = editable.permission.copy(scope = AgentScope.Attached), attachedNoteIds = setOf("note"))
        assertTrue(attached.canEdit(bookNote))
        assertFalse(attached.canEdit(unfiled))
    }

    @Test fun grantIsBoundToRunAndCurrentPermissionRevision() {
        val granted = context.copy(grant = AgentRunGrant("run", 0, AgentPermissionLevel.Edit, setOf("note")))
        assertTrue(granted.canReadCurrent(bookNote))
        assertTrue(granted.canEdit(bookNote))
        assertFalse(granted.copy(runId = "next").canEdit(bookNote))
        assertFalse(granted.copy(permission = AgentPermission(revision = 1)).canReadCurrent(bookNote))
        assertFalse(granted.copy(permission = AgentPermission(revision = 1)).canEdit(bookNote))
    }

    @Test fun creationSelectsOnlyExistingAuthorizedTargets() {
        val editable = context.copy(permission = AgentPermission(AgentPermissionLevel.Edit, AgentScope.Notebooks, setOf("book", "other")))
        assertEquals(AgentCreateDecision.Allowed(AgentCreateTarget("book")), editable.createDecision(setOf("book")))
        assertTrue(editable.createDecision(setOf("book", "other")) is AgentCreateDecision.Choose)
        assertEquals(AgentCreateDecision.Allowed(AgentCreateTarget("other")), editable.createDecision(setOf("book", "other"), AgentCreateTarget("other")))
        assertEquals(AgentCreateDecision.RequiresAuthorization, editable.createDecision(setOf("book"), AgentCreateTarget("other")))
        assertEquals(AgentCreateDecision.RequiresAuthorization, editable.createDecision(setOf("book"), AgentCreateTarget(null)))
    }

    @Test fun perRunCreationDoesNotAuthorizeWholeNotebook() {
        val granted = context.copy(grant = AgentRunGrant("run", 0, AgentPermissionLevel.Edit, createTargets = setOf(AgentCreateTarget("book"))))
        val afterCreation = granted.copy(grant = granted.grantCreatedNote("new", AgentCreateTarget("book"), setOf("book")))
        assertTrue(afterCreation.canEdit(bookNote.copy(noteId = "new")))
        assertFalse(afterCreation.canReadCurrent(bookNote))
        assertFalse(afterCreation.copy(runId = "next").canEdit(bookNote.copy(noteId = "new")))
        assertEquals(AgentScope.Attached, afterCreation.permission.scope)
    }
}
