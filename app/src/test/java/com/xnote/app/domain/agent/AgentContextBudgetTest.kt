package com.xnote.app.domain.agent

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

// -- Tests

class AgentContextBudgetTest {
    private val profile = ModelProfile("profile", name = "模型", protocol = ModelProtocol.OpenAI, modelId = "model",
        contextTokens = estimatedAgentTokens(AgentSystemPrompt) + 4096, outputTokens = 512)

    @Test fun runningCompressionKeepsGoalAndAtomicToolReceiptsWithoutDanglingCalls() {
        val call = ModelToolCall("read-1", "read", buildJsonObject { put("note_id", "note") })
        val result = ModelToolResult(call.id, call.name, buildJsonObject { put("note_id", "note"); put("version", "current"); put("body", "正文".repeat(3000)) }.toString())
        val current = listOf(ModelMessage(AgentMessageRole.User, "完整当前目标"), ModelMessage(AgentMessageRole.Assistant, calls = listOf(call)),
            ModelMessage(AgentMessageRole.Tool, results = listOf(result)), ModelMessage(AgentMessageRole.User, "完整补充"))
        val plan = planAgentExecutionContext(profile, emptyList(), current)
        assertTrue(plan.compressed)
        assertEquals(current.first(), plan.messages.first()); assertEquals(current.last(), plan.messages.last())
        assertTrue(plan.messages.none { it.calls.isNotEmpty() || it.results.isNotEmpty() })
        assertTrue(plan.messages[1].text.contains("read-1") && plan.messages[1].text.contains("current"))
        assertTrue(plan.estimatedInputTokens + profile.outputTokens + ModelLimits.ToolReserveTokens <= profile.contextTokens)
        val pending = current.dropLast(2) + ModelMessage(AgentMessageRole.Assistant, "尚未完成".repeat(2000))
        assertThrows(AgentBudgetException::class.java) { planAgentExecutionContext(profile, emptyList(), pending) }
    }

    @Test fun inputAndSystemKeepReservedOutputAndToolBudget() {
        val input = "当前目标"
        val plan = planAgentContext(profile, listOf(AgentContextTurn("问", "答")), input)
        assertEquals(input, plan.messages.last().text)
        assertFalse(plan.compressed)
        assertTrue(plan.estimatedInputTokens + profile.outputTokens + ModelLimits.ToolReserveTokens <= profile.contextTokens)
    }

    @Test fun compressesOnlyCompleteExchangesAndKeepsCurrentGoal() {
        val plan = planAgentContext(profile, List(20) { AgentContextTurn("历史目标$it".repeat(100), "历史回答$it".repeat(100)) }, "当前目标必须完整保留")
        assertTrue(plan.compressed)
        assertEquals("当前目标必须完整保留", plan.messages.last().text)
        assertTrue(plan.estimatedInputTokens + profile.outputTokens + ModelLimits.ToolReserveTokens <= profile.contextTokens)
        assertEquals(1, plan.messages.size % 2)
        assertTrue(plan.messages.dropLast(1).any { it.text.contains("19") })
    }

    @Test fun oversizeMinimumRequestIsNotSilentlyTruncated() {
        assertThrows(AgentBudgetException::class.java) { planAgentContext(profile, emptyList(), "大".repeat(5000)) }
    }

    @Test fun executionKeepsPartialReplyAndSupplementWithoutCompression() {
        val execution = listOf(ModelMessage(AgentMessageRole.User, "原始目标"), ModelMessage(AgentMessageRole.Assistant, "中断前的输出"), ModelMessage(AgentMessageRole.User, "新的补充"))
        val plan = planAgentExecutionContext(profile, List(20) { AgentContextTurn("旧问".repeat(100), "旧答".repeat(100)) }, execution)
        assertEquals(execution, plan.messages.takeLast(3))
        assertTrue(plan.compressed)
        assertTrue(plan.estimatedInputTokens + profile.outputTokens + ModelLimits.ToolReserveTokens <= profile.contextTokens)
        assertThrows(AgentBudgetException::class.java) {
            planAgentExecutionContext(profile, emptyList(), execution + ModelMessage(AgentMessageRole.Assistant, "长".repeat(5000)))
        }
    }

    @Test fun toolArgumentsResultsAndNativePartsConsumeTheCurrentExecutionBudget() {
        val current = listOf(ModelMessage(AgentMessageRole.User, "读取"),
            ModelMessage(AgentMessageRole.Tool, results = listOf(ModelToolResult("call", "read", "私".repeat(5000)))))
        assertThrows(AgentBudgetException::class.java) { planAgentExecutionContext(profile, emptyList(), current) }
        val native = listOf(ModelMessage(AgentMessageRole.User, "读取"),
            ModelMessage(AgentMessageRole.Assistant, nativeParts = buildJsonArray { add(buildJsonObject { put("thoughtSignature", "x".repeat(5000)) }) }))
        assertThrows(AgentBudgetException::class.java) { planAgentExecutionContext(profile, emptyList(), native) }
    }

    @Test fun chineseAndEmojiBudgetUsesUtf8InsteadOfEnglishCharacterRatio() {
        assertTrue(estimatedAgentTokens("你好") > estimatedAgentTokens("hi"))
        assertEquals(4, estimatedAgentTokens("😀") - estimatedAgentTokens(""))
    }
}
