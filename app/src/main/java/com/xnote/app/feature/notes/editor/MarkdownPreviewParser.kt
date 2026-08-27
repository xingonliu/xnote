package com.xnote.app.feature.notes.editor

// -- Type Definitions

internal sealed interface MarkdownPreviewBlock {
    data class Heading(val level: Int, val content: String) : MarkdownPreviewBlock
    data class Paragraph(val content: String) : MarkdownPreviewBlock
    data class Quote(val content: String) : MarkdownPreviewBlock
    data class Bullet(val indent: Int, val content: String) : MarkdownPreviewBlock
    data class Numbered(val indent: Int, val number: String, val content: String) : MarkdownPreviewBlock
    data class Checklist(val indent: Int, val checked: Boolean, val content: String) : MarkdownPreviewBlock
    data class Code(val language: String?, val content: String) : MarkdownPreviewBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownPreviewBlock
}

// -- Constants

private val HeadingPattern = Regex("^\\s*(#{1,6})[ \\t]+(.*)$")
private val QuotePattern = Regex("^\\s*>[ \\t]?(.*)$")
private val ChecklistPattern = Regex("^(\\s*)[-*+][ \\t]+\\[([ xX])]\\s+(.*)$")
private val BulletPattern = Regex("^(\\s*)[-*+][ \\t]+(.*)$")
private val NumberedPattern = Regex("^(\\s*)(\\d+)[.)][ \\t]+(.*)$")
private val TableDividerCellPattern = Regex(":?-{3,}:?")

// -- Functions

internal fun parseMarkdownPreview(markdown: String): List<MarkdownPreviewBlock> {
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MarkdownPreviewBlock>()
    val paragraph = mutableListOf<String>()
    var index = 0

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        blocks += MarkdownPreviewBlock.Paragraph(paragraph.joinToString("\n"))
        paragraph.clear()
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            flushParagraph()
            val language = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
            val code = mutableListOf<String>()
            index += 1
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                code += lines[index]
                index += 1
            }
            if (index < lines.size) index += 1
            blocks += MarkdownPreviewBlock.Code(language, code.joinToString("\n"))
            continue
        }
        if (line.isBlank()) {
            flushParagraph()
            index += 1
            continue
        }
        val tableHeader = splitTableRow(line)
        if (tableHeader != null && index + 1 < lines.size && isTableDivider(lines[index + 1])) {
            flushParagraph()
            val rows = mutableListOf<List<String>>()
            index += 2
            while (index < lines.size) {
                val row = splitTableRow(lines[index]) ?: break
                rows += normalizeTableRow(row, tableHeader.size)
                index += 1
            }
            blocks += MarkdownPreviewBlock.Table(tableHeader, rows)
            continue
        }
        val heading = HeadingPattern.matchEntire(line)
        if (heading != null) {
            flushParagraph()
            blocks += MarkdownPreviewBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            index += 1
            continue
        }
        val checklist = ChecklistPattern.matchEntire(line)
        if (checklist != null) {
            flushParagraph()
            blocks += MarkdownPreviewBlock.Checklist(
                indent = checklist.groupValues[1].length / 2,
                checked = checklist.groupValues[2].equals("x", ignoreCase = true),
                content = checklist.groupValues[3],
            )
            index += 1
            continue
        }
        val numbered = NumberedPattern.matchEntire(line)
        if (numbered != null) {
            flushParagraph()
            blocks += MarkdownPreviewBlock.Numbered(
                indent = numbered.groupValues[1].length / 2,
                number = numbered.groupValues[2],
                content = numbered.groupValues[3],
            )
            index += 1
            continue
        }
        val bullet = BulletPattern.matchEntire(line)
        if (bullet != null) {
            flushParagraph()
            blocks += MarkdownPreviewBlock.Bullet(
                indent = bullet.groupValues[1].length / 2,
                content = bullet.groupValues[2],
            )
            index += 1
            continue
        }
        val quote = QuotePattern.matchEntire(line)
        if (quote != null) {
            flushParagraph()
            blocks += MarkdownPreviewBlock.Quote(quote.groupValues[1])
            index += 1
            continue
        }
        paragraph += line
        index += 1
    }
    flushParagraph()
    return blocks
}

private fun isTableDivider(line: String): Boolean {
    val cells = splitTableRow(line) ?: return false
    return cells.isNotEmpty() && cells.all { TableDividerCellPattern.matches(it.trim()) }
}

private fun splitTableRow(line: String): List<String>? {
    if ('|' !in line) return null
    val trimmed = line.trim().removePrefix("|").removeSuffix("|")
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    for (character in trimmed) {
        when {
            escaped -> {
                current.append('\\')
                current.append(character)
                escaped = false
            }
            character == '\\' -> escaped = true
            character == '|' -> {
                cells += current.toString().trim()
                current.clear()
            }
            else -> current.append(character)
        }
    }
    if (escaped) current.append('\\')
    cells += current.toString().trim()
    return cells
}

private fun normalizeTableRow(row: List<String>, columnCount: Int): List<String> {
    return List(columnCount) { index -> row.getOrNull(index).orEmpty() }
}
