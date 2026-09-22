package com.xnote.app.data.agent

import com.xnote.app.domain.agent.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.*

// -- Type Definitions

data class ModelCapabilityResult(val capabilities: ModelCapabilities, val detail: String)

class ModelCapabilityTest(private val client: ModelClient, private val store: ModelProfileStore) {
    // -- Functions

    suspend fun test(profile: ModelProfile): ModelCapabilityResult {
        val secret = store.credential(profile)
        val probe = profile.copy(outputTokens = minOf(profile.outputTokens, 1024))
        var capabilities = ModelCapabilities(testedAtEpochMs = System.currentTimeMillis())
        try {
            var hasText = false
            var completed = false
            client.stream(probe, secret, ModelRequest("Reply briefly.", listOf(ModelMessage(AgentMessageRole.User, "Reply OK.")))).collect {
                if (it is ModelEvent.Text && it.value.isNotBlank()) hasText = true
                if (it is ModelEvent.Finished && it.reason == ModelFinish.Complete) completed = true
            }
            if (!hasText || !completed) throw ModelException(ModelError.Protocol)
            capabilities = capabilities.copy(textStreaming = true)
            store.recordCapabilities(profile, capabilities)
            val tool = ModelTool("xnote_connection_check", "Return the supplied value; this test has no side effects.", buildJsonObject {
                put("type", "object"); putJsonObject("properties") { putJsonObject("value") { put("type", "string") } }
                putJsonArray("required") { add("value") }; put("additionalProperties", false)
            })
            val messages = listOf(ModelMessage(AgentMessageRole.User, "Call xnote_connection_check with value OK, then report its result."))
            val calls = mutableListOf<ModelToolCall>()
            var native: JsonArray? = null
            var firstText = ""
            var toolsFinished = false
            client.stream(probe, secret, ModelRequest("Use the provided test function.", messages, listOf(tool), true)).collect { event ->
                when (event) {
                    is ModelEvent.ToolCall -> calls += event.value
                    is ModelEvent.NativeParts -> native = event.value
                    is ModelEvent.Text -> firstText += event.value
                    is ModelEvent.Finished -> toolsFinished = event.reason == ModelFinish.ToolCalls
                    else -> Unit
                }
            }
            if (!toolsFinished || calls.size != 1 || calls.single().name != tool.name || calls.single().arguments.string("value") != "OK") throw ModelException(ModelError.Protocol)
            var finalText = ""
            completed = false
            client.stream(probe, secret, ModelRequest("Report the test result briefly.", messages +
                ModelMessage(AgentMessageRole.Assistant, firstText, calls, nativeParts = native) +
                ModelMessage(AgentMessageRole.Tool, results = calls.map { ModelToolResult(it.id, it.name, "OK") }), listOf(tool))).collect { event ->
                if (event is ModelEvent.Text) finalText += event.value
                if (event is ModelEvent.Finished) completed = event.reason == ModelFinish.Complete
            }
            if (!completed || finalText.isBlank()) throw ModelException(ModelError.Protocol)
            capabilities = capabilities.copy(tools = true)
            store.recordCapabilities(profile, capabilities)
            return ModelCapabilityResult(capabilities, "文字流式与工具调用/结果回传验证通过。")
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            store.recordCapabilities(profile, capabilities)
            return ModelCapabilityResult(capabilities, (if (capabilities.textStreaming) "文字流式通过；工具验证未通过。" else "文字流式未通过。") + safeModelError(error))
        }
    }
}
