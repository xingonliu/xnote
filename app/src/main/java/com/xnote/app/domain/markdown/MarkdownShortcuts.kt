package com.xnote.app.domain.markdown

import com.xnote.app.domain.document.EditorChange
import com.xnote.app.domain.document.EditorSelection
import com.xnote.app.domain.document.InlineMarks
import com.xnote.app.domain.document.InlineRun
import com.xnote.app.domain.document.ListMarker
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.ParagraphStyle
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.document.block
import com.xnote.app.domain.document.mapRange
import com.xnote.app.domain.document.plainText
import com.xnote.app.domain.document.replaceBlock
import com.xnote.app.domain.document.replaceSelectedInlines
import com.xnote.app.domain.document.replaceSelectedText
import com.xnote.app.domain.document.selectedInlines

// -- Type Definitions

private sealed interface BlockShortcut {
    data object Heading : BlockShortcut
    data object Subheading : BlockShortcut
    data object Quote : BlockShortcut
    data object DashList : BlockShortcut
    data object BulletList : BlockShortcut
    data object NumberedList : BlockShortcut
    data class Checklist(val checked: Boolean) : BlockShortcut
}

private data class DelimitedMark(
    val opener: String,
    val apply: (InlineRun) -> InlineRun,
)

private data class DelimiterMatch(
    val openerStart: Int,
    val closerStart: Int,
    val delimiter: DelimitedMark,
)

private data class LinkMatch(
    val bracketOpen: Int,
    val bracketClose: Int,
    val urlOpen: Int,
    val urlClose: Int,
    val url: String,
)

// -- Constants

private val NumberedPrefix = Regex("^\\d+\\. $")
private val DelimiterCharacters = setOf('*', '_', '~', '=')

// -- Functions

fun applyMarkdownShortcut(
    document: NoteDocument,
    selection: EditorSelection,
    inserted: String,
    enabled: Boolean,
    composing: Boolean,
): EditorChange? {
    if (!enabled || composing || !selection.isCollapsed || inserted.isEmpty()) return null
    if ('\n' in inserted || '\r' in inserted) return null
    val blockChange = if (!selection.isTable) {
        applyBlockShortcut(document, selection, inserted)
    } else {
        null
    }
    return blockChange ?: applyInlineShortcut(document, selection, inserted)
}

private fun applyBlockShortcut(
    document: NoteDocument,
    selection: EditorSelection,
    inserted: String,
): EditorChange? {
    val block = document.block(selection.blockId) as? TextBlock ?: return null
    val text = block.inlines.plainText()
    val caret = selection.min.coerceIn(0, text.length)
    if (caret == 0 || caret > text.length) return null
    val prefix = text.substring(0, caret)
    if (!prefix.endsWith(inserted)) return null
    val shortcut = matchBlockPrefix(prefix, block.listMarker) ?: return null
    val stripped = document.replaceSelectedText(
        selection.copy(start = 0, end = prefix.length),
        "",
        InlineMarks(),
    )
    val updatedBlock = (stripped.document.block(block.id) as? TextBlock)?.withShortcut(shortcut) ?: return null
    return EditorChange(
        document = stripped.document.replaceBlock(updatedBlock),
        selection = selection.copy(start = 0, end = 0),
    )
}

private fun matchBlockPrefix(prefix: String, listMarker: ListMarker): BlockShortcut? {
    when (prefix) {
        "- [ ] " -> return BlockShortcut.Checklist(checked = false)
        "- [x] ", "- [X] " -> return BlockShortcut.Checklist(checked = true)
    }
    if (listMarker == ListMarker.Dash) {
        when (prefix) {
            "[ ] " -> return BlockShortcut.Checklist(checked = false)
            "[x] ", "[X] " -> return BlockShortcut.Checklist(checked = true)
        }
    }
    return when {
        prefix == "- " -> BlockShortcut.DashList
        prefix == "* " || prefix == "+ " -> BlockShortcut.BulletList
        prefix == "> " -> BlockShortcut.Quote
        prefix == "## " -> BlockShortcut.Subheading
        prefix == "# " -> BlockShortcut.Heading
        NumberedPrefix.matches(prefix) -> BlockShortcut.NumberedList
        else -> null
    }
}

