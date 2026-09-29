package com.xnote.app.data.agent

import com.xnote.app.domain.agent.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

// -- Type Definitions

data class ModelCapabilityResult(val capabilities: ModelCapabilities, val detail: String)

class ModelCapabilityTest(private val client: ModelClient, private val store: ModelProfileStore) {
    // -- Functions

    suspend fun test(profile: ModelProfile): ModelCapabilityResult {
        val secret = store.credential(profile)
        val probe = profile.copy(outputTokens = minOf(profile.outputTokens, 1024))
        var capabilities = profile.capabilities.copy(textStreaming = false, tools = false, testedAtEpochMs = System.currentTimeMillis())
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
            return ModelCapabilityResult(capabilities, "连接成功，模型文字流式可用。")
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            store.recordCapabilities(profile, capabilities)
            return ModelCapabilityResult(capabilities, "连接测试未通过。" + safeModelError(error))
        }
    }
}
