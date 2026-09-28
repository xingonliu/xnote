package com.xnote.app.domain.agent

import kotlinx.serialization.Serializable

// -- Type Definitions

@Serializable
enum class AgentFactEvidence(val priority: Int) { Inferred(0), Repeated(1), Stated(2), Corrected(3) }

@Serializable
data class AgentFactCandidate(
    val key: String,
    val value: String,
    val sourceMessageId: String,
    val quote: String,
    val evidence: AgentFactEvidence = AgentFactEvidence.Stated,
    val sensitive: Boolean = false,
)

enum class AgentFactDecision { Add, Supersede, Ignore, Review }

@Serializable
data class AgentRememberArguments(val key: String, val value: String, val quote: String, val sensitive: Boolean = false)

// -- Functions

fun validAgentFact(candidate: AgentFactCandidate): Boolean =
    candidate.key.length <= 100 && candidate.key.matches(Regex("(user\\.(response|preference|location|fact)|agent\\.response)\\.[a-zA-Z0-9_.-]+")) &&
        candidate.value.isNotBlank() && candidate.value.length <= 240 && candidate.quote.isNotBlank() && candidate.quote.length <= 500 &&
        !Regex("(?i)(api[ _-]?key|password|密码|令牌|token|secret|sk-[a-z0-9]{8})").containsMatchIn(candidate.key + " " + candidate.value + " " + candidate.quote)

fun agentFactDecision(candidate: AgentFactCandidate, oldValue: String?, oldPriority: Int?, newer: Boolean): AgentFactDecision = when {
    !validAgentFact(candidate) || candidate.value == oldValue -> AgentFactDecision.Ignore
    oldPriority != null && candidate.evidence.priority < oldPriority -> AgentFactDecision.Ignore
    candidate.sensitive || candidate.key.startsWith("user.location.") || candidate.key.startsWith("user.fact.") ||
        Regex("(?i)(健康|疾病|病史|用药|宗教|收入|薪资|住址|身份证|银行卡|性取向|政治|health|medical|religion|salary|address|sexual|politic)").containsMatchIn(candidate.key + candidate.value + candidate.quote) ||
        candidate.evidence in setOf(AgentFactEvidence.Inferred, AgentFactEvidence.Repeated) -> AgentFactDecision.Review
    oldValue != null && !newer -> AgentFactDecision.Review
    oldValue != null -> AgentFactDecision.Supersede
    else -> AgentFactDecision.Add
}

fun hasExplicitMemoryRequest(text: String): Boolean = Regex("(?i)(请记住|记住|记下来|remember|memorize)").containsMatchIn(text) &&
    !Regex("(?i)(不要记|别记|不用记|do not remember|don't remember)").containsMatchIn(text)