private fun TextBlock.withShortcut(shortcut: BlockShortcut): TextBlock = when (shortcut) {
    BlockShortcut.Heading -> copy(
        paragraphStyle = ParagraphStyle.Heading,
        listMarker = ListMarker.None,
        checked = false,
        collapsed = false,
    )
    BlockShortcut.Subheading -> copy(
        paragraphStyle = ParagraphStyle.Subheading,
        listMarker = ListMarker.None,
        checked = false,
        collapsed = false,
    )
    BlockShortcut.Quote -> copy(quoted = true)
    BlockShortcut.DashList -> copy(
        paragraphStyle = ParagraphStyle.Body,
        listMarker = ListMarker.Dash,
        checked = false,
        collapsed = false,
    )
    BlockShortcut.BulletList -> copy(
        paragraphStyle = ParagraphStyle.Body,
        listMarker = ListMarker.Bullet,
        checked = false,
        collapsed = false,
    )
    BlockShortcut.NumberedList -> copy(
        paragraphStyle = ParagraphStyle.Body,
        listMarker = ListMarker.Numbered,
        checked = false,
        collapsed = false,
    )
    is BlockShortcut.Checklist -> copy(
        paragraphStyle = ParagraphStyle.Body,
        listMarker = ListMarker.Checklist,
        checked = shortcut.checked,
        collapsed = false,
    )
}

private fun applyInlineShortcut(
    document: NoteDocument,
    selection: EditorSelection,
    inserted: String,
): EditorChange? {
    val text = document.selectedInlines(selection)?.plainText() ?: return null
    val caret = selection.min.coerceIn(0, text.length)
    applyLinkShortcut(document, selection, text, caret, inserted)?.let { return it }
    val match = findDelimiterMatch(text, caret, inserted) ?: return null
    return applyDelimitedMark(document, selection, match)
}

private fun applyDelimitedMark(
    document: NoteDocument,
    selection: EditorSelection,
    match: DelimiterMatch,
): EditorChange? {
    val openerEnd = match.openerStart + match.delimiter.opener.length
    val closerEnd = match.closerStart + match.delimiter.opener.length
    val afterCloser = document.replaceSelectedText(
        selection.copy(start = match.closerStart, end = closerEnd),
        "",
        InlineMarks(),
    )
    val afterOpener = afterCloser.document.replaceSelectedText(
        selection.copy(start = match.openerStart, end = openerEnd),
        "",
        InlineMarks(),
    )
    val interiorEnd = match.closerStart - match.delimiter.opener.length
    val inlines = afterOpener.document.selectedInlines(selection) ?: return null
    val marked = inlines.mapRange(match.openerStart, interiorEnd, match.delimiter.apply)
    return EditorChange(
        document = afterOpener.document.replaceSelectedInlines(selection, marked),
        selection = selection.copy(start = interiorEnd, end = interiorEnd),
    )
}

private fun applyLinkShortcut(
    document: NoteDocument,
    selection: EditorSelection,
    text: String,
    caret: Int,
    inserted: String,
): EditorChange? {
    if (inserted != ")" || caret == 0 || text[caret - 1] != ')') return null
    val match = findLinkMatch(text, caret) ?: return null
    val afterUrl = document.replaceSelectedText(
        selection.copy(start = match.bracketClose, end = match.urlClose + 1),
        "",
        InlineMarks(),
    )
    val afterBracket = afterUrl.document.replaceSelectedText(
        selection.copy(start = match.bracketOpen, end = match.bracketOpen + 1),
        "",
        InlineMarks(),
    )
    val interiorEnd = match.bracketClose - 1
    val inlines = afterBracket.document.selectedInlines(selection) ?: return null
    val linked = inlines.mapRange(match.bracketOpen, interiorEnd) { run ->
        run.copy(linkUrl = match.url)
    }
    return EditorChange(
        document = afterBracket.document.replaceSelectedInlines(selection, linked),
        selection = selection.copy(start = interiorEnd, end = interiorEnd),
    )
}

private fun findDelimiterMatch(text: String, caret: Int, inserted: String): DelimiterMatch? {
    val delimiters = buildList {
        if (completesCloser(text, caret, inserted, "**")) {
            add(DelimitedMark("**") { it.copy(bold = true) })
        }
        if (completesCloser(text, caret, inserted, "__")) {
            add(DelimitedMark("__") { it.copy(bold = true) })
        }
        if (completesCloser(text, caret, inserted, "~~")) {
            add(DelimitedMark("~~") { it.copy(strikethrough = true) })
        }
        if (completesCloser(text, caret, inserted, "==")) {
            add(DelimitedMark("==") { it.copy(highlight = true) })
        }
        if (completesCloser(text, caret, inserted, "*") && !text.endsWith("**", caret)) {
            add(DelimitedMark("*") { it.copy(italic = true) })
        }
        if (completesCloser(text, caret, inserted, "_") && !text.endsWith("__", caret)) {
            add(DelimitedMark("_") { it.copy(italic = true) })
        }
    }
    for (delimiter in delimiters) {
        findOpener(text, caret, delimiter)?.let { return it }
    }
    return null
}

