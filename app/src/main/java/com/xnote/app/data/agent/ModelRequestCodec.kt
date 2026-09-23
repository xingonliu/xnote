package com.xnote.app.data.agent

import com.xnote.app.domain.agent.*
import kotlinx.serialization.json.*

// -- Functions

fun modelRequestJson(profile: ModelProfile, request: ModelRequest): JsonObject = buildJsonObject {
    when (profile.protocol) {
        ModelProtocol.OpenAI -> {
            put("model", profile.modelId)
            put("stream", true)
            val nativeOpenAI = profile.providerId == null || profile.providerId == "openai"
            if (nativeOpenAI) put("store", false)
            put(if (nativeOpenAI) "max_completion_tokens" else "max_tokens", profile.outputTokens)
            if (profile.providerId != "mistral") putJsonObject("stream_options") { put("include_usage", true) }
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", request.system) })
                request.messages.forEach { message ->
                    if (message.results.isNotEmpty()) message.results.forEach { result ->
                        add(buildJsonObject { put("role", "tool"); put("tool_call_id", result.id); put("content", result.content) })
                    } else add(buildJsonObject {
                        put("role", if (message.role == AgentMessageRole.Assistant) "assistant" else "user")
                        put("content", message.text)
                        if (message.calls.isNotEmpty()) putJsonArray("tool_calls") {
                            message.calls.forEach { call -> add(buildJsonObject {
                                put("id", call.id); put("type", "function")
                                putJsonObject("function") { put("name", call.name); put("arguments", call.arguments.toString()) }
                            }) }
                        }
                    })
                }
            }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") { request.tools.forEach { tool -> add(buildJsonObject {
                    put("type", "function"); putJsonObject("function") {
                        put("name", tool.name); put("description", tool.description); put("parameters", tool.parameters)
                    }
                }) } }
                if (request.forceTool) put("tool_choice", "required")
            }
        }
        ModelProtocol.Anthropic -> {
            put("model", profile.modelId); put("stream", true); put("max_tokens", profile.outputTokens); put("system", request.system)
            putJsonArray("messages") { request.messages.forEach { message -> add(buildJsonObject {
                put("role", if (message.role == AgentMessageRole.Assistant) "assistant" else "user")
                putJsonArray("content") {
                    if (message.text.isNotEmpty()) add(buildJsonObject { put("type", "text"); put("text", message.text) })
                    message.calls.forEach { call -> add(buildJsonObject {
                        put("type", "tool_use"); put("id", call.id); put("name", call.name); put("input", call.arguments)
                    }) }
                    message.results.forEach { result -> add(buildJsonObject {
                        put("type", "tool_result"); put("tool_use_id", result.id); put("content", result.content)
                    }) }
                }
            }) } }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") { request.tools.forEach { tool -> add(buildJsonObject {
                    put("name", tool.name); put("description", tool.description); put("input_schema", tool.parameters)
                }) } }
                if (request.forceTool) putJsonObject("tool_choice") { put("type", "any") }
            }
        }
        ModelProtocol.Gemini -> {
            putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", request.system) }) } }
            putJsonObject("generationConfig") { put("maxOutputTokens", profile.outputTokens) }
            putJsonArray("contents") { request.messages.forEach { message -> add(buildJsonObject {
                put("role", if (message.role == AgentMessageRole.Assistant) "model" else "user")
                if (message.nativeParts != null) put("parts", message.nativeParts) else putJsonArray("parts") {
                    if (message.text.isNotEmpty()) add(buildJsonObject { put("text", message.text) })
                    message.calls.forEach { call -> add(buildJsonObject { putJsonObject("functionCall") {
                        put("id", call.id); put("name", call.name); put("args", call.arguments)
                    } }) }
                    message.results.forEach { result -> add(buildJsonObject { putJsonObject("functionResponse") {
                        put("id", result.id); put("name", result.name); putJsonObject("response") { put("result", result.content) }
                    } }) }
                }
            }) } }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") { add(buildJsonObject { putJsonArray("functionDeclarations") {
                    request.tools.forEach { tool -> add(buildJsonObject {
                        put("name", tool.name); put("description", tool.description); put("parameters", tool.parameters)
                    }) }
                } }) }
                if (request.forceTool) putJsonObject("toolConfig") { putJsonObject("functionCallingConfig") { put("mode", "ANY") } }
            }
        }
    }
}
