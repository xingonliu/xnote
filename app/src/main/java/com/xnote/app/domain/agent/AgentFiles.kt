package com.xnote.app.domain.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// -- Type Definitions

@Serializable
data class AgentOutputFileArguments(val filename: String, val content: String)

object AgentFileLimits {
    const val Count = 4
    const val FileBytes = 8 * 1024 * 1024
    const val TotalBytes = 12 * 1024 * 1024
    const val TextBytes = 128 * 1024
    const val TextCharacters = 32 * 1024
    const val PdfPages = 20
    const val ImageEdge = 2048
    const val ImageTokens = 4096
    const val PdfPageTokens = 4096
    const val OutputCount = 4
}

// -- Constants

val AgentFileTools = listOf(ModelTool("output_file", "生成纯文本或 Markdown 文件供用户预览、保存和分享，不创建笔记。filename 仅允许 .txt 或 .md，content 最多 32768 字符，每个任务最多 4 个文件。", buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("filename") { put("type", "string") }
        putJsonObject("content") { put("type", "string") }
    }
    putJsonArray("required") { add("filename"); add("content") }; put("additionalProperties", false)
}))
