package com.xnote.app.data.agent

import com.xnote.app.domain.agent.*
import kotlinx.serialization.json.*
import java.io.Reader

// -- Type Definitions

private data class PartialTool(var id: String = "", var name: String = "", val arguments: StringBuilder = StringBuilder())

class ModelStreamDecoder(private val protocol: ModelProtocol) {
    // -- State and Variables

    private val calls = linkedMapOf<Int, PartialTool>()
    private val nativeParts = mutableListOf<JsonElement>()
    private var finish: ModelFinish? = null
    private var terminal = false
    private var inputTokens: Long? = null
    private var outputTokens: Long? = null

    // -- Functions

    fun accept(data: String): List<ModelEvent> {
        if (terminal) throw ModelException(ModelError.Protocol)
        if (data == "[DONE]" && protocol == ModelProtocol.OpenAI) {
            if (finish == null) throw ModelException(ModelError.Interrupted)
            terminal = true
            return emptyList()
        }
        val value = try { Json.parseToJsonElement(data).jsonObject } catch (_: Exception) { throw ModelException(ModelError.Protocol) }
        if (value["error"] != null || value.string("type") == "error") throw ModelException(modelServiceError(value.obj("error")))
        val events = mutableListOf<ModelEvent>()
        when (protocol) {
            ModelProtocol.OpenAI -> {
                value.obj("usage")?.let { inputTokens = it.long("prompt_tokens"); outputTokens = it.long("completion_tokens") }
                value.array("choices").forEach { choiceElement ->
                    val choice = choiceElement.jsonObject
                    if (choice.int("index") != 0) throw ModelException(ModelError.Protocol)
                    val delta = choice.obj("delta")
                    delta?.string("content")?.let { events += ModelEvent.Text(it) }
                    delta?.string("refusal")?.let { events += ModelEvent.Text(it) }
                    delta?.array("tool_calls")?.forEach { element ->
                        val tool = element.jsonObject
                        val partial = calls.getOrPut(tool.int("index") ?: throw ModelException(ModelError.Protocol)) { PartialTool() }
                        tool.string("id")?.let { partial.id = it }
                        tool.obj("function")?.let { function ->
                            function.string("name")?.let { partial.name += it }
                            function.string("arguments")?.let { partial.arguments.append(it) }
                        }
                    }
                    choice.string("finish_reason")?.let { finish = when (it) {
                        "stop" -> ModelFinish.Complete
                        "tool_calls" -> ModelFinish.ToolCalls
                        "length" -> ModelFinish.OutputLimit
                        else -> ModelFinish.Filtered
                    } }
                }
            }
            ModelProtocol.Anthropic -> when (value.string("type")) {
                "message_start" -> value.obj("message")?.obj("usage")?.let {
                    inputTokens = it.long("input_tokens")?.plus(it.long("cache_creation_input_tokens") ?: 0)?.plus(it.long("cache_read_input_tokens") ?: 0)
                }
                "content_block_start" -> value.obj("content_block")?.let { block ->
                    if (block.string("type") == "tool_use") {
                        val partial = PartialTool(block.string("id").orEmpty(), block.string("name").orEmpty())
                        block.obj("input")?.takeIf { it.isNotEmpty() }?.let { partial.arguments.append(it.toString()) }
                        calls[value.int("index") ?: throw ModelException(ModelError.Protocol)] = partial
                    } else if (block.string("type") == "text") block.string("text")?.let { events += ModelEvent.Text(it) }
                }
                "content_block_delta" -> value.obj("delta")?.let { delta -> when (delta.string("type")) {
                    "text_delta" -> events += ModelEvent.Text(delta.string("text").orEmpty())
                    "input_json_delta" -> (calls[value.int("index")] ?: throw ModelException(ModelError.Protocol))
                        .arguments.append(delta.string("partial_json").orEmpty())
                } }
                "message_delta" -> {
                    value.obj("usage")?.long("output_tokens")?.let { outputTokens = it }
                    value.obj("delta")?.string("stop_reason")?.let { finish = when (it) {
                        "end_turn", "stop_sequence" -> ModelFinish.Complete
                        "tool_use" -> ModelFinish.ToolCalls
                        "max_tokens" -> ModelFinish.OutputLimit
                        else -> ModelFinish.Filtered
                    } }
                }
                "message_stop" -> terminal = true
            }
            ModelProtocol.Gemini -> {
                value.obj("usageMetadata")?.let {
                    inputTokens = it.long("promptTokenCount")
                    outputTokens = it.long("candidatesTokenCount")?.plus(it.long("thoughtsTokenCount") ?: 0)
                }
                if (value.obj("promptFeedback")?.string("blockReason") != null) {
                    finish = ModelFinish.Filtered; terminal = true
                }
                value.array("candidates").forEach { element ->
                    val candidate = element.jsonObject
                    candidate.obj("content")?.array("parts")?.forEach { partElement ->
                        val part = partElement.jsonObject
                        nativeParts += part
                        if (part["thought"]?.jsonPrimitive?.booleanOrNull != true) part.string("text")?.let { events += ModelEvent.Text(it) }
                        part.obj("functionCall")?.let { call ->
                            calls[calls.size] = PartialTool(call.string("id") ?: "gemini-${calls.size}", call.string("name").orEmpty(),
                                StringBuilder(call.obj("args")?.toString() ?: "{}"))
                        }
                    }
                    candidate.string("finishReason")?.let { finish = when (it) {
                        "STOP" -> if (calls.isEmpty()) ModelFinish.Complete else ModelFinish.ToolCalls
                        "MAX_TOKENS" -> ModelFinish.OutputLimit
                        else -> ModelFinish.Filtered
                    } }
                }
                // GenerateContent uses the final finishReason instead of a separate [DONE] marker.
                if (finish != null) terminal = true
            }
        }
        return events
    }

