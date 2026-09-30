package com.xnote.app.domain.agent

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentFinishToolTest {
    @Test fun endingIsPlainTextAndEachNonemptyLineBecomesABubble() {
        val summary = agentFinishSummary(buildJsonObject { put("summary", "**整理好了**\r\n\n可以看一下。") })
        assertEquals(listOf("整理好了", "可以看一下。"), agentFinalBubbleTexts(summary))
        assertEquals(listOf("一句话"), agentFinalBubbleTexts("一句话"))
    }

    @Test fun closingCannotSkipNewInputOrRunBesideAnotherTool() {
        val call = ModelToolCall("finish", AgentFinishToolName, buildJsonObject { put("summary", "好了。") })
        assertNull(agentFinishError(call, 1, false))
        assertEquals("pending_input", agentFinishError(call, 1, true))
        assertEquals("finish_must_be_separate", agentFinishError(call, 2, false))
    }

    @Test fun blankOversizeAndNonStringSummariesCannotCompleteATask() {
        listOf(buildJsonObject { put("summary", " ") }, buildJsonObject { put("summary", "字".repeat(241)) },
            buildJsonObject { put("summary", 1) }, buildJsonObject { put("summary", "好了"); put("other", true) },
            buildJsonObject { put("summary", "---") }).forEach { arguments ->
            assertEquals("invalid_finish_summary", agentFinishError(ModelToolCall("finish", AgentFinishToolName, arguments), 1, false))
        }
        assertEquals(240, agentFinishSummary(buildJsonObject { put("summary", "字".repeat(240)) }).length)
    }
}
