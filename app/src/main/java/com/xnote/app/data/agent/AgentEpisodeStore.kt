package com.xnote.app.data.agent

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.text.FtsIndexText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.UUID

// -- Type Definitions

data class AgentEpisodeRecall(val text: String, val sources: List<AgentMessageSource>)
private data class EpisodeInput(val text: String, val sources: List<AgentMessageSource>, val startId: String, val endId: String)

class AgentEpisodeStore(private val database: XNoteDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    // -- State and Variables

    private val notes = AgentNoteStore(database)
    private val processor = Mutex()

    // -- Functions

    suspend fun close(segment: AgentSegmentEntity, reason: String) = transaction {
        database.agent().saveSegment(segment.copy(closedAtEpochMs = clock(), closeReason = reason))
        freeze(segment.id)
    }

    suspend fun sweep() = transaction {
        database.memory().closedSegments().forEach { freeze(it.id) }
    }

    private suspend fun freeze(segmentId: String) {
        if (database.memory().job(segmentId) != null) return
        val messages = eligible(segmentId)
        if (messages.isEmpty()) return
        val run = database.agent().run(requireNotNull(messages.last().runId)) ?: return
        database.memory().saveJob(AgentEpisodeJobEntity(segmentId, Json.encodeToString(messages.map { it.id }),
            fingerprint(messages), run.profileId, run.profileVersion, AgentMemoryLimits.PromptVersion))
    }

    private suspend fun eligible(segmentId: String): List<AgentMessageEntity> {
        val result = mutableListOf<AgentMessageEntity>()
        for (message in database.agent().messages().filter { it.segmentId == segmentId && it.runId != null && it.status == AgentMessageStatus.Complete }) {
            val run = database.agent().run(requireNotNull(message.runId)) ?: continue
            if (run.status !in setOf(AgentRunStatus.Complete, AgentRunStatus.Failed, AgentRunStatus.Cancelled) || run.errorCode == "history_removed") continue
            if (message.role in setOf(AgentMessageRole.User, AgentMessageRole.Assistant, AgentMessageRole.Tool)) result += message
        }
        return result
    }

    private suspend fun fingerprint(messages: List<AgentMessageEntity>): String = agentSourceHash(buildString {
        messages.forEach { message ->
            append(Json.encodeToString(listOf(message.id, message.text, message.sourcesJson, message.modelJson.orEmpty(), message.status.name)))
        }
        messages.mapNotNull { it.runId }.distinct().forEach { runId ->
            database.agent().toolEvents(runId).forEach { event ->
                append(Json.encodeToString(listOf(event.id, event.name, event.status.name, event.sourcesJson)))
            }
        }
    })

    private suspend fun input(job: AgentEpisodeJobEntity, accessRun: AgentRunEntity? = null): EpisodeInput? {
        val ids = Json.decodeFromString<List<String>>(job.messageIdsJson)
        val messages = eligible(job.segmentId).filter { it.id in ids }
        if (messages.map { it.id } != ids || fingerprint(messages) != job.sourceHash) return null
        val sourceRun = accessRun ?: database.agent().run(requireNotNull(messages.last().runId))?.copy(segmentId = "derived-memory", grantJson = null) ?: return null
        val safe = mutableListOf<AgentMessageEntity>()
        val sources = mutableListOf<AgentMessageSource>()
        for (message in messages) {
            val provenance = Json.decodeFromString<List<AgentMessageSource>>(message.sourcesJson)
            if (!notes.canUseSources(sourceRun, provenance)) continue
            safe += message
            sources += provenance
        }
        if (safe.isEmpty()) return null
        val text = buildJsonObject {
            put("sourceMessageStartId", ids.first()); put("sourceMessageEndId", ids.last())
            put("messages", buildJsonArray {
                safe.forEach { message -> add(buildJsonObject {
                    put("id", message.id); put("role", message.role.name)
                    // Tool results are represented by committed status, never by copied note bodies.
                    if (message.role != AgentMessageRole.Tool) put("text", message.text)
                }) }
            })
            put("tools", buildJsonArray {
                messages.mapNotNull { it.runId }.distinct().forEach { runId ->
                    database.agent().toolEvents(runId).forEach { event ->
                        val provenance = Json.decodeFromString<List<AgentMessageSource>>(event.sourcesJson)
                        if (notes.canUseSources(sourceRun, provenance)) {
                            sources += provenance
                            add(buildJsonObject { put("tool", event.name); put("status", event.status.name) })
                        }
                    }
                }
            })
        }.toString()
        return EpisodeInput(text, sources.distinct(), ids.first(), ids.last())
    }

    /** Returns true when work remains retryable; calls and reservations survive cancellation and crashes. */
    suspend fun process(profiles: ModelProfileStore, client: ModelClient): Boolean = processor.withLock {
        sweep()
        var retry = false
        for (segment in database.memory().closedSegments()) {
            currentCoroutineContext().ensureActive()
            val job = database.memory().job(segment.id) ?: continue
            if (job.status in setOf("complete", "invalid", "blocked") || job.attempts >= AgentMemoryLimits.MaxAttempts) continue
            if (job.retryAfter > clock() || job.leaseUntil > clock()) { retry = true; continue }
            if (database.agent().unfinishedRuns().isNotEmpty() || database.agent().pendingQueue().isNotEmpty()) return@withLock true
            val profile = profiles.list().singleOrNull { it.id == job.profileId && it.version == job.profileVersion && it.enabled }
            if (profile == null) {
                database.memory().saveJob(job.copy(status = "blocked", error = "model_configuration_changed"))
                continue
            }
            val prepared = transaction { input(job) }
            if (prepared == null) { invalidate(job.segmentId); continue }
            val tokenCost = estimatedAgentTokens(prepared.text).toLong() + estimatedAgentTokens(AgentEpisodePrompt)
            if (tokenCost + profile.outputTokens + ModelLimits.ToolReserveTokens > profile.contextTokens) {
                database.memory().saveJob(job.copy(status = "blocked", error = "source_budget")); continue
            }
            val usage = AgentDerivedUsageEntity(UUID.randomUUID().toString(), "episode", clock(), tokenCost + profile.outputTokens)
            val claimed = transaction {
                val latest = database.memory().job(job.segmentId)
                val daily = database.memory().usage(clock() - 24L * 60 * 60 * 1000)
                if (latest != job || database.agent().unfinishedRuns().isNotEmpty() || database.agent().pendingQueue().isNotEmpty() ||
                    daily.size >= AgentMemoryLimits.DailyCalls || daily.sumOf { it.reservedTokens } + usage.reservedTokens > AgentMemoryLimits.DailyTokens) false
                else {
                    database.memory().saveJob(job.copy(status = "running", attempts = job.attempts + 1, leaseUntil = clock() + AgentMemoryLimits.LeaseMs))
                    database.memory().saveUsage(usage)
                    true
                }
            }
            if (!claimed) { retry = true; continue }
            try {
                val secret = profiles.credential(profile)
                // Project once more immediately before dispatch, after credentials and claim.
                require(transaction { input(job) } == prepared) { "source_changed" }
                val output = StringBuilder()
                var finished = false
                client.stream(profile.copy(outputTokens = minOf(profile.outputTokens, AgentMemoryLimits.MaxSummaryTokens)), secret,
                    ModelRequest(AgentEpisodePrompt, listOf(ModelMessage(AgentMessageRole.User, prepared.text)))).collect { event ->
                    if (database.agent().unfinishedRuns().isNotEmpty()) throw CancellationException("foreground_priority")
                    when (event) {
                        is ModelEvent.Text -> { output.append(event.value); require(output.length <= AgentMemoryLimits.MaxSummaryTokens * 4) }
                        is ModelEvent.Finished -> { require(event.reason == ModelFinish.Complete); finished = true }
                        is ModelEvent.ToolCall -> error("summary_tools_forbidden")
                        is ModelEvent.Usage -> {
                            val recordedUsage = usage.copy(inputTokens = event.inputTokens, outputTokens = event.outputTokens,
                                reservedTokens = maxOf(usage.reservedTokens, (event.inputTokens ?: 0) + (event.outputTokens ?: 0)))
                            database.memory().saveUsage(recordedUsage)
                        }
                        is ModelEvent.NativeParts -> Unit
                    }
                }
                require(finished)
                val summary = parseAgentEpisode(output.toString(), prepared.startId, prepared.endId)
                transaction {
                    require(input(job) == prepared) { "source_changed" }
                    val currentProfile = profiles.list().singleOrNull { it.id == profile.id }
                    require(currentProfile?.version == profile.version)
                    database.memory().saveEpisode(AgentEpisodeEntity(job.segmentId, Json.encodeToString(summary), Json.encodeToString(prepared.sources),
                        job.sourceHash, profile.id, profile.version, profile.protocol.name, profile.modelId, job.promptVersion, clock()))
                    database.memory().deleteFts(job.segmentId)
                    database.memory().insertFts(AgentEpisodeFtsEntity(segmentId = job.segmentId, text = FtsIndexText.prepare(summary.title + " " + summary.summary + " " + summary.keywords.joinToString(" "))))
                    database.memory().saveJob(job.copy(status = "complete", attempts = job.attempts + 1))
                }
            } catch (cancelled: CancellationException) {
                // An expiring lease makes process death and WorkManager cancellation recoverable.
                throw cancelled
            } catch (error: Exception) {
                transaction {
                    val latest = database.memory().job(job.segmentId)
                    if (latest?.status != "invalid") database.memory().saveJob(job.copy(status = "failed", attempts = job.attempts + 1,
                        retryAfter = clock() + AgentMemoryLimits.RetryDelayMs, error = (error as? ModelException)?.error?.name ?: "invalid_summary"))
                }
                retry = true
            }
        }
        retry
    }

    suspend fun invalidate(segmentId: String) = transaction {
        database.memory().deleteFts(segmentId)
        database.memory().deleteEpisode(segmentId)
        database.memory().job(segmentId)?.let { database.memory().saveJob(it.copy(status = "invalid", leaseUntil = 0)) }
    }

    suspend fun recall(run: AgentRunEntity, query: String): List<AgentEpisodeRecall> {
        val safe = mutableListOf<AgentEpisodeEntity>()
        for (episode in database.memory().episodes()) {
            val job = database.memory().job(episode.segmentId) ?: continue
            if (input(job, run) == null) continue
            val sources = Json.decodeFromString<List<AgentMessageSource>>(episode.sourcesJson)
            if (notes.canUseSources(run, sources)) safe += episode
        }
        val hits = FtsIndexText.matchQuery(query)?.let { database.memory().search(it, safe.map { row -> row.segmentId }) }.orEmpty()
        return (safe.filter { it.segmentId in hits }.take(3) + safe.take(2)).distinctBy { it.segmentId }.take(AgentMemoryLimits.RecallCount).map {
            AgentEpisodeRecall("[不可信派生情景记忆 sourceId=${it.segmentId} sourceVersion=${it.sourceHash} createdAt=${it.createdAtEpochMs}]\n${it.summaryJson}", Json.decodeFromString(it.sourcesJson))
        }
    }

    private suspend fun <T> transaction(block: suspend () -> T): T = database.useWriterConnection { it.immediateTransaction { block() } }
}
