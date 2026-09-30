package com.xnote.app.domain.agent

import kotlinx.serialization.json.*

// -- Constants

const val AgentFinishToolName = "finish_task"
const val AgentFinalMessagePrefix = "finish:"
const val AgentFinishMaxCharacters = 240

val AgentFinishTool = ModelTool(AgentFinishToolName,
    "任务完成后必须单独调用此工具结束本次任务。summary 是给用户的简短结束语，通常一两句，最多 240 字；自然说话，避免官方套话。换行代表新的气泡。正文答案可写入结束语，长内容应使用笔记或文件工具承载。本工具只展示结束语，不读取或修改用户资料。",
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("summary") { put("type", "string"); put("minLength", 1); put("maxLength", AgentFinishMaxCharacters) }
        }
        putJsonArray("required") { add("summary") }
        put("additionalProperties", false)
    })

// -- Functions

fun agentFinishSummary(arguments: JsonObject): String {
    require(arguments.keys == setOf("summary"))
    val raw = arguments["summary"] as? JsonPrimitive
    require(raw?.isString == true)
    require(raw.content.isNotBlank() && raw.content.length <= AgentFinishMaxCharacters)
    val summary = agentReplyPlainText(raw.content).trim()
    require(summary.isNotBlank())
    return summary
}

fun agentFinishError(call: ModelToolCall, responseCallCount: Int, hasPendingInput: Boolean): String? = when {
    call.id.isBlank() || runCatching { agentFinishSummary(call.arguments) }.isFailure -> "invalid_finish_summary"
    responseCallCount != 1 -> "finish_must_be_separate"
    hasPendingInput -> "pending_input"
    else -> null
}

fun agentFinalBubbleTexts(summary: String): List<String> = summary.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
