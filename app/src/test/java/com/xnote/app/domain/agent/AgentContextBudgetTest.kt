package com.xnote.app.domain.agent

import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentContextBudgetTest {
    private val profile = ModelProfile("profile", name = "模型", protocol = ModelProtocol.OpenAI, modelId = "model", contextTokens = 4096, outputTokens = 512)

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

    @Test fun chineseAndEmojiBudgetUsesUtf8InsteadOfEnglishCharacterRatio() {
        assertTrue(estimatedAgentTokens("你好") > estimatedAgentTokens("hi"))
        assertEquals(4, estimatedAgentTokens("😀") - estimatedAgentTokens(""))
    }
}