private fun String.endsWith(suffix: String, endExclusive: Int): Boolean {
    val start = endExclusive - suffix.length
    return start >= 0 && substring(start, endExclusive) == suffix
}

private fun completesCloser(text: String, caret: Int, inserted: String, closer: String): Boolean {
    if (caret < closer.length || !text.endsWith(closer, caret)) return false
    if (isEscaped(text, caret - closer.length)) return false
    return inserted == closer || (inserted.length == 1 && closer.endsWith(inserted))
}

private fun findOpener(text: String, caret: Int, delimiter: DelimitedMark): DelimiterMatch? {
    val token = delimiter.opener
    val closerStart = caret - token.length
    if (closerStart <= token.length) return null
    if (isWordInternal(text, closerStart, caret)) return null
    var index = closerStart - token.length
    while (index >= 0) {
        if (text.startsWith(token, index) &&
            !isEscaped(text, index) &&
            !isSameDelimiterRun(text, index, token) &&
            !isWordInternal(text, index, index + token.length)
        ) {
            val interior = text.substring(index + token.length, closerStart)
            if (isConvertibleInterior(interior, token)) {
                return DelimiterMatch(index, closerStart, delimiter)
            }
        }
        index -= 1
    }
    return null
}

private fun findLinkMatch(text: String, caret: Int): LinkMatch? {
    val urlClose = caret - 1
    if (isEscaped(text, urlClose)) return null
    val urlOpen = findUnescaped(text, "](", endExclusive = urlClose) ?: return null
    val parenOpen = urlOpen + 1
    val url = text.substring(parenOpen + 1, urlClose).trim()
    if (url.isEmpty() || '\n' in url) return null
    val bracketClose = urlOpen
    val bracketOpen = findUnescapedOpenBracket(text, bracketClose) ?: return null
    val linkText = text.substring(bracketOpen + 1, bracketClose)
    if (linkText.isEmpty() || '\n' in linkText) return null
    return LinkMatch(
        bracketOpen = bracketOpen,
        bracketClose = bracketClose,
        urlOpen = parenOpen,
        urlClose = urlClose,
        url = url,
    )
}

private fun findUnescaped(text: String, token: String, endExclusive: Int): Int? {
    var index = endExclusive - token.length
    while (index >= 0) {
        if (text.startsWith(token, index) && !isEscaped(text, index)) {
            return index
        }
        index -= 1
    }
    return null
}

private fun findUnescapedOpenBracket(text: String, bracketClose: Int): Int? {
    var index = bracketClose - 1
    while (index >= 0) {
        if (text[index] == '[' && !isEscaped(text, index)) {
            return index
        }
        if (text[index] == ']' && !isEscaped(text, index)) {
            return null
        }
        index -= 1
    }
    return null
}

private fun isConvertibleInterior(interior: String, token: String): Boolean {
    if (interior.isEmpty() || interior.isBlank()) return false
    if (interior.first().isWhitespace() || interior.last().isWhitespace()) return false
    val marker = token.first()
    if (interior.first() == marker || interior.last() == marker) return false
    return interior.any { character ->
        !character.isWhitespace() && character !in DelimiterCharacters
    }
}

private fun isSameDelimiterRun(text: String, openerStart: Int, token: String): Boolean {
    val marker = token.first()
    return openerStart > 0 && text[openerStart - 1] == marker && !isEscaped(text, openerStart - 1)
}

private fun isWordInternal(text: String, start: Int, end: Int): Boolean {
    val left = start > 0 && isAsciiWordChar(text[start - 1])
    val right = end < text.length && isAsciiWordChar(text[end])
    return left && right
}

private fun isAsciiWordChar(character: Char): Boolean {
    return character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9'
}

private fun isEscaped(text: String, index: Int): Boolean {
    var count = 0
    var cursor = index - 1
    while (cursor >= 0 && text[cursor] == '\\') {
        count += 1
        cursor -= 1
    }
    return count % 2 == 1
}
