package com.xnote.app.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Test

// -- Tests

class AgentReplyTextTest {
    @Test fun convertsProseFormattingAndKeepsLinkDestinations() {
        assertEquals("总结\n重点和提醒\n• 第一项\n引用\n文档 (https://example.com)",
            agentReplyPlainText("# 总结\n**重点**和_提醒_\n- 第一项\n> 引用\n[文档](https://example.com)"))
    }

    @Test fun preservesLiteralCodeAndOrdinarySymbols() {
        assertEquals("a_b * 2 # 注释\n**literal**\n变量 a_b，2 * 3 = 6，C#", agentReplyPlainText(
            "```kotlin\na_b * 2 # 注释\n**literal**\n```\n变量 a_b，2 * 3 = 6，C#"))
        assertEquals("**literal**", agentReplyPlainText("`**literal**`"))
    }

    @Test fun acceptsUnfinishedStreamsWithoutLosingCode() {
        assertEquals("val count = 2", agentReplyPlainText("```kotlin\nval count = 2"))
        assertEquals("正在生成", agentReplyPlainText("## 正在生成"))
        assertEquals("", agentReplyPlainText(""))
    }

    @Test fun keepsParagraphsAndRemovesFormattingOnlyLines() {
        assertEquals("已完成\n\n• 检查\n结束", agentReplyPlainText("已完成\n\n- [x] 检查\n---\n结束"))
    }
}
