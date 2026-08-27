package com.xnote.app.domain.markdown

import com.xnote.app.domain.document.InlineRun
import com.xnote.app.domain.document.ListMarker
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.ParagraphStyle
import com.xnote.app.domain.document.TableBlock
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.document.plainText

// -- Constants

private val MarkdownEscapableCharacters = setOf(
    '\\', '`', '*', '_', '{', '}', '[', ']', '<', '>', '(', ')', '#', '+', '-', '.', '!', '|', '~', '=',
)

// -- Functions

fun richNoteMarkdown(title: String, document: NoteDocument): String {
    val parts = buildList {
        title.trim().takeIf { it.isNotEmpty() }?.let { value ->
            add("# ${escapeMarkdownText(value)}")
        }
        document.blocks.forEach { block ->
            when (block) {
                is TextBlock -> add(block.toMarkdown())
                is TableBlock -> add(block.toMarkdown())
                else -> error("Media blocks must be removed before Markdown conversion")
            }
        }
    }
    return parts.joinToString(separator = "\n\n").trimEnd()
}

private fun TextBlock.toMarkdown(): String {
    if (paragraphStyle == ParagraphStyle.Monospace) {
        return monospaceMarkdown(inlines.plainText(), indent, quoted)
    }

    val content = buildString {
        append(
            when (listMarker) {
                ListMarker.None -> ""
                ListMarker.Bullet, ListMarker.Dash -> "- "
                ListMarker.Numbered -> "1. "
                ListMarker.Checklist -> if (checked) "- [x] " else "- [ ] "
            },
        )
        append(
            when (paragraphStyle) {
                ParagraphStyle.Heading -> "## "
                ParagraphStyle.Subheading -> "### "
                ParagraphStyle.Body, ParagraphStyle.Monospace -> ""
            },
        )
        append(inlines.joinToString(separator = "") { it.toMarkdown() })
    }
    val prefix = "  ".repeat(indent.coerceAtLeast(0)) + if (quoted) "> " else ""
    return content.lines().joinToString(separator = "\n") { line -> prefix + line }
}

private fun TableBlock.toMarkdown(): String {
    val columnCount = rows.maxOfOrNull { it.cells.size }?.coerceAtLeast(1) ?: 1
    val normalizedRows = if (rows.isEmpty()) listOf(emptyList()) else rows.map { it.cells }
    val lines = buildList {
        add(tableRowMarkdown(normalizedRows.first(), columnCount))
        add("| ${List(columnCount) { "---" }.joinToString(" | ")} |")
        normalizedRows.drop(1).forEach { row -> add(tableRowMarkdown(row, columnCount)) }
    }
    return lines.joinToString(separator = "\n")
}

private fun tableRowMarkdown(
    cells: List<com.xnote.app.domain.document.TableCell>,
    columnCount: Int,
): String {
    val values = List(columnCount) { index ->
        cells.getOrNull(index)?.inlines
            ?.joinToString(separator = "") { it.toMarkdown() }
            ?.replace("\n", "<br>")
            .orEmpty()
    }
    return "| ${values.joinToString(" | ")} |"
}

private fun InlineRun.toMarkdown(): String {
    var value = escapeMarkdownText(text)
    if (value.isEmpty()) return value
    if (bold) value = "**$value**"
    if (italic) value = "*$value*"
    if (strikethrough) value = "~~$value~~"
    if (underline) value = "<u>$value</u>"
    if (highlight) value = "==$value=="
    if (!linkUrl.isNullOrBlank()) value = "[$value](${escapeMarkdownUrl(linkUrl)})"
    return value
}

private fun monospaceMarkdown(text: String, indent: Int, quoted: Boolean): String {
    val prefix = "  ".repeat(indent.coerceAtLeast(0)) + if (quoted) "> " else ""
    if ('\n' !in text) {
        val longestDelimiter = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
        val delimiter = "`".repeat((longestDelimiter + 1).coerceAtLeast(1))
        val padding = if (text.startsWith('`') || text.endsWith('`')) " " else ""
        return "$prefix$delimiter$padding$text$padding$delimiter"
    }
    return buildString {
        append(prefix)
        append("```\n")
        text.lines().forEachIndexed { index, line ->
            if (index > 0) append('\n')
            append(prefix)
            append(line)
        }
        append('\n')
        append(prefix)
        append("```")
    }
}

private fun escapeMarkdownText(text: String): String = buildString(text.length) {
    text.forEach { character ->
        if (character in MarkdownEscapableCharacters) append('\\')
        append(character)
    }
}

private fun escapeMarkdownUrl(url: String): String = buildString(url.length) {
    url.trim().forEach { character ->
        if (character == '\\' || character == ')') append('\\')
        append(character)
    }
}
