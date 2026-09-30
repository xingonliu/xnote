package com.xnote.app.data.agent

import com.xnote.app.data.db.AgentMessageEntity
import com.xnote.app.data.db.AgentSegmentEntity
import com.xnote.app.domain.agent.AgentMessageRole

// -- Type Definitions

data class AgentConversation(val id: String, val title: String, val updatedAtEpochMs: Long)

// -- Functions

fun agentConversationMessages(messages: List<AgentMessageEntity>, segments: List<AgentSegmentEntity>, conversationId: String?): List<AgentMessageEntity> {
    val selectedSegments = segments.filter { it.conversationId == conversationId }.map { it.id }.toSet()
    return messages.filter { it.segmentId in selectedSegments }
}

fun agentConversations(segments: List<AgentSegmentEntity>, messages: List<AgentMessageEntity>): List<AgentConversation> =
    segments.groupBy { it.conversationId }.mapNotNull { (id, parts) ->
        val history = messages.filter { message -> parts.any { it.id == message.segmentId } && message.role != AgentMessageRole.Event }
        if (history.isEmpty()) null else AgentConversation(id,
            history.firstOrNull { it.role == AgentMessageRole.User && it.text.isNotBlank() }?.text?.lineSequence()?.first()?.take(48) ?: "附件会话",
            history.maxOf { it.createdAtEpochMs })
    }.sortedByDescending { it.updatedAtEpochMs }
