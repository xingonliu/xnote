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
        (validGrant()?.let { it.level >= AgentPermissionLevel.Read && note.noteId in it.noteIds } == true)
    )

fun AgentAccessContext.canEdit(note: AgentNoteAccess): Boolean = !note.deleted && (
    (permission.level == AgentPermissionLevel.Edit && inScope(note)) ||
        (validGrant()?.let { it.level == AgentPermissionLevel.Edit && note.noteId in it.noteIds } == true)
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
    validGrant()?.takeIf { it.level == AgentPermissionLevel.Edit }?.createTargets
        ?.filter { it.notebookId == null || it.notebookId in existingNotebookIds }?.let(targets::addAll)
    return when {
        requested != null -> if (requested in targets) AgentCreateDecision.Allowed(requested)
            else AgentCreateDecision.RequiresAuthorization
        targets.size == 1 -> AgentCreateDecision.Allowed(targets.single())
        targets.isEmpty() -> AgentCreateDecision.RequiresAuthorization
        else -> AgentCreateDecision.Choose(targets)
    }
}

/** Call only after the authorized creation transaction has committed. */
fun AgentAccessContext.grantCreatedNote(noteId: String, target: AgentCreateTarget, existingNotebookIds: Set<String>): AgentRunGrant {
    require(createDecision(existingNotebookIds, target) is AgentCreateDecision.Allowed)
    val previous = validGrant()
    return AgentRunGrant(runId, permission.revision, AgentPermissionLevel.Edit,
        previous?.takeIf { it.level == AgentPermissionLevel.Edit }?.noteIds.orEmpty() + noteId,
        previous?.createTargets.orEmpty())
}
