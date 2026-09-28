package com.xnote.app.domain.agent

import java.text.BreakIterator
import java.util.Locale

// -- Type Definitions

enum class AgentDiffKind { Equal, Removed, Added }
data class AgentDiffPart(val kind: AgentDiffKind, val text: String)

// -- Functions

fun agentDisplayDiff(before: String, after: String, byLine: Boolean = false): List<AgentDiffPart> {
    val base = diffTokens(before, byLine)
    val next = diffTokens(after, byLine)
    val edits = agentSequenceEdits(base, next) ?: listOf(AgentSequenceEdit(0, base.size, next))
    return buildList {
        fun part(kind: AgentDiffKind, tokens: List<String>) {
            if (tokens.isNotEmpty()) add(AgentDiffPart(kind, tokens.joinToString("")))
        }
        var offset = 0
        for (edit in edits) {
            part(AgentDiffKind.Equal, base.subList(offset, edit.start))
            part(AgentDiffKind.Removed, base.subList(edit.start, edit.end))
            part(AgentDiffKind.Added, edit.replacement)
            offset = edit.end
        }
        part(AgentDiffKind.Equal, base.subList(offset, base.size))
    }
}

private fun diffTokens(text: String, byLine: Boolean): List<String> {
    if (byLine) return Regex("[^\\n]*\\n|[^\\n]+$").findAll(text).map { it.value }.toList()
    val iterator = BreakIterator.getWordInstance(Locale.ROOT).apply { setText(text) }
    return buildList {
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            add(text.substring(start, end))
            start = end
            end = iterator.next()
        }
    }
}
