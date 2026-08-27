package com.xnote.app.domain.text

// -- Constants

private const val DefaultSearchSnippetLength = 120
private const val SearchSnippetLeadingContext = 32

// -- Functions

fun searchMatchRanges(text: String, query: String): List<IntRange> {
    val normalizedQuery = query.trim()
    if (text.isEmpty() || normalizedQuery.isEmpty()) return emptyList()

    val phraseMatches = nonOverlappingMatches(text, normalizedQuery)
    if (phraseMatches.isNotEmpty()) return phraseMatches

    val terms = normalizedQuery
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
    val candidates = terms
        .flatMap { term -> nonOverlappingMatches(text, term) }
        .sortedBy { range -> range.first }
    val accepted = mutableListOf<IntRange>()
    candidates.forEach { candidate ->
        if (accepted.lastOrNull()?.last?.let { it >= candidate.first } != true) {
            accepted += candidate
        }
    }
    return accepted
}

fun searchSnippet(
    text: String,
    query: String,
    maximumLength: Int = DefaultSearchSnippetLength,
): String {
    require(maximumLength > 0) { "maximumLength must be positive" }
    val normalizedText = text.replace(Regex("\\s+"), " ").trim()
    if (normalizedText.length <= maximumLength) return normalizedText

    val firstMatch = searchMatchRanges(normalizedText, query).firstOrNull()
    if (firstMatch == null) {
        return normalizedText.take(maximumLength).trimEnd() + "…"
    }

    val start = (firstMatch.first - SearchSnippetLeadingContext).coerceAtLeast(0)
    val endExclusive = (start + maximumLength).coerceAtMost(normalizedText.length)
    val adjustedStart = (endExclusive - maximumLength).coerceAtLeast(0)
    return buildString {
        if (adjustedStart > 0) append('…')
        append(normalizedText.substring(adjustedStart, endExclusive).trim())
        if (endExclusive < normalizedText.length) append('…')
    }
}

private fun nonOverlappingMatches(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val matches = mutableListOf<IntRange>()
    var searchFrom = 0
    while (searchFrom <= text.length - query.length) {
        val index = text.indexOf(query, startIndex = searchFrom, ignoreCase = true)
        if (index < 0) break
        matches += index until index + query.length
        searchFrom = index + query.length
    }
    return matches
}
