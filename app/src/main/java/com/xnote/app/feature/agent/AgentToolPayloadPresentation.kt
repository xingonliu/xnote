package com.xnote.app.feature.agent

import com.xnote.app.domain.document.decodeNoteDocument
import com.xnote.app.domain.text.extractPlainText
import kotlinx.serialization.json.*
import java.text.DateFormat
import java.util.Date

// -- Type Definitions

internal data class AgentToolDetailField(val label: String, val value: String)

internal data class AgentToolPayload(
    val fields: List<AgentToolDetailField>,
    val items: List<AgentToolDetailField>,
    val content: String?,
    val rawJson: String,
)

// -- Constants

private val ToolDetailJson = Json { prettyPrint = true }
private val TechnicalToolFields = setOf("version", "base_version", "snapshot_id", "review_id", "attachment_id",
    "episodeId", "key", "cursor", "next_offset", "trust", "source", "kind")
private val ToolFieldLabels = mapOf(
    "note_id" to "笔记", "notebook_id" to "笔记本", "target" to "保存位置", "title" to "标题",
    "query" to "关键词", "filename" to "文件名", "value" to "偏好内容", "quote" to "用户原话",
    "sensitive" to "敏感内容", "selection" to "修改范围", "offset" to "起始位置", "limit" to "单次读取上限",
    "startTime" to "开始时间", "endTime" to "结束时间", "has_more" to "还有更多结果",
    "bytes" to "文件大小（字节）", "status" to "处理结果", "error" to "失败原因", "location" to "冲突位置",
    "active" to "偏好已生效", "decision" to "记忆处理", "command" to "命令", "path" to "路径",
)

// -- Functions

internal fun agentToolJsonObject(raw: String): JsonObject =
    runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())

internal fun agentToolPayload(raw: String, noteTitles: Map<String, String>, notebookTitles: Map<String, String>): AgentToolPayload {
    val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
    val formatted = element?.let { ToolDetailJson.encodeToString(JsonElement.serializer(), it) } ?: raw
    if (element !is JsonObject) return AgentToolPayload(emptyList(), emptyList(), raw, formatted)
    val fields = mutableListOf<AgentToolDetailField>()
    val items = mutableListOf<AgentToolDetailField>()
    var content: String? = null
    for ((key, value) in element) {
        when {
            key == "document_json" -> {
                val document = (value as? JsonPrimitive)?.contentOrNull
                content = document?.let { runCatching { extractPlainText(decodeNoteDocument(it)) }.getOrNull() }
                if (content == null) fields += AgentToolDetailField("正文", "正文为分段或未解析的数据，可在原始数据中查看。")
            }
            key in setOf("content", "text") && value is JsonPrimitive -> content = value.contentOrNull
            key in setOf("entries", "notes", "items", "messages") && value is JsonArray -> {
                fields += AgentToolDetailField("返回数量", "${value.size} 项")
                value.forEachIndexed { index, entry ->
                    val item = entry as? JsonObject
                    val title = item?.text("title")?.ifBlank { "未命名笔记" }
                        ?: item?.text("role")?.let { when (it) {
                            "User" -> "用户"; "Assistant" -> "Agent"; else -> it
                        } } ?: "第 ${index + 1} 项"
                    val body = item?.let { listOfNotNull(it.text("summary"), it.text("snippet"), it.text("excerpt"), it.text("text"))
                        .filter { text -> text.isNotBlank() }.distinct().joinToString("\n") } ?: entry.toString()
                    items += AgentToolDetailField(title, body)
                }
            }
            key in TechnicalToolFields -> Unit
            else -> {
                val text = when (key) {
                    "note_id" -> (value as? JsonPrimitive)?.contentOrNull?.let { noteTitles[it]?.ifBlank { "未命名笔记" } ?: "笔记不可用" }
                    "notebook_id" -> notebookTitle((value as? JsonPrimitive)?.contentOrNull, notebookTitles)
                    "target" -> notebookTitle((value as? JsonObject)?.text("notebookId"), notebookTitles)
                    "selection" -> "仅修改所选文字"
                    "error" -> (value as? JsonPrimitive)?.contentOrNull?.let(::agentToolDecisionReason)
                    "status" -> when ((value as? JsonPrimitive)?.contentOrNull) {
                        "created" -> "已新建"; "applied" -> "已保存改动"; "trashed" -> "已移入回收站"
                        "unchanged" -> "内容无需改动"; "saved" -> "已生成文件"; else -> toolFieldValue(value)
                    }
                    "decision" -> when ((value as? JsonPrimitive)?.contentOrNull) {
                        "Add" -> "已记住"; "Supersede" -> "已替换原有偏好"; "Review" -> "等待确认"; "Ignore" -> "未新增记忆"
                        else -> toolFieldValue(value)
                    }
                    "startTime", "endTime" -> (value as? JsonPrimitive)?.longOrNull?.let {
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                    }
                    else -> toolFieldValue(value)
                }
                if (text != null) fields += AgentToolDetailField(ToolFieldLabels[key] ?: key, text)
            }
        }
    }
    if (element.isNotEmpty() && fields.isEmpty() && items.isEmpty() && content == null) {
        fields += AgentToolDetailField("详情", "完整字段可在原始数据中查看。")
    }
    return AgentToolPayload(fields, items, content, formatted)
}

internal fun agentToolDecisionReason(reason: String): String = when (reason) {
    "private_mode" -> "完全隐私模式下无法执行此操作。"
    "unavailable_under_current_permission", "resource_unavailable", "tool_unavailable" -> "当前权限下无法访问所需内容。"
    "selection_scope" -> "此操作超出了所选文字的范围。"
    "read_required" -> "需要先读取笔记，再执行此操作。"
    "edit_conflict" -> "笔记内容已变化，需要重新读取并调整。"
    "explicit_request_required" -> "需要用户明确要求记住这项偏好。"
    else -> reason
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

private fun notebookTitle(id: String?, titles: Map<String, String>): String =
    if (id.isNullOrBlank()) "未归档" else titles[id] ?: "笔记本不可用"

private fun toolFieldValue(value: JsonElement): String? = when (value) {
    JsonNull -> null
    is JsonPrimitive -> if (value.isString) value.content else when (value.booleanOrNull) { true -> "是"; false -> "否"; null -> value.content }
    else -> ToolDetailJson.encodeToString(JsonElement.serializer(), value)
}
