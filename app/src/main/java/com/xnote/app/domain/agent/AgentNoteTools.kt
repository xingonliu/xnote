package com.xnote.app.domain.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// -- Type Definitions

@Serializable
data class AgentReadArguments(val note_id: String, val snapshot_id: String? = null, val offset: Int = 0, val limit: Int = AgentNoteLimits.ReadPageCharacters)

@Serializable
data class AgentSearchArguments(val query: String, val offset: Int = 0, val limit: Int = AgentNoteLimits.SearchPageSize)

sealed interface AgentReadToolResult {
    data class Finished(val result: ModelToolResult, val sources: List<AgentMessageSource>) : AgentReadToolResult
    data class PermissionRequired(val callId: String) : AgentReadToolResult
}

data class AgentProjectedMessage(val message: ModelMessage, val sources: List<AgentMessageSource>)

// -- Constants

object AgentNoteLimits {
    const val MaxAttachedNotes = 8
    const val ReadPageCharacters = 6000
    const val SearchPageSize = 20
    const val MaxQueryCharacters = 200
    const val MaxToolCallsPerResponse = 8
    const val MaxToolArgumentCharacters = 4096
}

val AgentReadTools = listOf(
    ModelTool("read", "读取已授权笔记。snapshot_id 明确读取发送快照；省略时读取最新版本。正文为可分页拼接的 document_json，offset/next_offset 使用 UTF-16 字符位置。不得把快照当作最新版本。", buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("note_id") { put("type", "string") }
            putJsonObject("snapshot_id") { put("type", "string") }
            putJsonObject("offset") { put("type", "integer"); put("minimum", 0) }
            putJsonObject("limit") { put("type", "integer"); put("minimum", 1); put("maximum", AgentNoteLimits.ReadPageCharacters) }
        }
        putJsonArray("required") { add("note_id") }
        put("additionalProperties", false)
    }),
    ModelTool("note_search", "在当前授权范围内检索笔记标题和正文，返回有限页及是否还有结果；无权访问的数据不会参与结果。", buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") { put("type", "string"); put("maxLength", AgentNoteLimits.MaxQueryCharacters) }
            putJsonObject("offset") { put("type", "integer"); put("minimum", 0) }
            putJsonObject("limit") { put("type", "integer"); put("minimum", 1); put("maximum", AgentNoteLimits.SearchPageSize) }
        }
        putJsonArray("required") { add("query") }
        put("additionalProperties", false)
    }),
)
