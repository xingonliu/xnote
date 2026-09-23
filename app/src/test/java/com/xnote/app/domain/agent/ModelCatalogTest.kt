package com.xnote.app.domain.agent

import com.xnote.app.data.agent.parseModelCatalog
import org.junit.Assert.*
import org.junit.Test

// -- Type Definitions

class ModelCatalogTest {
    // -- Functions

    private fun model(id: String, created: Int, output: String = "text", context: Int = 128000, limit: String = "null") =
        """{"id":"$id","name":"$id","created":$created,"context_length":$context,"architecture":{"input_modalities":["text"],"output_modalities":["$output"]},"top_provider":{"max_completion_tokens":$limit}}"""

    @Test fun prioritizesVendorsSortsLatestAndFiltersNonTextModels() {
        val entries = listOf(model("google/new", 20), model("openai/old", 1), model("openai/new", 3),
            model("openai/image", 4, "image"), model("openai/new", 3), model("qwen/small", 1, context = 1000))
        val result = parseModelCatalog("""{"data":[${entries.joinToString()}]}""")
        assertEquals(listOf("openai/new", "openai/old", "google/new"), result.map { it.id })
    }

    @Test fun budgetsRespectCatalogAndLocalBounds() {
        val entries = listOf(model("openai/large", 3, context = 3_000_000), model("openai/small", 2, context = 4096),
            model("openai/limited", 1, limit = "1024"))
        val result = parseModelCatalog("""{"data":[${entries.joinToString()}]}""")
        assertEquals(2_000_000, result[0].contextTokens)
        assertEquals(3071, result[1].outputTokens)
        assertEquals(1024, result[2].outputTokens)
        result.forEach { ModelProfile(it.id, name = it.name, protocol = ModelProtocol.OpenAI, modelId = it.id,
            contextTokens = it.contextTokens, outputTokens = it.outputTokens).validate() }
    }

    @Test fun emptyOrMalformedCatalogFailsInsteadOfInventingModels() {
        assertThrows(IllegalArgumentException::class.java) { parseModelCatalog("""{"data":[]}""") }
        assertThrows(Exception::class.java) { parseModelCatalog("not json") }
    }
}
