package com.xnote.app.data.agent

import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.text.FtsIndexText
import kotlinx.serialization.json.*

// -- Type Definitions

private data class SafeHistoryMessage(val row: AgentMessageEntity, val text: String, val sources: List<AgentMessageSource>)

class AgentHistoryMemoryStore(private val database: XNoteDatabase) {
    // -- Functions

    suspend fun execute(run: AgentRunEntity, call: ModelToolCall): AgentToolResult.Finished {
        val events = database.agent().toolEvents(run.id).filter { it.callId != call.id && it.name in setOf("memory_search", "memory_read") }
        val limit = if (call.name == "memory_search") AgentMemoryToolLimits.SearchCalls else AgentMemoryToolLimits.ReadCalls
        if (events.count { it.name == call.name } >= limit) return error(call, "memory_call_limit")
        val result = when (call.name) {
            "memory_search" -> search(run, call, Json.decodeFromJsonElement(call.arguments))
            "memory_read" -> read(run, call, Json.decodeFromJsonElement(call.arguments))
            else -> error(call, "unsupported_tool")
        }
        val cost = estimatedAgentTokens(result.result.content)
        val used = events.filter { it.status == AgentToolStatus.Committed }.sumOf { estimatedAgentTokens(it.resultJson.orEmpty()).toLong() }
        if (cost > AgentMemoryToolLimits.PageTokens || used + cost > AgentMemoryToolLimits.ReturnedTokens) return error(call, "memory_token_limit")
        if (events.any { it.name == call.name && it.argumentsJson == call.arguments.toString() && it.resultJson == result.result.content }) return error(call, "duplicate_memory_request")
        return result
    }

    private suspend fun safeMessages(run: AgentRunEntity): List<SafeHistoryMessage> {
        val closedIds = database.memory().closedSegments().map { it.id }.toSet()
        val safe = mutableListOf<SafeHistoryMessage>()
        val notes = AgentNoteStore(database)
        for (message in database.agent().messages().filter { it.segmentId in closedIds && it.runId != null && it.status == AgentMessageStatus.Complete }) {
            val originalRun = database.agent().run(requireNotNull(message.runId)) ?: continue
            if (originalRun.errorCode == "history_removed") continue
            if (!AgentMemoryProvenance(database).canUse(message.id, run)) continue
            val sources = Json.decodeFromString<List<AgentMessageSource>>(message.sourcesJson)
            if (!notes.canUseSources(run, sources)) continue
            val text = if (message.role == AgentMessageRole.Tool) database.agent().toolEvents(originalRun.id)
                .filter { notes.canUseSources(run, Json.decodeFromString(it.sourcesJson)) }
                .joinToString("\n") { "${it.name}: ${it.status.name}" } else message.text
            if (text.isNotBlank()) safe += SafeHistoryMessage(message, text, sources)
        }
        return safe
    }

    private suspend fun search(run: AgentRunEntity, call: ModelToolCall, args: AgentMemorySearchArguments): AgentToolResult.Finished {
        require(args.query.isNotBlank() && args.query.length <= AgentNoteLimits.MaxQueryCharacters && args.limit in 1..10)
        require((args.startTime ?: 0) <= (args.endTime ?: Long.MAX_VALUE))
        val safe = safeMessages(run).filter { it.row.createdAtEpochMs >= (args.startTime ?: 0) && it.row.createdAtEpochMs <= (args.endTime ?: Long.MAX_VALUE) }
        val query = FtsIndexText.matchQuery(args.query) ?: return result(call, buildJsonObject { putJsonArray("items") {} }, emptyList())
        val messageHits = database.memory().searchMessages(query, safe.map { it.row.id }).toSet()
        val summaries = AgentEpisodeStore(database).safeEpisodes(run).filter { it.segmentId in safe.map { row -> row.row.segmentId } }
        val episodeHits = database.memory().search(query, summaries.map { it.segmentId }).toSet()
        val groups = safe.groupBy { it.row.segmentId }.filter { (id, rows) -> id in episodeHits || rows.any { it.row.id in messageHits } }
            .toList().sortedByDescending { it.second.last().row.sequence }
        val identity = agentSourceHash(Json.encodeToString(args.copy(cursor = null)))
        val projection = agentSourceHash(safe.joinToString { it.row.id } + summaries.joinToString { it.sourceHash })
        val offset = cursor(args.cursor, identity, projection)?.offset ?: 0
        require(offset in 0..groups.size)
        val page = groups.drop(offset).take(args.limit)
        val sourceMessages = mutableListOf<SafeHistoryMessage>()
        val payload = buildJsonObject {
            put("trust", "derived")
            put("items", buildJsonArray {
                page.forEach { (id, rows) ->
                    val summary = summaries.find { it.segmentId == id }?.let { Json.decodeFromString<AgentEpisodeSummary>(it.summaryJson) }
                    val hit = rows.firstOrNull { it.row.id in messageHits } ?: rows.first()
                    sourceMessages += rows
                    add(buildJsonObject {
                        put("episodeId", id); put("startTime", rows.first().row.createdAtEpochMs); put("endTime", rows.last().row.createdAtEpochMs)
                        put("title", summary?.title ?: "历史对话"); put("summary", summary?.summary?.take(300).orEmpty())
                        val match = hit.text.indexOf(args.query, ignoreCase = true).coerceAtLeast(0)
                        put("excerpt", hit.text.drop((match - 50).coerceAtLeast(0)).take(240))
                        put("reason", if (id in episodeHits) "summary_and_keywords" else "message_text")
                        put("readable", true)
                        put("noteIds", JsonArray(rows.flatMap { it.sources }.map { it.noteId }.distinct().map(::JsonPrimitive)))
                    })
                }
            })
            put("cursor", if (offset + page.size < groups.size) JsonPrimitive(Json.encodeToString(AgentMemoryCursor(identity, projection, offset + page.size))) else JsonNull)
        }
        return result(call, payload, sourceMessages)
    }

