package com.xnote.app.domain.agent

import com.xnote.app.data.agent.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

// -- Type Definitions

class ModelCatalogTest {
    // -- Functions

    private fun model(date: String, output: String = "text", context: Int = 128000, limit: Int = 4096, status: String = "") =
        """{"name":"Test","release_date":"$date","status":"$status","modalities":{"input":["text"],"output":["$output"]},"limit":{"context":$context,"output":$limit}}"""

    @Test fun dynamicProvidersUseNativeIdsAndProtocolsWithMainstreamFirst() {
        val result = parseModelCatalog("""{
            "new-vendor":{"name":"New Vendor","npm":"@ai-sdk/openai-compatible","api":"https://new.example/v2","models":{"test":${model("2026-09-23")}}},
            "anthropic":{"name":"Anthropic","npm":"@ai-sdk/anthropic","models":{"claude-test":${model("2026-09-21")}}},
            "openai":{"name":"OpenAI","npm":"@ai-sdk/openai","models":{"older":${model("2026-01-01")},"newer":${model("2026-09-23")},"image":${model("2026-09-23", "image")},"gone":${model("2026-09-23", status = "deprecated")}}}
        }""", """[{"slug":"openai","api_base_url":"https://openai.example/v1"},{"slug":"anthropic","api_base_url":"https://anthropic.example"}]""")
        assertEquals(listOf("newer", "older", "claude-test", "test"), result.map { it.id })
        assertEquals(ModelProtocol.Anthropic, result[2].provider.protocol)
        assertEquals("https://anthropic.example/v1", result[2].provider.baseUrl)
        assertEquals("new-vendor", result.last().provider.id)
        assertEquals("https://new.example/v2", result.last().provider.baseUrl)
    }

    @Test fun aliasesSupplyMissingAddressAndMetadataChangesAreApplied() {
        val body = """{"google":{"name":"Google","npm":"@ai-sdk/google","models":{"gemini-test":${model("2026-09-23")}}}}"""
        val result = parseModelCatalog(body, """[{"slug":"google-ai-studio","aliases":["google"],"api_base_url":"https://google.example"}]""")
        assertEquals("https://google.example/v1beta", result.single().provider.baseUrl)
        assertEquals(ModelProtocol.Gemini, result.single().provider.protocol)
        val changed = parseModelCatalog(body, """[{"slug":"google","api_base_url":"https://new.google.example/v3"}]""")
        assertEquals("https://new.google.example/v3", changed.single().provider.baseUrl)
    }

    @Test fun budgetsRespectCatalogAndLocalBounds() {
        val result = parseModelCatalog("""{"deepseek":{"name":"DeepSeek","npm":"@ai-sdk/openai-compatible","api":"https://deepseek.example","models":{"large":${model("2026-09-23", context = 3000000)},"small":${model("2026-09-22", context = 4096)},"limited":${model("2026-09-21", limit = 1024)}}}}""", "[]")
        assertEquals(2000000, result[0].contextTokens)
        assertEquals(3071, result[1].outputTokens)
        assertEquals(1024, result[2].outputTokens)
        result.forEach { ModelProfile(it.id, name = it.name, protocol = it.provider.protocol, baseUrl = it.provider.baseUrl,
            modelId = it.id, contextTokens = it.contextTokens, outputTokens = it.outputTokens).validate() }
    }

    @Test fun incompleteUnsupportedOrUnsafeProviderNeverGetsInventedConfiguration() {
        for ((npm, url) in listOf("@ai-sdk/unknown" to "https://unknown.example", "@ai-sdk/google-vertex" to "https://vertex.example",
            "@ai-sdk/openai-compatible" to "http://unsafe.example", "@ai-sdk/openai-compatible" to "https://user:secret@unsafe.example")) {
            assertThrows(IllegalArgumentException::class.java) {
                parseModelCatalog("""{"test":{"npm":"$npm","api":"$url","models":{"test":${model("2026-09-23")}}}}""", "[]")
            }
        }
        assertThrows(IllegalArgumentException::class.java) { parseModelCatalog("{}", "[]") }
        assertThrows(Exception::class.java) { parseModelCatalog("not json", "[]") }
    }

    @Test fun officialCompatibleProvidersUseTheirSupportedTokenParameter() {
        for (id in listOf("openai", "deepseek", "mistral", "new-vendor")) {
            val profile = ModelProfile("test", name = "Test", protocol = ModelProtocol.OpenAI, modelId = "test", providerId = id)
            val json = modelRequestJson(profile, ModelRequest("system", emptyList()))
            assertTrue(json.containsKey(if (id == "openai") "max_completion_tokens" else "max_tokens"))
            assertEquals(id == "openai", json.containsKey("store"))
            if (id == "mistral") assertFalse(json.containsKey("stream_options"))
        }
    }
}
