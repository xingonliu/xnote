package com.xnote.app.data.agent

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.text.FtsIndexText
import com.xnote.app.domain.text.extractPlainText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.UUID

// -- Type Definitions

data class AgentNoteRecall(val note: NoteEntity, val text: String)

class AgentNoteMemoryStore(private val database: XNoteDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    // -- State and Variables

    private val processor = Mutex()

    // -- Derived Values

    val entries = database.noteMemory().observe()

    // -- Functions

    private suspend fun allowed(note: NoteEntity): Boolean = AgentAccessContext(AgentPermissionStore(database).current(), "note-memory", "note-memory")
        .canReadCurrent(AgentNoteAccess(note.id, note.notebookId, note.deletedAtEpochMs != null))

    suspend fun clear() = transaction { database.noteMemory().clear(); database.noteMemory().clearFts() }

    suspend fun sweep(profiles: ModelProfileStore) = transaction {
        val profile = profiles.list().singleOrNull { it.isDefault && it.enabled }
        for (note in database.notes().getAll()) {
            val previous = database.noteMemory().get(note.id)
            if (!allowed(note)) {
                database.noteMemory().delete(note.id); database.noteMemory().deleteFts(note.id)
                continue
            }
            if (previous?.sourceVersion == note.agentVersion()) continue
            val plain = extractPlainText(note.toDomain().document)
            val short = plain.length <= AgentNoteMemoryLimits.ShortCharacters
            if (!short && profile == null) continue
            val row = AgentNoteMemoryEntity(note.id, UUID.randomUUID().toString(), agentSourceHash(note.title + "\n" + note.documentJson),
                note.agentVersion(), note.title, plain, "", profile?.id.orEmpty(), profile?.version ?: 0,
                profile?.modelId.orEmpty(), profile?.protocol?.name.orEmpty(), AgentNoteMemoryLimits.PromptVersion,
                maxOf(note.updatedAtEpochMs, clock()), if (short) "raw" else "pending")
            database.noteMemory().save(row)
            index(row)
        }
    }

    private suspend fun index(row: AgentNoteMemoryEntity) {
        database.noteMemory().deleteFts(row.noteId)
        val summary = row.summaryJson.takeIf { it.isNotBlank() }?.let { Json.decodeFromString<AgentNoteSummary>(it) }
        database.noteMemory().insertFts(AgentNoteMemoryFtsEntity(noteId = row.noteId,
            text = FtsIndexText.prepare(listOf(row.title, row.plainText, summary?.summary.orEmpty(), summary?.outline.orEmpty().joinToString(" "), summary?.keywords.orEmpty().joinToString(" ")).joinToString(" "))))
    }

    private suspend fun valid(row: AgentNoteMemoryEntity): Boolean {
        val note = database.notes().get(row.noteId) ?: return false
        return database.noteMemory().get(row.noteId)?.generationId == row.generationId && note.agentVersion() == row.sourceVersion && allowed(note)
    }

    suspend fun process(profiles: ModelProfileStore, client: ModelClient): Boolean = processor.withLock {
        sweep(profiles)
        var retry = false
        for (row in database.noteMemory().all()) {
            if (row.status in setOf("raw", "complete", "blocked") || row.attempts >= AgentMemoryLimits.MaxAttempts) continue
            if (row.updatedAtEpochMs + AgentNoteMemoryLimits.StableMs > clock() || row.retryAfter > clock() || row.leaseUntil > clock()) { retry = true; continue }
            if (database.agent().unfinishedRuns().isNotEmpty() || database.agent().pendingQueue().isNotEmpty()) return@withLock true
            val profile = profiles.list().singleOrNull { it.id == row.profileId && it.version == row.profileVersion && it.enabled }
            if (profile == null || row.promptVersion != AgentNoteMemoryLimits.PromptVersion) {
                block(row, "configuration_changed"); continue
            }
            val input = buildJsonObject { put("title", row.title); put("body", row.plainText); put("version", row.sourceVersion) }.toString()
            val cost = estimatedAgentTokens(input).toLong() + estimatedAgentTokens(AgentNoteSummaryPrompt) + minOf(profile.outputTokens, AgentMemoryLimits.MaxSummaryTokens)
            if (cost + ModelLimits.ToolReserveTokens > profile.contextTokens) {
                block(row, "source_budget"); continue
            }
            val usage = AgentDerivedUsageEntity(UUID.randomUUID().toString(), "note", clock(), cost)
            val claimed = transaction {
                val daily = database.memory().usage(clock() - 24L * 60 * 60 * 1000)
                if (database.noteMemory().get(row.noteId) != row || !valid(row) || database.agent().unfinishedRuns().isNotEmpty() ||
                    database.agent().pendingQueue().isNotEmpty() || daily.size >= AgentMemoryLimits.DailyCalls || daily.sumOf { it.reservedTokens } + cost > AgentMemoryLimits.DailyTokens) false
                else {
                    database.noteMemory().save(row.copy(status = "running", attempts = row.attempts + 1, leaseUntil = clock() + AgentMemoryLimits.LeaseMs))
                    database.memory().saveUsage(usage); true
                }
            }
            if (!claimed) { retry = true; continue }
            try {
                val secret = profiles.credential(profile)
                require(transaction { valid(row) })
                val output = StringBuilder()
                var finished = false
                client.stream(profile.copy(outputTokens = minOf(profile.outputTokens, AgentMemoryLimits.MaxSummaryTokens)), secret,
                    ModelRequest(AgentNoteSummaryPrompt, listOf(ModelMessage(AgentMessageRole.User, input)))).collect { event ->
                    if (database.agent().unfinishedRuns().isNotEmpty()) throw CancellationException("foreground_priority")
                    when (event) {
                        is ModelEvent.Text -> { output.append(event.value); require(output.length <= AgentMemoryLimits.MaxSummaryTokens * 4) }
                        is ModelEvent.Finished -> { require(event.reason == ModelFinish.Complete); finished = true }
                        is ModelEvent.ToolCall -> error("summary_tools_forbidden")
                        is ModelEvent.Usage -> database.memory().saveUsage(usage.copy(inputTokens = event.inputTokens, outputTokens = event.outputTokens,
                            reservedTokens = maxOf(cost, (event.inputTokens ?: 0) + (event.outputTokens ?: 0))))
                        is ModelEvent.NativeParts -> Unit
                    }
                }
                require(finished)
                val summary = parseAgentNoteSummary(output.toString())
                transaction {
                    if (valid(row) && profiles.list().any { it.id == profile.id && it.version == profile.version && it.enabled }) {
                        val completed = row.copy(status = "complete", attempts = row.attempts + 1, summaryJson = Json.encodeToString(summary))
                        database.noteMemory().save(completed); index(completed)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val error = (failure as? ModelException)?.error
                val retryable = error == null || error in setOf(ModelError.Network, ModelError.Service, ModelError.Timeout)
                transaction {
                    if (valid(row)) database.noteMemory().save(row.copy(status = if (retryable) "failed" else "blocked", attempts = row.attempts + 1,
                        retryAfter = clock() + AgentMemoryLimits.RetryDelayMs, error = error?.name ?: "invalid_summary"))
                }
                retry = retry || (retryable && row.attempts + 1 < AgentMemoryLimits.MaxAttempts)
            }
        }
        retry
    }

    private suspend fun block(row: AgentNoteMemoryEntity, error: String) = transaction {
        if (database.noteMemory().get(row.noteId) == row && valid(row)) database.noteMemory().save(row.copy(status = "blocked", error = error))
    }

    /** Current permission and source version are checked before any match or excerpt is exposed. */
    suspend fun search(run: AgentRunEntity, query: String): List<AgentNoteRecall> {
        val access = AgentNoteStore(database).access(run)
        val allowed = database.notes().getAll().filter { access.canReadCurrent(AgentNoteAccess(it.id, it.notebookId, it.deletedAtEpochMs != null)) }
        val current = database.noteMemory().all().filter { row -> allowed.any { it.id == row.noteId && it.agentVersion() == row.sourceVersion } }.associateBy { it.noteId }
        val fts = FtsIndexText.matchQuery(query)?.let { database.noteMemory().search(it, current.keys.toList()) }.orEmpty().toSet()
        return allowed.mapNotNull { note ->
            val body = extractPlainText(note.toDomain().document)
            if (query.isNotBlank() && note.id !in fts && !note.title.contains(query, true) && !body.contains(query, true)) return@mapNotNull null
            val summary = current[note.id]?.takeIf { it.status == "complete" }?.summaryJson?.let { Json.decodeFromString<AgentNoteSummary>(it).summary }
            val match = body.indexOf(query, ignoreCase = true).coerceAtLeast(0)
            AgentNoteRecall(note, summary ?: body.drop((match - 50).coerceAtLeast(0)).take(600))
        }.sortedWith(compareByDescending<AgentNoteRecall> { it.note.updatedAtEpochMs }.thenBy { it.note.id })
    }

    private suspend fun <T> transaction(block: suspend () -> T): T = database.useWriterConnection { it.immediateTransaction { block() } }
}
