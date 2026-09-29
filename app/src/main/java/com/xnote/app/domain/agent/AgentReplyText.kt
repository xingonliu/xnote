package com.xnote.app.domain.agent

// -- Constants

private val ReplyFence = Regex("^\\s{0,3}(`{3,}|~{3,})(.*)$")
private val ReplyHeading = Regex("^\\s{0,3}#{1,6}\\s+")
private val ReplyQuote = Regex("^\\s{0,3}(?:>\\s?)+")
private val ReplyBullet = Regex("^([ \\t]*)[-+*]\\s+(?:\\[[ xX]\\]\\s+)?")
private val ReplyRule = Regex("^\\s{0,3}(?:[-*_]\\s*){3,}$")
private val ReplyTableRule = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$")
private val ReplyInline = Regex("(`+)(.+?)\\1|!?\\[([^]\\n]+)]\\(([^)\\n]+)\\)|(\\*{1,3}|(?<![A-Za-z0-9_])_{1,3}|~~)(?=\\S)(.+?)\\5")

// -- Functions

/** Normalize conversational prose without altering literal code or tool payloads. */
fun agentReplyPlainText(value: String): String {
    var fence: String? = null
    return value.lineSequence().mapNotNull { line ->
        val marker = ReplyFence.matchEntire(line)
        val currentFence = fence
        if (currentFence != null) {
            if (marker != null && marker.groupValues[1].first() == currentFence.first() &&
                marker.groupValues[1].length >= currentFence.length && marker.groupValues[2].isBlank()) {
                fence = null
                null
            } else line
        } else if (marker != null) {
            fence = marker.groupValues[1]
            null
        } else if (ReplyRule.matches(line) || ReplyTableRule.matches(line)) {
            null
        } else {
            val prose = line.replace(ReplyHeading, "").replace(ReplyQuote, "")
                .replace(ReplyBullet) { "${it.groupValues[1]}• " }
            prose.replace(ReplyInline) { match ->
                when {
                    match.groupValues[1].isNotEmpty() -> match.groupValues[2]
                    match.groupValues[3].isNotEmpty() -> {
                        val label = match.groupValues[3]
                        val destination = match.groupValues[4]
                        if (label == destination) label else "$label ($destination)"
                    }
                    else -> match.groupValues[6]
                }
            }
        }
    }.joinToString("\n")
}
