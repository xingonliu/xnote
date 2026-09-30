package com.xnote.app.feature.agent

import com.xnote.app.domain.agent.AgentToolStatus
import com.xnote.app.domain.document.*
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

    @Test fun compactSummaryKeepsStatusAndNormalizesMultilineTargets() {
        assertEquals("修改笔记 · 清单 第一项", agentToolSummary("write", "清单\n第一项", AgentToolStatus.Committed))
        assertEquals("读取笔记 · 正在执行 · 清单", agentToolSummary("read", "清单", AgentToolStatus.Executing))
        assertTrue(agentToolSummary("delete", "清单", AgentToolStatus.Denied).contains("未获允许"))
    }

    @Test fun payloadSeparatesReadableDocumentAndMetadataWithoutLosingRawFields() {
        val document = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun("正文内容")))))
        val raw = buildJsonObject {
            put("note_id", "note"); put("title", "新标题"); put("base_version", "internal-version")
            put("document_json", document.encodeToJson())
        }.toString()
        val payload = agentToolPayload(raw, mapOf("note" to "原标题"), emptyMap())
        assertEquals("正文内容", payload.content)
        assertEquals(listOf(AgentToolDetailField("笔记", "原标题"), AgentToolDetailField("标题", "新标题")), payload.fields)
        assertEquals(Json.parseToJsonElement(raw), Json.parseToJsonElement(payload.rawJson))
        assertFalse(payload.fields.any { it.value.contains("internal-version") })
    }

    @Test fun searchResultShowsCountAndOrderedReadableItems() {
        val raw = buildJsonObject {
            putJsonArray("notes") {
                add(buildJsonObject { put("title", "清单"); put("snippet", "带上雨伞"); put("version", "hidden") })
                add(buildJsonObject { put("title", ""); put("snippet", "第二条") })
            }
            put("has_more", false)
        }.toString()
        val payload = agentToolPayload(raw, emptyMap(), emptyMap())
        assertEquals(listOf(AgentToolDetailField("返回数量", "2 项"), AgentToolDetailField("还有更多结果", "否")), payload.fields)
        assertEquals(listOf(AgentToolDetailField("清单", "带上雨伞"), AgentToolDetailField("未命名笔记", "第二条")), payload.items)
    }

    @Test fun partialDocumentsAndInvalidPayloadsRemainAvailableInRawData() {
        val partial = buildJsonObject { put("document_json", "{\"blocks\":") }.toString()
        val payload = agentToolPayload(partial, emptyMap(), emptyMap())
        assertNull(payload.content)
        assertTrue(payload.fields.single().value.contains("原始数据"))
        assertEquals(Json.parseToJsonElement(partial), Json.parseToJsonElement(payload.rawJson))
        assertEquals("unparsed output", agentToolPayload("unparsed output", emptyMap(), emptyMap()).content)
    }

    @Test fun payloadTranslatesErrorsAndKeepsExtensionFieldsAndStringValues() {
        val raw = buildJsonObject { put("error", "read_required"); put("custom_flag", "false"); put("command", "echo hello") }.toString()
        val payload = agentToolPayload(raw, emptyMap(), emptyMap())
        assertEquals(AgentToolDetailField("失败原因", "需要先读取笔记，再执行此操作。"), payload.fields[0])
        assertEquals(AgentToolDetailField("custom_flag", "false"), payload.fields[1])
        assertEquals(AgentToolDetailField("命令", "echo hello"), payload.fields[2])
    }
}
