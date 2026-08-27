package com.xnote.app.domain.rules

import com.xnote.app.domain.document.DrawingBlock
import com.xnote.app.domain.document.ImageBlock
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.StickerBlock
import com.xnote.app.domain.document.TableBlock
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteKind

// -- Type Definitions

enum class ConversionBlocker {
    AlreadyMarkdown,
    Image,
    Sticker,
    Drawing,
}

// -- Functions

fun conversionBlockers(document: NoteDocument): Set<ConversionBlocker> {
    val blockers = linkedSetOf<ConversionBlocker>()
    for (block in document.blocks) {
        when (block) {
            is ImageBlock -> blockers += ConversionBlocker.Image
            is StickerBlock -> blockers += ConversionBlocker.Sticker
            is DrawingBlock -> blockers += ConversionBlocker.Drawing
            is TextBlock, is TableBlock -> Unit
        }
    }
    return blockers
}

fun conversionBlockers(note: Note): Set<ConversionBlocker> {
    if (note.kind == NoteKind.Markdown) {
        return setOf(ConversionBlocker.AlreadyMarkdown)
    }
    return conversionBlockers(note.document ?: NoteDocument())
}

fun canConvertToMarkdown(note: Note): Boolean = conversionBlockers(note).isEmpty()
