package com.xnote.app.data.agent

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

// -- Type Definitions

class AgentProfileMemoryStore(private val database: XNoteDatabase) {
    // -- Derived Values

    val settings = database.profileMemory().observeSettings().map { it ?: AgentMemorySettingsEntity() }
    val usage = database.memory().observeUsage()
    val problems = database.memory().observeProblems()
    val facts = combine(database.profileMemory().observeFacts(), database.agent().observeMessages(), database.agent().observeRuns()) { rows, _, _ ->
        rows.filter { it.status in setOf("active", "review") && sourceAvailable(it) }
    }

    // -- Functions

    suspend fun setAutomatic(enabled: Boolean) = transaction {
        val previous = database.profileMemory().settings() ?: AgentMemorySettingsEntity()
        database.profileMemory().saveSettings(previous.copy(automatic = enabled,
            enabledAfterSequence = if (enabled && !previous.automatic) database.agent().messages().maxOfOrNull { it.sequence } ?: 0 else previous.enabledAfterSequence))
    }

    suspend fun active(): List<AgentProfileFactEntity> = database.profileMemory().facts().filter { it.status == "active" && sourceAvailable(it) }

    suspend fun canUseMessage(messageId: String): Boolean {
        val ids = database.profileMemory().references(messageId).map { it.factId }
        if (ids.isEmpty()) return true
        return active().map { it.id }.containsAll(ids)
    }

    suspend fun coordinate(candidate: AgentFactCandidate, allowedSourceIds: Set<String>, explicit: Boolean = false): AgentFactDecision = transaction {
        if (!validAgentFact(candidate) || candidate.sourceMessageId !in allowedSourceIds) return@transaction AgentFactDecision.Ignore
        val message = database.agent().message(candidate.sourceMessageId) ?: return@transaction AgentFactDecision.Ignore
        if (message.role != AgentMessageRole.User || message.status != AgentMessageStatus.Complete || message.sourcesJson != "[]" ||
            !message.text.contains(candidate.quote)) return@transaction AgentFactDecision.Ignore
        val run = message.runId?.let { database.agent().run(it) } ?: return@transaction AgentFactDecision.Ignore
        if (run.errorCode == "history_removed") return@transaction AgentFactDecision.Ignore
        val settings = database.profileMemory().settings() ?: AgentMemorySettingsEntity()
        if (message.sequence <= settings.clearedThroughSequence) return@transaction AgentFactDecision.Ignore
        if (explicit) {
            if (!hasExplicitMemoryRequest(message.text)) return@transaction AgentFactDecision.Ignore
        } else if (!settings.automatic || message.sequence <= settings.enabledAfterSequence) return@transaction AgentFactDecision.Ignore
        val forgotten = database.profileMemory().forgotten(candidate.key) != null
        if (forgotten && !explicit) return@transaction AgentFactDecision.Ignore
        val existing = database.profileMemory().facts().filter { it.key == candidate.key }
        if (existing.any { it.sourceMessageId == candidate.sourceMessageId && it.value == candidate.value }) return@transaction AgentFactDecision.Ignore
        val old = active().singleOrNull { it.key == candidate.key }
        val supportedEvidence = if (candidate.evidence == AgentFactEvidence.Corrected &&
            !Regex("(?i)(更正|改为|纠正|不是|correction|actually|instead)").containsMatchIn(message.text)) AgentFactEvidence.Stated else candidate.evidence
        val decision = if (forgotten) AgentFactDecision.Review else agentFactDecision(candidate.copy(evidence = supportedEvidence), old?.value, old?.priority, message.sequence > (old?.sourceSequence ?: 0))
        if (decision == AgentFactDecision.Ignore) return@transaction decision
        val now = System.currentTimeMillis()
        if (decision == AgentFactDecision.Supersede) database.profileMemory().saveFact(checkNotNull(old).copy(status = "superseded", validToEpochMs = now))
        val saved = AgentProfileFactEntity(UUID.randomUUID().toString(), candidate.key, candidate.value, supportedEvidence.name,
            supportedEvidence.priority, candidate.sourceMessageId, candidate.quote, message.sequence,
            if (decision == AgentFactDecision.Review) "review" else "active", old?.id, now)
        database.profileMemory().saveFact(saved)
        if (saved.status == "active") database.profileMemory().saveReference(AgentMessageProfileRefEntity(message.id, saved.id))
        decision
    }

    suspend fun confirm(id: String, value: String) = transaction {
        val fact = database.profileMemory().facts().single { it.id == id }
        require(sourceAvailable(fact)) { "来源已删除，请重新提供记忆内容。" }
        require(validAgentFact(AgentFactCandidate(fact.key, value, fact.sourceMessageId.orEmpty(), value))) { "记忆内容无效或包含凭据。" }
        val now = System.currentTimeMillis()
        database.profileMemory().facts().filter { it.key == fact.key && it.status in setOf("active", "review") }.forEach {
            database.profileMemory().saveFact(it.copy(status = "superseded", validToEpochMs = now))
        }
        database.profileMemory().allowKey(fact.key)
        database.profileMemory().saveFact(fact.copy(id = UUID.randomUUID().toString(), value = value, evidence = "Corrected", priority = 3,
            sourceMessageId = null, sourceQuote = "用户在画像管理中确认", sourceSequence = database.agent().messages().maxOfOrNull { it.sequence } ?: 0,
            status = "active", supersedesId = fact.id, createdAtEpochMs = now, validToEpochMs = null))
    }

    suspend fun forget(key: String) = transaction {
        val now = System.currentTimeMillis()
        database.profileMemory().saveForgotten(AgentProfileForgottenEntity(key, now))
        database.profileMemory().facts().filter { it.key == key }.forEach { database.profileMemory().saveFact(it.copy(status = "forgotten", validToEpochMs = now)) }
    }

    suspend fun clear() = transaction {
        database.profileMemory().facts().map { it.key }.distinct().forEach { forget(it) }
        val settings = database.profileMemory().settings() ?: AgentMemorySettingsEntity()
        database.profileMemory().saveSettings(settings.copy(clearedThroughSequence = database.agent().messages().maxOfOrNull { it.sequence } ?: 0))
    }

    private suspend fun sourceAvailable(fact: AgentProfileFactEntity): Boolean {
        if (fact.sourceMessageId == null) return true
        val message = database.agent().message(fact.sourceMessageId) ?: return false
        return message.sourcesJson == "[]" && message.runId?.let { database.agent().run(it)?.errorCode != "history_removed" } == true
    }

    private suspend fun <T> transaction(block: suspend () -> T): T = database.useWriterConnection { it.immediateTransaction { block() } }
}
