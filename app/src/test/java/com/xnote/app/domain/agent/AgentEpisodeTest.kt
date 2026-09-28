package com.xnote.app.domain.agent

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentEpisodeTest {
    @Test fun idleBoundaryUsesCompletionAndNeverSplitsUnresolvedRuns() {
        assertNull(agentSegmentCloseReason(100, 100 + AgentMemoryLimits.IdleBoundaryMs - 1, 1, 200, false))
        assertEquals("idle", agentSegmentCloseReason(100, 100 + AgentMemoryLimits.IdleBoundaryMs, 1, 200, false))
        assertNull(agentSegmentCloseReason(100, Long.MAX_VALUE, 300, 200, true))
        assertEquals("capacity", agentSegmentCloseReason(null, 200, 300, 200, false))
    }

    @Test fun summaryRejectsWrongSourceUnknownFieldsAndOversizedLists() {
        val summary = AgentEpisodeSummary("旅行", "选择高铁", listOf("旅行"), listOf("高铁"), emptyList(), emptyList(), listOf("高铁"), "a", "b")
        assertEquals(summary, parseAgentEpisode(Json.encodeToString(summary), "a", "b"))
        assertThrows(IllegalArgumentException::class.java) { parseAgentEpisode(Json.encodeToString(summary), "a", "c") }
        assertThrows(IllegalArgumentException::class.java) { parseAgentEpisode(Json.encodeToString(summary.copy(keywords = List(13) { "x" })), "a", "b") }
        assertThrows(IllegalArgumentException::class.java) { parseAgentEpisode(Json.encodeToString(summary).dropLast(1) + ",\"execute\":true}", "a", "b") }
    }
}
