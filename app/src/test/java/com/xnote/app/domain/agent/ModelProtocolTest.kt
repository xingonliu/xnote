package com.xnote.app.domain.agent

import com.xnote.app.data.agent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.sun.net.httpserver.HttpServer

// -- Tests

class ModelProtocolTest {
    private fun profile(protocol: ModelProtocol) = ModelProfile("profile", name = "测试", protocol = protocol, modelId = "test-model")
    private val request = ModelRequest("system", listOf(ModelMessage(AgentMessageRole.User, "你好")))

    @Test fun allProtocolsDecodeTextUsageAndExplicitCompletion() {
        for (protocol in ModelProtocol.entries) {
            val decoder = ModelStreamDecoder(protocol)
            val events = textEvents(protocol).flatMap(decoder::accept) + decoder.end()
            assertEquals("你好", events.filterIsInstance<ModelEvent.Text>().joinToString("") { it.value })
            assertEquals(ModelFinish.Complete, events.filterIsInstance<ModelEvent.Finished>().single().reason)
            assertEquals(ModelEvent.Usage(3, 2), events.filterIsInstance<ModelEvent.Usage>().single())
        }
    }

    @Test fun missingTerminatorNeverCompletes() {
        for (protocol in ModelProtocol.entries) {
            val decoder = ModelStreamDecoder(protocol)
            textEvents(protocol).dropLast(1).forEach(decoder::accept)
            assertThrows(ModelException::class.java) { decoder.end() }
        }
    }

    @Test fun providerErrorPayloadNeverAppearsInException() {
        for (protocol in ModelProtocol.entries) {
            val error = assertThrows(ModelException::class.java) { ModelStreamDecoder(protocol).accept("""{"error":{"message":"secret-key-value"}}""") }
            assertFalse(error.toString().contains("secret-key-value"))
        }
    }

    @Test fun fragmentedToolArgumentsAndNativeSignatureAreRetained() {
        for (protocol in ModelProtocol.entries) {
            val decoder = ModelStreamDecoder(protocol)
            toolEvents(protocol).forEach(decoder::accept)
            val events = decoder.end()
            val call = events.filterIsInstance<ModelEvent.ToolCall>().single().value
            assertEquals("echo", call.name)
            assertEquals("OK", call.arguments["value"]?.jsonPrimitive?.content)
            val native = events.filterIsInstance<ModelEvent.NativeParts>().singleOrNull()?.value
            val payload = modelRequestJson(profile(protocol), ModelRequest("sys", listOf(
                ModelMessage(AgentMessageRole.Assistant, calls = listOf(call), nativeParts = native),
                ModelMessage(AgentMessageRole.Tool, results = listOf(ModelToolResult(call.id, call.name, "OK"))),
            ))).toString()
            assertTrue(payload.contains(when (protocol) { ModelProtocol.OpenAI -> "tool_call_id"; ModelProtocol.Anthropic -> "tool_result"; ModelProtocol.Gemini -> "functionResponse" }))
            if (protocol == ModelProtocol.Gemini) assertTrue(payload.contains("signature-value"))
        }
    }

    @Test fun geminiCallsWithoutProviderIdsHaveDistinctDurableIdsAcrossResponses() {
        fun call(): ModelToolCall {
            val decoder = ModelStreamDecoder(ModelProtocol.Gemini)
            decoder.accept("""{"candidates":[{"content":{"parts":[{"functionCall":{"name":"read","args":{"note_id":"note"}},"thoughtSignature":"keep-signature"}]},"finishReason":"STOP"}]}""")
            return decoder.end().filterIsInstance<ModelEvent.ToolCall>().single().value
        }
        assertNotEquals(call().id, call().id)
    }

    @Test fun outputLimitIsNotSuccessfulCompletion() {
        val decoder = ModelStreamDecoder(ModelProtocol.OpenAI)
        decoder.accept("""{"choices":[{"index":0,"delta":{},"finish_reason":"length"}]}""")
        decoder.accept("[DONE]")
        assertEquals(ModelEvent.Finished(ModelFinish.OutputLimit), decoder.end().last())
    }

    @Test fun serviceContextLimitHasActionableErrorWithoutRawBody() {
        val error = assertThrows(ModelException::class.java) {
            ModelStreamDecoder(ModelProtocol.OpenAI).accept("""{"error":{"code":"context_length_exceeded","message":"private-content"}}""")
        }
        assertEquals(ModelError.ContextLimit, error.error)
        assertFalse(error.toString().contains("private-content"))
    }

    @Test fun sseSupportsCommentsCrLfAndMultilineAndRejectsTruncatedEvents() = runBlocking {
        val results = mutableListOf<String>()
        readModelSse(StringReader(":ping\r\nevent: chunk\r\ndata: first\r\ndata: second\r\n\r\n")) { results += it }
        assertEquals(listOf("first\nsecond"), results)
        try { readModelSse(StringReader("data: partial")) {}; fail("truncated event") } catch (error: ModelException) { assertEquals(ModelError.Interrupted, error.error) }
    }

