package com.xnote.app.domain.agent

// -- Functions

private fun AgentAccessContext.validGrant(): AgentRunGrant? = grant?.takeIf {
    it.runId == runId && it.permissionRevision == permission.revision
}

private fun AgentAccessContext.inScope(note: AgentNoteAccess): Boolean = when (permission.scope) {
    AgentScope.Attached -> note.noteId in attachedNoteIds
    AgentScope.Unfiled -> note.notebookId == null
    AgentScope.Notebooks -> note.notebookId != null && note.notebookId in permission.notebookIds
    AgentScope.All -> true
}

fun AgentAccessContext.canReadCurrent(note: AgentNoteAccess): Boolean = !note.deleted && (
    (permission.level >= AgentPermissionLevel.Read && inScope(note)) ||
        (validGrant()?.let { (it.level >= AgentPermissionLevel.Read && note.noteId in it.noteIds) || note.noteId in it.createdNoteIds } == true)
    )

fun AgentAccessContext.canEdit(note: AgentNoteAccess): Boolean = !note.deleted && (
    (permission.level == AgentPermissionLevel.Edit && inScope(note)) ||
        (validGrant()?.let { (it.level == AgentPermissionLevel.Edit && note.noteId in it.noteIds) || note.noteId in it.createdNoteIds } == true)
    )

fun AgentAccessContext.canReadSnapshot(snapshot: AgentSnapshotAccess, current: AgentNoteAccess?): Boolean {
    if (current == null || current.deleted || current.noteId != snapshot.noteId) return false
    return canReadCurrent(current) || (
        snapshot.segmentOpen && snapshot.segmentId == segmentId &&
            snapshot.permissionRevision == permission.revision && snapshot.noteId in attachedNoteIds
        )
}

fun AgentAccessContext.createDecision(
    existingNotebookIds: Set<String>,
    requested: AgentCreateTarget? = null,
): AgentCreateDecision {
    val targets = linkedSetOf<AgentCreateTarget>()
    if (permission.level == AgentPermissionLevel.Edit) {
        when (permission.scope) {
            AgentScope.Attached -> Unit
            AgentScope.Unfiled -> targets += AgentCreateTarget(null)
            AgentScope.Notebooks -> targets += permission.notebookIds.intersect(existingNotebookIds).map(::AgentCreateTarget)
            AgentScope.All -> {
                targets += AgentCreateTarget(null)
                targets += existingNotebookIds.map(::AgentCreateTarget)
            }
        }
    }
    return when {
        requested != null -> if (requested in targets) AgentCreateDecision.Allowed(requested)
            else AgentCreateDecision.RequiresAuthorization
        targets.size == 1 -> AgentCreateDecision.Allowed(targets.single())
        targets.isEmpty() -> AgentCreateDecision.RequiresAuthorization
        else -> AgentCreateDecision.Choose(targets)
    }
}

/** Use inside the authorized creation transaction; it grants no other note or creation target. */
fun AgentAccessContext.grantCreatedNote(noteId: String): AgentRunGrant {
    val previous = validGrant()
    return (previous ?: AgentRunGrant(runId, permission.revision, AgentPermissionLevel.None))
        .copy(createdNoteIds = previous?.createdNoteIds.orEmpty() + noteId)
}
