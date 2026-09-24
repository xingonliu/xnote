package com.xnote.app.domain.agent

import kotlinx.serialization.Serializable

// -- Type Definitions

@Serializable
enum class AgentPermissionLevel { None, Read, Edit }

@Serializable
enum class AgentScope { Attached, Unfiled, Notebooks, All }

@Serializable
data class AgentPermission(
    val level: AgentPermissionLevel = AgentPermissionLevel.None,
    val scope: AgentScope = AgentScope.Attached,
    val notebookIds: Set<String> = emptySet(),
    val revision: Long = 0,
)

@Serializable
data class AgentRunGrant(
    val runId: String,
    val permissionRevision: Long,
    val level: AgentPermissionLevel,
    val noteIds: Set<String> = emptySet(),
    val createTargets: Set<AgentCreateTarget> = emptySet(),
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
    val grant: AgentRunGrant? = null,
)

data class AgentSnapshotAccess(
    val noteId: String,
    val segmentId: String,
    val permissionRevision: Long,
    val segmentOpen: Boolean,
)

sealed interface AgentCreateDecision {
    data class Allowed(val target: AgentCreateTarget) : AgentCreateDecision
    data class Choose(val targets: Set<AgentCreateTarget>) : AgentCreateDecision
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
enum class AgentToolStatus { Requested, Denied, Executing, Committed, Failed, Unknown }

@Serializable
enum class AgentChangeOrigin { User, Agent }

@Serializable
enum class AgentChangeKind { Create, Edit, Trash }

@Serializable
enum class AgentReviewStatus { Pending, Accepted, Rejected, Undone, Conflict, Unrecoverable }

@Serializable
data class AgentMessageSource(val noteId: String, val snapshotId: String? = null)

@Serializable
data class AgentSelection(val version: String, val blockId: String, val start: Int, val end: Int)

// -- Constants

const val AgentAcceptedUndoRetentionMs = 30L * 24 * 60 * 60 * 1000
