package com.xnote.app.domain.agent

import kotlinx.serialization.Serializable

// -- Type Definitions

@Serializable
data class AgentToolDecision(
    val atEpochMs: Long,
    val permission: AgentPermission,
    val grant: AgentRunGrant?,
    val attachedNoteIds: Set<String>,
    val reason: String,
)
