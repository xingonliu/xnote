package com.xnote.app.feature.agent

import kotlinx.serialization.json.*

// -- Functions

internal fun agentToolTitle(name: String): String = when (name) {
    "read" -> "读取笔记"
    "note_search" -> "搜索笔记"
    "create" -> "新建笔记"
    "write" -> "修改笔记"
    "delete" -> "删除笔记"
    "memory_remember" -> "记住偏好"
    "memory_search" -> "查找聊天记录"
    "memory_read" -> "读取聊天记录"
    "output_file" -> "生成文件"
    else -> name
}

internal fun agentToolTarget(name: String, arguments: JsonObject, noteTitles: Map<String, String>, notebookTitles: Map<String, String>): String {
    fun text(key: String) = (arguments[key] as? JsonPrimitive)?.contentOrNull
    val noteTitle = text("note_id")?.let { noteTitles[it]?.ifBlank { "未命名笔记" } ?: "笔记不可用" }
    return when (name) {
        "read" -> noteTitle ?: text("notebook_id")?.let {
            if (it.isBlank()) "未归档" else notebookTitles[it] ?: "笔记本不可用"
        } ?: "笔记目录"
        "write", "delete" -> noteTitle.orEmpty()
        "create" -> text("title").orEmpty().ifBlank { "未命名笔记" }
        "note_search", "memory_search" -> text("query").orEmpty()
        "memory_remember" -> text("value").orEmpty()
        "memory_read" -> "历史对话"
        "output_file" -> text("filename").orEmpty()
        else -> "查看操作详情"
    }
}

internal fun agentToolDescription(
    name: String,
    arguments: JsonObject,
    noteTitles: Map<String, String>,
    notebookTitles: Map<String, String>,
): String {
    fun text(key: String) = (arguments[key] as? JsonPrimitive)?.contentOrNull
    val noteId = text("note_id")
    val notebookId = text("notebook_id")
    val title = noteId?.let { noteTitles[it]?.ifBlank { "未命名笔记" } ?: "笔记 $it" }
    val targetId = ((arguments["target"] as? JsonObject)?.get("notebookId") as? JsonPrimitive)?.contentOrNull
    val destination = targetId?.let { notebookTitles[it] ?: "笔记本 $it" } ?: "未归档"
    return when (name) {
        "read" -> when {
            noteId != null -> "读取“$title”的内容，发送给当前模型。"
            notebookId != null -> "查看“${if (notebookId.isBlank()) "未归档" else notebookTitles[notebookId] ?: notebookId}”中的笔记列表。"
            else -> "查看你的笔记目录。"
        }
        "note_search" -> "在笔记中搜索“${text("query").orEmpty()}”。"
        "create" -> "在“$destination”中新建“${text("title").orEmpty().ifBlank { "未命名笔记" }}”，保存后可在笔记改动中查看。"
        "write" -> buildString {
            append("修改“$title”")
            if (arguments["selection"] is JsonObject) append("中的选中内容")
            text("title")?.takeIf { it != noteTitles[noteId] }?.let { append("，标题设为“${it.ifBlank { "未命名笔记" }}”") }
            append("。保存后可在笔记改动中查看或撤回。")
        }
        "delete" -> "将“$title”移入回收站，可以恢复。"
        "memory_remember" -> "记住这项偏好：${text("value").orEmpty()}"
        "memory_search" -> "在过去的聊天中搜索“${text("query").orEmpty()}”。"
        "memory_read" -> "读取一段过去的聊天，发送给当前模型。"
        "output_file" -> "生成“${text("filename").orEmpty()}”，供你保存或分享。"
        else -> "请查看完整请求，确认要访问的内容与执行的操作。"
    }
}
