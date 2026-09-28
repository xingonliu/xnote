package com.xnote.app.domain.agent

import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentDisplayDiffTest {
    @Test fun inlineDiffHighlightsWholeWordsAndPreservesSurroundingText() {
        val parts = agentDisplayDiff("A walking example.", "A talking example.")
        assertEquals("walking", parts.single { it.kind == AgentDiffKind.Removed }.text)
        assertEquals("talking", parts.single { it.kind == AgentDiffKind.Added }.text)
        assertEquals("A walking example.", parts.filter { it.kind != AgentDiffKind.Added }.joinToString("") { it.text })
        assertEquals("A talking example.", parts.filter { it.kind != AgentDiffKind.Removed }.joinToString("") { it.text })
    }

    @Test fun monospaceDiffKeepsCompleteLinesIncludingBlankLines() {
        val before = "val x = 1\n\nreturn x"
        val after = "val x = 2\n\nreturn x"
        val parts = agentDisplayDiff(before, after, byLine = true)
        assertEquals("val x = 1\n", parts.single { it.kind == AgentDiffKind.Removed }.text)
        assertEquals("val x = 2\n", parts.single { it.kind == AgentDiffKind.Added }.text)
        assertEquals("\nreturn x", parts.last().text)
    }

    @Test fun diffCanReconstructUnicodeAndEmptyInputs() {
        for ((before, after) in listOf("" to "😀新内容", "😀旧文" to "新的🌍文字", "全部删除" to "", "\n" to "\n\n")) {
            for (byLine in listOf(false, true)) {
                val parts = agentDisplayDiff(before, after, byLine)
                assertEquals(before, parts.filter { it.kind != AgentDiffKind.Added }.joinToString("") { it.text })
                assertEquals(after, parts.filter { it.kind != AgentDiffKind.Removed }.joinToString("") { it.text })
            }
        }
    }
}
