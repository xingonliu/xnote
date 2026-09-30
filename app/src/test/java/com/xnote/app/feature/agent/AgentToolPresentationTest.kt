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
}
