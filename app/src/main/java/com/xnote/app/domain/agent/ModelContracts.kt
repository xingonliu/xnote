package com.xnote.app.domain.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.net.URI

// -- Type Definitions

@Serializable
enum class ModelProtocol(val label: String, val root: String) {
    OpenAI("OpenAI 兼容（Chat Completions）", "https://api.openai.com/v1"),
    Anthropic("Anthropic Messages", "https://api.anthropic.com/v1"),
    Gemini("Gemini GenerateContent", "https://generativelanguage.googleapis.com/v1beta"),
}

@Serializable
data class ModelCapabilities(val textStreaming: Boolean = false, val tools: Boolean = false, val testedAtEpochMs: Long? = null)

@Serializable
data class ModelProfile(
    val id: String,
    val version: Long = 1,
    val name: String,
    val protocol: ModelProtocol,
    val baseUrl: String = protocol.root,
    val modelId: String,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val contextTokens: Int = ModelLimits.DefaultContextTokens,
    val outputTokens: Int = ModelLimits.DefaultOutputTokens,
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val usesPreset: Boolean = false,
    val providerId: String? = null,
    val providerName: String? = null,
)

data class ModelTool(val name: String, val description: String, val parameters: JsonObject)
data class ModelToolCall(val id: String, val name: String, val arguments: JsonObject)
data class ModelToolResult(val id: String, val name: String, val content: String)
data class ModelMessage(
    val role: AgentMessageRole,
    val text: String = "",
    val calls: List<ModelToolCall> = emptyList(),
    val results: List<ModelToolResult> = emptyList(),
    val nativeParts: JsonArray? = null,
)

data class ModelRequest(
    val system: String,
    val messages: List<ModelMessage>,
    val tools: List<ModelTool> = emptyList(),
    val forceTool: Boolean = false,
)

enum class ModelFinish { Complete, ToolCalls, OutputLimit, Filtered }

sealed interface ModelEvent {
    data class Text(val value: String) : ModelEvent
    data class ToolCall(val value: ModelToolCall) : ModelEvent
    data class NativeParts(val value: JsonArray) : ModelEvent
    data class Usage(val inputTokens: Long?, val outputTokens: Long?) : ModelEvent
    data class Finished(val reason: ModelFinish) : ModelEvent
}

enum class ModelError(val display: String) {
    InvalidConfig("配置无效，请检查 HTTPS 服务根地址、模型名称和容量。"),
    MissingCredential("凭据不可用，请在设置中重新填写 API Key。"),
    Authentication("认证失败，请检查 API Key 与服务地址。"),
    Quota("请求受限或配额不足，请检查服务配额后重试。"),
    InvalidRequest("服务拒绝请求，请检查模型、协议与容量配置。"),
    ContextLimit("请求超过服务的实际上下文容量，已暂停并保留内容。请缩小请求或调整配置容量。"),
    Service("模型服务暂时不可用，请稍后重试。"),
    Network("网络连接失败，请检查网络后重试。"),
    Timeout("模型响应超时，已保留生成内容。"),
    Interrupted("响应流中断，已保留生成内容。"),
    Protocol("服务返回了无法识别的响应，请检查接口协议。"),
    TooLarge("响应超过本地容量限制，已停止接收。"),
    Busy("当前运行或队列尚未处理完，暂不能更改正在使用的模型配置。"),
}

class ModelException(val error: ModelError) : Exception(error.display)

// -- Constants

object ModelLimits {
    const val DefaultContextTokens = 32768
    const val DefaultOutputTokens = 4096
    const val ToolReserveTokens = 1024
    const val ConnectTimeoutMs = 15000
    const val ReadTimeoutMs = 90000
    const val RequestTimeoutMs = 180000L
    const val MaxEventChars = 1024 * 1024
    const val MaxResponseChars = 4 * 1024 * 1024
}

// -- Functions

fun ModelProfile.validate() {
    val uri = runCatching { URI(baseUrl) }.getOrNull()
    if (name.isBlank() || modelId.isBlank() || modelId.any { it.isWhitespace() } ||
        uri == null || uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
        uri.query != null || uri.fragment != null || contextTokens !in 4096..2_000_000 ||
        outputTokens !in 256..65536 || outputTokens + ModelLimits.ToolReserveTokens >= contextTokens
    ) throw ModelException(ModelError.InvalidConfig)
}

fun safeModelError(error: Throwable): String = (error as? ModelException)?.error?.display ?: ModelError.Service.display
