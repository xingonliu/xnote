package com.xnote.app.feature.agent

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

// -- Type Definitions

class AgentToolPresentationTest {
    // -- Functions

    @Test fun creationSummaryIdentifiesItsActualDestination() {
        val arguments = buildJsonObject {
            put("title", "旅行清单")
            putJsonObject("target") { put("notebookId", "travel") }
        }
        val summary = agentToolDescription("create", arguments, emptyMap(), mapOf("travel" to "旅行"))
        assertTrue(summary.contains("在“旅行”中新建“旅行清单”"))
        assertTrue(agentToolDescription("create", buildJsonObject { put("title", "") }, emptyMap(), emptyMap())
            .contains("在“未归档”中新建“未命名笔记”"))
    }

    @Test fun writeSummaryKeepsSelectionAndTitleChangesVisible() {
        val arguments = buildJsonObject {
            put("note_id", "note")
            put("title", "新标题")
            putJsonObject("selection") { put("start", 0); put("end", 3) }
        }
        val summary = agentToolDescription("write", arguments, mapOf("note" to "原标题"), emptyMap())
        assertTrue(summary.contains("“原标题”中的选中内容"))
        assertTrue(summary.contains("标题设为“新标题”"))
    }

    @Test fun unknownToolsRetainTheirIdentityAndRequireTheFullRequest() {
        assertEquals("external_command", agentToolTitle("external_command"))
        assertTrue(agentToolDescription("external_command", JsonObject(emptyMap()), emptyMap(), emptyMap()).contains("完整请求"))
    }

    @Test fun compactTargetsDistinguishDirectoryNotebookAndNoteReads() {
        assertEquals("笔记目录", agentToolTarget("read", JsonObject(emptyMap()), emptyMap(), emptyMap()))
        assertEquals("未归档", agentToolTarget("read", buildJsonObject { put("notebook_id", "") }, emptyMap(), emptyMap()))
        assertEquals("旅行", agentToolTarget("read", buildJsonObject { put("notebook_id", "book") }, emptyMap(), mapOf("book" to "旅行")))
        assertEquals("清单", agentToolTarget("read", buildJsonObject { put("note_id", "note") }, mapOf("note" to "清单"), emptyMap()))
        assertEquals("笔记不可用", agentToolTarget("write", buildJsonObject { put("note_id", "removed") }, emptyMap(), emptyMap()))
    }

    @Test fun compactTargetsShowQueriesAndGeneratedFilenames() {
        assertEquals("会议", agentToolTarget("note_search", buildJsonObject { put("query", "会议") }, emptyMap(), emptyMap()))
        assertEquals("总结.pdf", agentToolTarget("output_file", buildJsonObject { put("filename", "总结.pdf") }, emptyMap(), emptyMap()))
        assertEquals("未命名笔记", agentToolTarget("create", buildJsonObject { put("title", "") }, emptyMap(), emptyMap()))
    }
}
