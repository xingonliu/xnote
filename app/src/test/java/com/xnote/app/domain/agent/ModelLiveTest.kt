package com.xnote.app.domain.agent

import com.xnote.app.data.agent.HttpModelClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

// -- Tests

class ModelLiveTest {
    @Test fun configuredServiceStreamsAndRoundTripsToolResults() = runBlocking {
        val path = System.getenv("XNOTE_LIVE_CONFIG")
        assumeTrue("真实服务验证需显式设置 XNOTE_LIVE_CONFIG", !path.isNullOrBlank())
        val values = File(path!!).readLines().take(3).associate { line ->
            line.substringBefore(':').trim().lowercase() to line.substringAfter(':').trim()
        }
        val profile = ModelProfile("live", name = "真实服务验收", protocol = ModelProtocol.OpenAI,
            baseUrl = values.getValue("baseurl"), modelId = values.getValue("model"), outputTokens = 1024)
        val secret = values.entries.single { it.key.endsWith("api_key") }.value.trim('"', '\'')
        val client = HttpModelClient()
        var text = ""
        var finish: ModelFinish? = null
        var usage: ModelEvent.Usage? = null
        client.stream(profile, secret, ModelRequest("Reply briefly.", listOf(ModelMessage(AgentMessageRole.User, "Reply OK.")))).collect {
            if (it is ModelEvent.Text) text += it.value
            if (it is ModelEvent.Finished) finish = it.reason
            if (it is ModelEvent.Usage) usage = it
        }
        assertTrue("真实服务需返回文字", text.isNotBlank())
        assertEquals(ModelFinish.Complete, finish)
        assertNotNull(usage)
        println("Live check: text streaming and completion passed.")
        val tool = ModelTool("xnote_connection_check", "Echo value without side effects.", buildJsonObject {
            put("type", "object"); putJsonObject("properties") { putJsonObject("value") { put("type", "string") } }
            putJsonArray("required") { add("value") }; put("additionalProperties", false)
        })
        val prompt = ModelMessage(AgentMessageRole.User, "Call xnote_connection_check with value OK, then report its result.")
        val calls = mutableListOf<ModelToolCall>()
        text = ""; finish = null
        client.stream(profile, secret, ModelRequest("Use the provided function.", listOf(prompt), listOf(tool), true)).collect {
            if (it is ModelEvent.ToolCall) calls += it.value
            if (it is ModelEvent.Text) text += it.value
            if (it is ModelEvent.Finished) finish = it.reason
        }
        assertEquals(ModelFinish.ToolCalls, finish)
        assertEquals(1, calls.size)
        assertEquals("xnote_connection_check", calls.single().name)
        println("Live check: tool call passed.")
        var finalText = ""; finish = null
        client.stream(profile, secret, ModelRequest("Report the test result.", listOf(prompt,
            ModelMessage(AgentMessageRole.Assistant, text, calls),
            ModelMessage(AgentMessageRole.Tool, results = calls.map { ModelToolResult(it.id, it.name, "OK") })), listOf(tool))).collect {
            if (it is ModelEvent.Text) finalText += it.value
            if (it is ModelEvent.Finished) finish = it.reason
        }
        assertEquals(ModelFinish.Complete, finish)
        assertTrue(finalText.isNotBlank())
        println("Live check: tool result round trip passed.")
    }
}
