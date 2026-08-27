package com.xnote.app.domain.text

import com.xnote.app.domain.document.DrawingBlock
import com.xnote.app.domain.document.ImageBlock
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.StickerBlock
import com.xnote.app.domain.document.TableBlock
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.document.plainText
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteKind

// -- Type Definitions

data class VisibleTextStats(
    val characterCount: Int,
    val latinWordCount: Int,
)

// -- Constants

private val LatinWordRegex = Regex("[A-Za-z]+(?:['’][A-Za-z]+)*")

const val DefaultSummaryLength = 80

// -- Functions

fun extractPlainText(document: NoteDocument): String {
    val parts = ArrayList<String>()
    for (block in document.blocks) {
        when (block) {
            is TextBlock -> {
                val text = block.inlines.plainText()
                if (text.isNotEmpty()) parts += text
            }
            is TableBlock -> {
                for (row in block.rows) {
                    for (cell in row.cells) {
                        val text = cell.inlines.plainText()
                        if (text.isNotEmpty()) parts += text
                    }
                }
            }
            is ImageBlock, is StickerBlock, is DrawingBlock -> Unit
        }
    }
    return parts.joinToString("\n")
}

fun extractPlainText(note: Note): String = when (note.kind) {
    NoteKind.Rich -> note.document?.let(::extractPlainText).orEmpty()
    NoteKind.Markdown -> MarkdownVisibleText.extract(note.markdownText.orEmpty())
}

fun summarizePlainText(plainText: String, maxLength: Int = DefaultSummaryLength): String {
    val collapsed = plainText.replace(Regex("\\s+"), " ").trim()
    if (collapsed.length <= maxLength) return collapsed
    return collapsed.take(maxLength).trimEnd()
}

fun visibleTextStats(plainText: String): VisibleTextStats = VisibleTextStats(
    characterCount = countVisibleCharacters(plainText),
    latinWordCount = LatinWordRegex.findAll(plainText).count(),
)

fun visibleTextStats(note: Note): VisibleTextStats = visibleTextStats(extractPlainText(note))

fun countVisibleCharacters(text: String): Int {
    var count = 0
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        if (!Character.isWhitespace(codePoint)) {
            count += 1
        }
        index += Character.charCount(codePoint)
    }
    return count
}
