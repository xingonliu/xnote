package com.xnote.app.domain.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// -- Type Definitions

@Serializable
data class AgentReadArguments(val note_id: String, val snapshot_id: String? = null, val offset: Int = 0, val limit: Int = AgentNoteLimits.ReadPageCharacters)

@Serializable
data class AgentSearchArguments(val query: String, val offset: Int = 0, val limit: Int = AgentNoteLimits.SearchPageSize)

@Serializable
data class AgentWriteArguments(val note_id: String, val base_version: String, val title: String, val document_json: String,
    val selection: AgentSelection? = null)

@Serializable
data class AgentCreateArguments(val title: String, val document_json: String, val target: AgentCreateTarget? = null)

@Serializable
data class AgentDeleteArguments(val note_id: String, val base_version: String)

sealed interface AgentToolResult {
    data class Finished(val result: ModelToolResult, val sources: List<AgentMessageSource>) : AgentToolResult
    data class PermissionRequired(val callId: String) : AgentToolResult
    data class Conflict(val callId: String, val location: String) : AgentToolResult
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
    const val MaxWriteArgumentCharacters = 262_144
}

val AgentNoteTools = listOf(
    ModelTool("create", "三级权限新建笔记并实时保存，进入单篇审阅。仅可新建文字/表格，不添加媒体或设置背景。document_json 与 read/write 格式相同。用户明确指定归属时传 target（notebookId 为笔记本 ID，null 为未归档）；未指定时省略 target，应用会使用唯一可写目标或暂停请用户选择并授权。本次新建不会扩大整个笔记本权限。", buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") { put("type", "string") }
            putJsonObject("document_json") { put("type", "string"); put("maxLength", AgentNoteLimits.MaxWriteArgumentCharacters) }
            putJsonObject("target") {
                put("type", "object")
                putJsonObject("properties") { putJsonObject("notebookId") { putJsonArray("type") { add("string"); add("null") } } }
                putJsonArray("required") { add("notebookId") }
                put("additionalProperties", false)
            }
        }
        putJsonArray("required") { add("title"); add("document_json") }
        put("additionalProperties", false)
    }),
    ModelTool("delete", "三级权限将笔记移入回收站，可从单篇审阅拒绝并恢复。先 read 或使用已附加快照的版本作为 base_version；正文或设置已变化时暂停，不删除用户的新内容。不会永久删除。", buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("note_id") { put("type", "string") }
            putJsonObject("base_version") { put("type", "string") }
        }
        putJsonArray("required") { add("note_id"); add("base_version") }
        put("additionalProperties", false)
    }),
    ModelTool("read", "读取已授权笔记。省略 snapshot_id 时读取当前版本，返回可固定该版本的 snapshot_id；分页继续时传回它，避免混合不同版本。也可指定发送快照 ID。正文为可拼接的 document_json，offset/next_offset 使用 UTF-16 字符位置。快照不是当前版本，也不会增加权限。", buildJsonObject {
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
    ModelTool("write", "三级权限修改笔记，实时保存并进入单篇审阅。base_version 必须来自当前话题的 read 或发送快照；先完整读取并拼接 document_json，保持结构和块 ID。title 与 document_json 为拟应用的完整标题和正文；正文格式与 read 相同，新增文本块使用 type=text、id 和 inlines（含 text）。不得改变媒体、背景或归属。用户并行编辑由应用合并，重叠冲突暂停等待用户。每次写后继续改动应重新 read。", buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("note_id") { put("type", "string") }
            putJsonObject("base_version") { put("type", "string") }
            putJsonObject("title") { put("type", "string") }
            putJsonObject("document_json") { put("type", "string"); put("maxLength", AgentNoteLimits.MaxWriteArgumentCharacters) }
            putJsonObject("selection") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("version") { put("type", "string") }
                    putJsonObject("blockId") { put("type", "string") }
                    putJsonObject("start") { put("type", "integer"); put("minimum", 0) }
                    putJsonObject("end") { put("type", "integer"); put("minimum", 0) }
                    putJsonObject("tableRow") { put("type", "integer"); put("minimum", 0) }
                    putJsonObject("tableColumn") { put("type", "integer"); put("minimum", 0) }
                }
                putJsonArray("required") { add("version"); add("blockId"); add("start"); add("end") }
                put("additionalProperties", false)
            }
        }
        putJsonArray("required") { add("note_id"); add("base_version"); add("title"); add("document_json") }
        put("additionalProperties", false)
    }),
)