    fun end(): List<ModelEvent> {
        if (!terminal || finish == null) throw ModelException(ModelError.Interrupted)
        val events = mutableListOf<ModelEvent>()
        if (finish == ModelFinish.ToolCalls) {
            if (calls.isEmpty()) throw ModelException(ModelError.Protocol)
            calls.values.forEach { call ->
                if (call.id.isBlank() || call.name.isBlank()) throw ModelException(ModelError.Protocol)
                val args = try { Json.parseToJsonElement(call.arguments.toString().ifEmpty { "{}" }).jsonObject }
                    catch (_: Exception) { throw ModelException(ModelError.Protocol) }
                events += ModelEvent.ToolCall(ModelToolCall(call.id, call.name, args))
            }
        } else if (calls.isNotEmpty() && finish == ModelFinish.Complete) throw ModelException(ModelError.Protocol)
        if (nativeParts.isNotEmpty()) events += ModelEvent.NativeParts(JsonArray(nativeParts))
        events += ModelEvent.Usage(inputTokens, outputTokens)
        events += ModelEvent.Finished(finish!!)
        return events
    }
}

// -- Functions

internal fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull
internal fun JsonObject.long(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull
internal fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject
internal fun JsonObject.array(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())

internal fun modelServiceError(error: JsonObject?, fallback: ModelError = ModelError.Service): ModelError {
    val description = listOfNotNull(error?.string("code"), error?.string("type"), error?.string("message")).joinToString(" ").lowercase()
    return when {
        listOf("context_length_exceeded", "context_window_exceeded", "maximum context length", "prompt is too long", "input token count exceeds", "context window").any { it in description } -> ModelError.ContextLimit
        "authentication_error" in description || "invalid_api_key" in description -> ModelError.Authentication
        "rate_limit" in description || "quota" in description -> ModelError.Quota
        else -> fallback
    }
}

/** Bounded SSE reader; dispatches only complete events and never treats EOF as completion. */
suspend fun readModelSse(reader: Reader, onData: suspend (String) -> Unit) {
    val line = StringBuilder()
    val data = StringBuilder()
    var total = 0
    suspend fun dispatchLine() {
        val value = line.toString().removeSuffix("\r")
        line.clear()
        if (value.isEmpty()) {
            if (data.isNotEmpty()) { onData(data.toString().removeSuffix("\n")); data.clear() }
        } else if (value.startsWith("data:")) {
            data.append(value.substring(5).removePrefix(" ")).append('\n')
            if (data.length > ModelLimits.MaxEventChars) throw ModelException(ModelError.TooLarge)
        }
    }
    while (true) {
        val char = reader.read()
        if (char < 0) break
        if (++total > ModelLimits.MaxResponseChars) throw ModelException(ModelError.TooLarge)
        if (char == '\n'.code) dispatchLine() else {
            line.append(char.toChar())
            if (line.length > ModelLimits.MaxEventChars) throw ModelException(ModelError.TooLarge)
        }
    }
    if (line.isNotEmpty() || data.isNotEmpty()) throw ModelException(ModelError.Interrupted)
}
