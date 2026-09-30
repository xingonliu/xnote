package com.xnote.app.data

import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

// -- Tests

class AgentConversationTest {
    @Test fun newConversationIsEmptyBeforeItsSegmentIsPersistedAndOldHistoryCanBeSelected() {
        val segments = listOf(AgentSegmentEntity("one", 1), AgentSegmentEntity("auto", 2, conversationId = "one"), AgentSegmentEntity("two", 3))
        val messages = listOf(message("user", "one", "第一会话"), message("continued", "auto", "继续内容"), message("other", "two", "第二会话"))
        assertTrue(agentConversationMessages(messages, segments, "new").isEmpty())
        assertEquals(listOf("user", "continued"), agentConversationMessages(messages, segments, "one").map { it.id })
        assertEquals(listOf("other"), agentConversationMessages(messages, segments, "two").map { it.id })
        val history = agentConversations(segments + AgentSegmentEntity("empty", 4), messages)
        assertEquals(setOf("one", "two"), history.map { it.id }.toSet())
        assertEquals("第一会话", history.single { it.id == "one" }.title)
    }

    @Test fun returningToPageCannotReplayConsumedNewConversationToast() {
        val state = MutableStateFlow(AgentTimelineState(ready = true, notice = "新会话已开始"))
        assertTrue(consumeAgentNotice(state, "新会话已开始"))
        assertNull(state.value.notice)
        state.value = state.value.copy(running = true)
        state.value = state.value.copy(running = false)
        assertFalse(consumeAgentNotice(state, "新会话已开始"))
        state.value = state.value.copy(notice = "工具需要授权")
        assertFalse(consumeAgentNotice(state, "新会话已开始"))
        assertEquals("工具需要授权", state.value.notice)
    }

    @Test fun concurrentPageCollectorsDeliverTheNoticeOnce() {
        val state = MutableStateFlow(AgentTimelineState(ready = true, notice = "新会话已开始"))
        val start = CountDownLatch(1)
        val deliveries = AtomicInteger()
        val threads = List(8) { Thread { start.await(); if (consumeAgentNotice(state, "新会话已开始")) deliveries.incrementAndGet() } }
        threads.forEach { it.start() }; start.countDown(); threads.forEach { it.join() }
        assertEquals(1, deliveries.get())
    }

    // -- Functions

    private fun message(id: String, segment: String, text: String) = AgentMessageEntity(id = id, segmentId = segment, runId = "run",
        role = AgentMessageRole.User, text = text, status = AgentMessageStatus.Complete, createdAtEpochMs = segment.length.toLong())
}
