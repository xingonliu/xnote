package com.xnote.app.data.agent

import com.xnote.app.domain.agent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// -- Type Definitions

interface ModelClient {
    fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest): Flow<ModelEvent>
}

class HttpModelClient(
    private val client: OkHttpClient = defaultModelHttpClient(),
    private val endpoint: (String) -> String = { it },
) : ModelClient {
    // -- Functions

    override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest): Flow<ModelEvent> = channelFlow {
        profile.validate()
        if (apiKey.isBlank()) throw ModelException(ModelError.MissingCredential)
        val activeCall = AtomicReference<Call?>()
        val worker = launch(Dispatchers.IO) {
            try {
                val suffix = when (profile.protocol) {
                    ModelProtocol.OpenAI -> "/chat/completions"
                    ModelProtocol.Anthropic -> "/messages"
                    ModelProtocol.Gemini -> "/models/${URLEncoder.encode(profile.modelId.removePrefix("models/"), "UTF-8")}:streamGenerateContent?alt=sse"
                }
                val builder = Request.Builder().url(endpoint(profile.baseUrl.trimEnd('/') + suffix))
                    .header("Accept", "text/event-stream")
                    .post(modelRequestJson(profile, request).toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                when (profile.protocol) {
                    ModelProtocol.OpenAI -> builder.header("Authorization", "Bearer $apiKey")
                    ModelProtocol.Anthropic -> builder.header("x-api-key", apiKey).header("anthropic-version", "2023-06-01")
                    ModelProtocol.Gemini -> builder.header("x-goog-api-key", apiKey)
                }
                val call = client.newCall(builder.build())
                activeCall.set(call)
                ensureActive()
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val fallback = when (response.code) {
                        401, 403 -> ModelError.Authentication
                        429 -> ModelError.Quota
                        in 500..599 -> ModelError.Service
                        else -> ModelError.InvalidRequest
                        }
                        val error = runCatching { Json.parseToJsonElement(response.peekBody(65536).string()).jsonObject.obj("error") }.getOrNull()
                        throw ModelException(modelServiceError(error, fallback))
                    }
                    val decoder = ModelStreamDecoder(profile.protocol)
                    response.body.charStream().buffered().use { reader ->
                        readModelSse(reader) { data ->
                            ensureActive()
                            decoder.accept(data).forEach { send(it) }
                        }
                    }
                    decoder.end().forEach { send(it) }
                }
                close()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                close(when (error) {
                    is ModelException -> error
                    is java.io.InterruptedIOException -> ModelException(ModelError.Timeout)
                    is java.io.IOException -> ModelException(ModelError.Network)
                    else -> ModelException(ModelError.Protocol)
                })
            } finally { activeCall.getAndSet(null)?.cancel() }
        }
        awaitClose { activeCall.getAndSet(null)?.cancel(); worker.cancel() }
    }
}

// -- Functions

private fun defaultModelHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(ModelLimits.ConnectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
    .readTimeout(ModelLimits.ReadTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
    .writeTimeout(ModelLimits.ConnectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
    .callTimeout(ModelLimits.RequestTimeoutMs, TimeUnit.MILLISECONDS)
    .retryOnConnectionFailure(false)
    .followRedirects(false)
    .followSslRedirects(false)
    .build()
