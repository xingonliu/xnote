package com.xnote.app.domain.document

// -- Type Definitions

data class InlineMarks(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val highlight: Boolean = false,
    val linkUrl: String? = null,
) {
    fun toRun(text: String): InlineRun = InlineRun(
        text = text,
        bold = bold,
        italic = italic,
        underline = underline,
        strikethrough = strikethrough,
        highlight = highlight,
        linkUrl = linkUrl,
    )
}

enum class InlineMark {
    Bold,
    Italic,
    Underline,
    Strikethrough,
    Highlight,
}

// -- Functions

fun InlineRun.marks(): InlineMarks = InlineMarks(
    bold = bold,
    italic = italic,
    underline = underline,
    strikethrough = strikethrough,
    highlight = highlight,
    linkUrl = linkUrl,
)

fun InlineRun.withMarks(marks: InlineMarks): InlineRun = marks.toRun(text)

fun InlineRun.sameStyle(other: InlineRun): Boolean = marks() == other.marks()

fun InlineMarks.has(mark: InlineMark): Boolean = when (mark) {
    InlineMark.Bold -> bold
    InlineMark.Italic -> italic
    InlineMark.Underline -> underline
    InlineMark.Strikethrough -> strikethrough
    InlineMark.Highlight -> highlight
}

fun InlineMarks.toggle(mark: InlineMark, enable: Boolean): InlineMarks = when (mark) {
    InlineMark.Bold -> copy(bold = enable)
    InlineMark.Italic -> copy(italic = enable)
    InlineMark.Underline -> copy(underline = enable)
    InlineMark.Strikethrough -> copy(strikethrough = enable)
    InlineMark.Highlight -> copy(highlight = enable)
}

fun List<InlineRun>.coalesce(): List<InlineRun> {
    if (isEmpty()) return emptyList()
    val merged = ArrayList<InlineRun>(size)
    for (run in this) {
        if (run.text.isEmpty()) continue
        val last = merged.lastOrNull()
        if (last != null && last.sameStyle(run)) {
            merged[merged.lastIndex] = last.copy(text = last.text + run.text)
        } else {
            merged += run
        }
    }
    return merged
}

fun List<InlineRun>.splitAt(offset: Int): Pair<List<InlineRun>, List<InlineRun>> {
    val length = plainText().length
    val safe = offset.coerceIn(0, length)
    if (safe <= 0) return emptyList<InlineRun>() to coalesce()
    if (safe >= length) return coalesce() to emptyList()

    val before = ArrayList<InlineRun>()
    val after = ArrayList<InlineRun>()
    var remaining = safe
    var split = false
    for (run in this) {
        if (split) {
            after += run
            continue
        }
        val runLength = run.text.length
        when {
            remaining >= runLength -> {
                before += run
                remaining -= runLength
                if (remaining == 0) split = true
            }
            remaining == 0 -> {
                after += run
                split = true
            }
            else -> {
                before += run.copy(text = run.text.substring(0, remaining))
                after += run.copy(text = run.text.substring(remaining))
                split = true
            }
        }
    }
    return before.coalesce() to after.coalesce()
}

fun List<InlineRun>.replaceRange(start: Int, end: Int, insertion: InlineRun): List<InlineRun> {
    val length = plainText().length
    val lo = minOf(start, end).coerceIn(0, length)
    val hi = maxOf(start, end).coerceIn(0, length)
    val (left, rest) = splitAt(lo)
    val (_, right) = rest.splitAt(hi - lo)
    val middle = if (insertion.text.isEmpty()) emptyList() else listOf(insertion)
    return (left + middle + right).coalesce()
}

fun List<InlineRun>.mapRange(start: Int, end: Int, transform: (InlineRun) -> InlineRun): List<InlineRun> {
    val length = plainText().length
    val lo = minOf(start, end).coerceIn(0, length)
    val hi = maxOf(start, end).coerceIn(0, length)
    if (lo == hi) return coalesce()
    val (left, rest) = splitAt(lo)
    val (middle, right) = rest.splitAt(hi - lo)
    return (left + middle.map(transform) + right).coalesce()
}

fun List<InlineRun>.runsInRange(start: Int, end: Int): List<InlineRun> {
    val length = plainText().length
    val lo = minOf(start, end).coerceIn(0, length)
    val hi = maxOf(start, end).coerceIn(0, length)
    if (lo == hi) return emptyList()
    val (_, rest) = splitAt(lo)
    val (middle, _) = rest.splitAt(hi - lo)
    return middle
}

fun List<InlineRun>.marksAt(offset: Int): InlineMarks {
    if (isEmpty()) return InlineMarks()
    val length = plainText().length
    if (length == 0) return first().marks()
    val index = if (offset <= 0) 0 else (offset - 1).coerceIn(0, length - 1)
    var cursor = 0
    for (run in this) {
        val next = cursor + run.text.length
        if (index < next) return run.marks()
        cursor = next
    }
    return last().marks()
}

fun List<InlineRun>.rangeHasMark(start: Int, end: Int, mark: InlineMark): Boolean {
    val runs = runsInRange(start, end)
    return runs.isNotEmpty() && runs.all { it.marks().has(mark) }
}

fun List<InlineRun>.rangeHasLink(start: Int, end: Int): Boolean {
    val runs = runsInRange(start, end)
    return runs.isNotEmpty() && runs.all { !it.linkUrl.isNullOrBlank() }
}

fun findTextReplacement(oldText: String, newText: String): Triple<Int, Int, String> {
    if (oldText == newText) {
        return Triple(newText.length, newText.length, "")
    }
    var start = 0
    val shared = minOf(oldText.length, newText.length)
    while (start < shared && oldText[start] == newText[start]) {
        start += 1
    }
    var oldEnd = oldText.length
    var newEnd = newText.length
    while (oldEnd > start && newEnd > start && oldText[oldEnd - 1] == newText[newEnd - 1]) {
        oldEnd -= 1
        newEnd -= 1
    }
    return Triple(start, oldEnd, newText.substring(start, newEnd))
}
