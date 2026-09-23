package com.xnote.app.data.agent

import com.xnote.app.domain.agent.ModelLimits
import com.xnote.app.domain.agent.ModelProtocol
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// -- Type Definitions

data class CatalogProvider(val id: String, val name: String, val protocol: ModelProtocol, val baseUrl: String)

data class CatalogModel(val id: String, val name: String, val releaseDate: String,
    val contextTokens: Int, val outputTokens: Int, val provider: CatalogProvider)

fun interface ModelCatalog {
    suspend fun load(): List<CatalogModel>
}

class OfficialModelCatalog : ModelCatalog {
    // -- State and Variables

    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    // -- Functions

    override suspend fun load(): List<CatalogModel> = coroutineScope {
        val models = async { fetch("https://models.dev/api.json") }
        val providers = async { fetch("https://raw.githubusercontent.com/foisalislambd/all-llm-provider-list/main/data/providers.json") }
        parseModelCatalog(models.await(), providers.await())
    }

    private suspend fun fetch(url: String): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(IOException("模型目录加载失败，请重试。")) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        check(it.isSuccessful)
                        val source = it.body.source()
                        check(!source.request(8L * 1024 * 1024 + 1))
                        source.readUtf8()
                    }
                }
                result.fold({ continuation.resume(it) }, { continuation.resumeWithException(IOException("模型目录加载失败，请重试。")) })
            }
        })
    }
}

// -- Constants

private val ProviderPriority = listOf("openai", "anthropic", "google", "deepseek", "zhipuai", "zai", "moonshotai", "minimax", "alibaba", "xai")

// -- Functions

fun parseModelCatalog(body: String, registryBody: String): List<CatalogModel> {
    val catalog = Json.parseToJsonElement(body).jsonObject
    val registry = Json.parseToJsonElement(registryBody).jsonArray.map { it.jsonObject }
    val models = catalog.flatMap { (providerId, element) ->
        val data = element as? JsonObject ?: return@flatMap emptyList()
        val protocol = when (data["npm"]?.jsonPrimitive?.content) {
            "@ai-sdk/anthropic" -> ModelProtocol.Anthropic
            "@ai-sdk/google" -> ModelProtocol.Gemini
            "@ai-sdk/openai", "@ai-sdk/openai-compatible", "@ai-sdk/deepseek", "@ai-sdk/groq", "@ai-sdk/xai", "@ai-sdk/mistral", "@ai-sdk/perplexity" -> ModelProtocol.OpenAI
            else -> return@flatMap emptyList()
        }
        val registryEntry = registry.firstOrNull { it["slug"]?.jsonPrimitive?.content == providerId }
            ?: registry.firstOrNull { row -> (row["aliases"] as? JsonArray)?.any { it.jsonPrimitive.content == providerId } == true }
        val rawUrl = data["api"]?.jsonPrimitive?.contentOrNull
            ?: registryEntry?.get("api_base_url")?.jsonPrimitive?.contentOrNull ?: return@flatMap emptyList()
        val baseUrl = catalogBaseUrl(rawUrl, protocol) ?: return@flatMap emptyList()
        val provider = CatalogProvider(providerId, data["name"]?.jsonPrimitive?.content ?: providerId, protocol, baseUrl)
        val entries = data["models"] as? JsonObject ?: return@flatMap emptyList()
        entries.mapNotNull { (id, value) ->
            val model = value as? JsonObject ?: return@mapNotNull null
            val modalities = model["modalities"] as? JsonObject ?: return@mapNotNull null
            fun hasText(key: String) = (modalities[key] as? JsonArray)?.any { it.jsonPrimitive.content == "text" } == true
            if (id.isBlank() || id.any(Char::isWhitespace) || !hasText("input") || !hasText("output") ||
                model["status"]?.jsonPrimitive?.content == "deprecated") return@mapNotNull null
            // Per-model overrides require a different adapter or endpoint; do not silently route through the provider default.
            if ((model["provider"] as? JsonObject)?.isNotEmpty() == true) return@mapNotNull null
            if (providerId == "openai" && ("codex" in id || "-pro" in id || "deep-research" in id)) return@mapNotNull null
            val limits = model["limit"] as? JsonObject ?: return@mapNotNull null
            val context = limits["context"]?.jsonPrimitive?.intOrNull?.coerceAtMost(2_000_000) ?: return@mapNotNull null
            val output = minOf(ModelLimits.DefaultOutputTokens, limits["output"]?.jsonPrimitive?.intOrNull ?: 0,
                context - ModelLimits.ToolReserveTokens - 1)
            if (context < 4096 || output < 256) return@mapNotNull null
            CatalogModel(id, model["name"]?.jsonPrimitive?.content ?: id,
                model["release_date"]?.jsonPrimitive?.content ?: "", context, output, provider)
        }
    }.sortedWith(compareBy<CatalogModel> {
        ProviderPriority.indexOf(it.provider.id.removeSuffix("-cn")).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
    }.thenBy { it.provider.name }.thenByDescending { it.releaseDate }.thenBy { it.id })
    require(models.isNotEmpty())
    return models
}

private fun catalogBaseUrl(raw: String, protocol: ModelProtocol): String? {
    val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null ||
        raw.contains('$') || raw.contains('{') || raw.contains('}')) return null
    val root = raw.trim().trimEnd('/')
    return if (uri.path.isNullOrEmpty() || uri.path == "/") when (protocol) {
        ModelProtocol.Anthropic -> "$root/v1"
        ModelProtocol.Gemini -> "$root/v1beta"
        ModelProtocol.OpenAI -> root
    } else root
}