    private suspend fun read(run: AgentRunEntity, call: ModelToolCall, args: AgentMemoryReadArguments): AgentToolResult.Finished {
        require(args.episodeId.isNotBlank() && args.limit in 1..AgentMemoryToolLimits.ReadCharacters)
        val safe = safeMessages(run).filter { it.row.segmentId == args.episodeId }
        if (safe.isEmpty()) return error(call, "memory_unavailable")
        val identity = agentSourceHash(args.episodeId)
        val projection = agentSourceHash(safe.joinToString { it.row.id + it.text })
        val position = cursor(args.cursor, identity, projection) ?: AgentMemoryCursor(identity, projection, 0)
        require(position.offset in safe.indices)
        var index = position.offset
        var character = position.characterOffset
        var remaining = args.limit
        var tokenRemaining = AgentMemoryToolLimits.PageTokens - 1024
        val sourceMessages = mutableListOf<SafeHistoryMessage>()
        val items = buildJsonArray {
            while (index < safe.size && remaining > 0 && tokenRemaining > 512 && sourceMessages.size < 20) {
                val message = safe[index]
                require(character in 0..message.text.length)
                var chunk = message.text.drop(character).take(remaining)
                while (estimatedAgentTokens(JsonPrimitive(chunk).toString()) + 400 > tokenRemaining && chunk.isNotEmpty()) chunk = chunk.take(chunk.length * 3 / 4)
                if (chunk.lastOrNull()?.isHighSurrogate() == true && message.text.getOrNull(character + chunk.length)?.isLowSurrogate() == true) chunk = chunk.dropLast(1)
                if (chunk.isEmpty()) break
                val item = buildJsonObject {
                    put("id", message.row.id); put("sequence", message.row.sequence); put("role", message.row.role.name)
                    put("createdAt", message.row.createdAtEpochMs); put("status", message.row.status.name)
                    put("characterOffset", character); put("text", chunk)
                }
                add(item)
                tokenRemaining -= estimatedAgentTokens(item.toString())
                sourceMessages += message
                remaining -= chunk.length
                character += chunk.length
                if (character == message.text.length) { index++; character = 0 }
            }
        }
        return result(call, buildJsonObject {
            put("episodeId", args.episodeId); put("trust", "historical_data"); put("messages", items)
            put("cursor", if (index < safe.size) JsonPrimitive(Json.encodeToString(AgentMemoryCursor(identity, projection, index, character))) else JsonNull)
        }, sourceMessages)
    }

    private fun cursor(value: String?, identity: String, projection: String): AgentMemoryCursor? = value?.let {
        require(it.length <= 1024)
        Json.decodeFromString<AgentMemoryCursor>(it).also { position -> require(position.identity == identity && position.projection == projection && position.offset >= 0 && position.characterOffset >= 0) }
    }

    private fun result(call: ModelToolCall, payload: JsonObject, messages: List<SafeHistoryMessage>) = AgentToolResult.Finished(
        ModelToolResult(call.id, call.name, payload.toString()), messages.flatMap { it.sources }.distinct(), messages.map { it.row.id }.distinct())

    private fun error(call: ModelToolCall, code: String) = result(call, buildJsonObject { put("error", code) }, emptyList())
}
