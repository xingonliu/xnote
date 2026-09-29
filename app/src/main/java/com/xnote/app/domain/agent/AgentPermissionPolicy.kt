package com.xnote.app.domain.agent

// -- Functions

fun AgentAccessContext.canAccessData(): Boolean = permission.mode == AgentPermissionMode.FullAccess ||
    (permission.mode == AgentPermissionMode.RequestApproval && executingApprovedCall)

fun AgentAccessContext.canReadCurrent(note: AgentNoteAccess): Boolean = !note.deleted && canAccessData()

fun AgentAccessContext.canEdit(note: AgentNoteAccess): Boolean = !note.deleted && canAccessData()

fun AgentAccessContext.createDecision(existingNotebookIds: Set<String>, requested: AgentCreateTarget? = null): AgentCreateDecision {
    if (!canAccessData()) return AgentCreateDecision.RequiresAuthorization
    val target = requested ?: AgentCreateTarget(null)
    return if (target.notebookId == null || target.notebookId in existingNotebookIds) AgentCreateDecision.Allowed(target)
        else AgentCreateDecision.RequiresAuthorization
}
