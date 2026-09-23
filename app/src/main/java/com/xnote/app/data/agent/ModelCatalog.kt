package com.xnote.app.data.agent

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// -- Type Definitions

data class CatalogModel(val id: String, val name: String, val created: Long, val contextTokens: Int, val outputTokens: Int) {
    val vendor: String get() = id.substringBefore('/').removePrefix("~")
}

fun interface ModelCatalog {
    suspend fun load(): List<CatalogModel>
}

class OpenRouterModelCatalog : ModelCatalog {
    // -- State and Variables

    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    // -- Functions

    override suspend fun load(): List<CatalogModel> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url("$OpenRouterBaseUrl/models").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(IOException("模型目录加载失败，请重试。")) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        check(it.isSuccessful)
                        val source = it.body.source()
                        check(!source.request(8L * 1024 * 1024 + 1))
                        parseModelCatalog(source.readUtf8())
                    }
                }
                result.fold({ continuation.resume(it) }, { continuation.resumeWithException(IOException("模型目录加载失败，请重试。")) })
            }
        })
    }
}

// -- Constants

const val OpenRouterBaseUrl = "https://openrouter.ai/api/v1"
val CatalogPriorityVendors = listOf("openai", "anthropic", "deepseek", "z-ai", "google", "x-ai", "moonshotai", "minimax", "qwen", "bytedance-seed")

// -- Functions

fun parseModelCatalog(body: String): List<CatalogModel> {
    val models = Json.parseToJsonElement(body).jsonObject.getValue("data").jsonArray.mapNotNull { element ->
        val value = element.jsonObject
        val id = value["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val architecture = value["architecture"]?.jsonObject ?: return@mapNotNull null
        fun hasText(key: String) = architecture[key]?.jsonArray?.any { it.jsonPrimitive.content == "text" } == true
        if (!hasText("input_modalities") || !hasText("output_modalities") || '/' !in id || id.any(Char::isWhitespace)) return@mapNotNull null
        val context = value["context_length"]?.jsonPrimitive?.intOrNull?.coerceAtMost(2_000_000) ?: return@mapNotNull null
        val limit = (value["top_provider"] as? JsonObject)?.get("max_completion_tokens")?.jsonPrimitive?.intOrNull ?: 4096
        val output = minOf(4096, limit, context - 1025)
        if (context < 4096 || output < 256) return@mapNotNull null
        CatalogModel(id, value["name"]?.jsonPrimitive?.content ?: id,
            value["created"]?.jsonPrimitive?.longOrNull ?: 0, context, output)
    }.distinctBy { it.id }.sortedWith(compareBy<CatalogModel> {
        CatalogPriorityVendors.indexOf(it.vendor).takeIf { rank -> rank >= 0 } ?: Int.MAX_VALUE
    }.thenBy { it.vendor }.thenByDescending { it.created }.thenBy { it.id })
    require(models.isNotEmpty())
    return models
}