    @Test fun profileRejectsInsecureOrCredentialBearingAddressesAndBadBudgets() {
        val profile = profile(ModelProtocol.OpenAI)
        listOf("http://example.com", "https://user:secret@example.com", "https://example.com?key=secret", "https://example.com/#secret").forEach {
            assertThrows(ModelException::class.java) { profile.copy(baseUrl = it).validate() }
        }
        assertThrows(ModelException::class.java) { profile.copy(contextTokens = 4096, outputTokens = 4096).validate() }
        profile.validate()
    }

    @Test fun allProtocolsStreamOverHttpAndUseCorrectAuthentication() = runBlocking {
        for (protocol in ModelProtocol.entries) {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            var observedKey: String? = null
            var observedBody: String? = null
            server.createContext("/") { exchange ->
                observedKey = exchange.requestHeaders.getFirst(when (protocol) { ModelProtocol.OpenAI -> "Authorization"; ModelProtocol.Anthropic -> "x-api-key"; ModelProtocol.Gemini -> "x-goog-api-key" })
                observedBody = exchange.requestBody.bufferedReader().readText()
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { output -> textEvents(protocol).forEach { output.write("data: $it\n\n".toByteArray()); output.flush() } }
            }
            server.start()
            try {
                val events = localClient(server).stream(profile(protocol), "test-secret", request).toList()
                assertEquals(ModelFinish.Complete, events.filterIsInstance<ModelEvent.Finished>().single().reason)
                assertEquals(if (protocol == ModelProtocol.OpenAI) "Bearer test-secret" else "test-secret", observedKey)
                assertFalse(observedBody!!.contains("test-secret"))
            } finally { server.stop(0) }
        }
    }

    @Test fun cancellationClosesEachProtocolStream() = runBlocking {
        for (protocol in ModelProtocol.entries) {
            val received = CountDownLatch(1)
            val release = CountDownLatch(1)
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                exchange.requestBody.close(); exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write(":ping\n\n".toByteArray()); exchange.responseBody.flush()
                received.countDown()
                release.await(5, TimeUnit.SECONDS)
                exchange.close()
            }
            server.start()
            try {
                val job = launch(Dispatchers.Default) { localClient(server).stream(profile(protocol), "test", request).toList() }
                assertTrue(received.await(5, TimeUnit.SECONDS))
                withTimeout(3000) { job.cancelAndJoin() }
                assertTrue(job.isCancelled)
            } finally { release.countDown(); server.stop(0) }
        }
    }

    @Test fun httpAuthenticationErrorsNeverExposeResponseBody() = runBlocking {
        for (protocol in ModelProtocol.entries) {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                exchange.sendResponseHeaders(401, 0)
                exchange.responseBody.use { it.write("private-key-echo".toByteArray()) }
            }
            server.start()
            try {
                try { localClient(server).stream(profile(protocol), "test", request).toList(); fail("must fail") }
                catch (error: ModelException) { assertEquals(ModelError.Authentication, error.error); assertFalse(error.toString().contains("private-key-echo")) }
            } finally { server.stop(0) }
        }
    }

    // -- Functions

    private fun localClient(server: HttpServer) = HttpModelClient(endpoint = { "http://127.0.0.1:${server.address.port}/" })

    private fun textEvents(protocol: ModelProtocol): List<String> = when (protocol) {
        ModelProtocol.OpenAI -> listOf("""{"choices":[{"index":0,"delta":{"content":"你好"},"finish_reason":null}]}""",
            """{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":2}}""", "[DONE]")
        ModelProtocol.Anthropic -> listOf("""{"type":"message_start","message":{"usage":{"input_tokens":3}}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"你好"}}""",
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":2}}""", """{"type":"message_stop"}""")
        ModelProtocol.Gemini -> listOf("""{"candidates":[{"content":{"parts":[{"text":"你好"}],"role":"model"}}]}""",
            """{"candidates":[{"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":3,"candidatesTokenCount":2}}""")
    }

    private fun toolEvents(protocol: ModelProtocol): List<String> = when (protocol) {
        ModelProtocol.OpenAI -> listOf("""{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call","function":{"name":"echo","arguments":"{\"value\":"}}]}}]}""",
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"OK\"}"}}]},"finish_reason":"tool_calls"}]}""", "[DONE]")
        ModelProtocol.Anthropic -> listOf("""{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"call","name":"echo","input":{}}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"value\":\"OK\"}"}}""",
            """{"type":"message_delta","delta":{"stop_reason":"tool_use"}}""", """{"type":"message_stop"}""")
        ModelProtocol.Gemini -> listOf("""{"candidates":[{"content":{"parts":[{"functionCall":{"id":"call","name":"echo","args":{"value":"OK"}},"thoughtSignature":"signature-value"}]},"finishReason":"STOP"}]}""")
    }
}
