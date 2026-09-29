package com.xnote.app.domain.agent

import kotlinx.serialization.Serializable

// -- Type Definitions

@Serializable
enum class AgentPermissionMode { Private, RequestApproval, FullAccess }

@Serializable
data class AgentPermission(
    val mode: AgentPermissionMode = AgentPermissionMode.RequestApproval,
    val revision: Long = 0,
)

@Serializable
data class AgentCreateTarget(val notebookId: String?)

data class AgentNoteAccess(
    val noteId: String,
    val notebookId: String?,
    val deleted: Boolean = false,
)

data class AgentAccessContext(
    val permission: AgentPermission = AgentPermission(),
    val runId: String,
    val segmentId: String,
    val attachedNoteIds: Set<String> = emptySet(),
    val executingApprovedCall: Boolean = false,
)

sealed interface AgentCreateDecision {
    data class Allowed(val target: AgentCreateTarget) : AgentCreateDecision
    data object RequiresAuthorization : AgentCreateDecision
}

@Serializable
enum class AgentMessageRole { User, Assistant, Tool, Event }

@Serializable
enum class AgentMessageStatus { Pending, Streaming, Complete, Failed, Cancelled, Interrupted }

@Serializable
enum class AgentRunStatus { Pending, Running, WaitingPermission, WaitingConflict, PausedBudget, Complete, Failed, Cancelled, Interrupted }

@Serializable
enum class AgentQueueStatus { Waiting, Paused, Dispatched }

@Serializable
enum class AgentToolStatus { Requested, Approved, Denied, Executing, Committed, Failed, Unknown }

@Serializable
enum class AgentChangeOrigin { User, Agent }

@Serializable
enum class AgentChangeKind { Create, Edit, Trash }

@Serializable
enum class AgentReviewStatus { Pending, Accepted, Rejected, Undone, Conflict, Unrecoverable }

@Serializable
data class AgentMessageSource(val noteId: String, val snapshotId: String? = null, val selection: AgentSelection? = null)

@Serializable
data class AgentSelection(val version: String, val blockId: String, val start: Int, val end: Int,
    val tableRow: Int? = null, val tableColumn: Int? = null,
    val endBlockId: String? = null, val endTableRow: Int? = null, val endTableColumn: Int? = null)

@Serializable
data class AgentDraftSelection(val noteId: String, val selection: AgentSelection)

// -- Constants

const val AgentAcceptedUndoRetentionMs = 30L * 24 * 60 * 60 * 1000
