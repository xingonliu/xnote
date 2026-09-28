package com.xnote.app.feature.notes.editor

import com.xnote.app.domain.agent.agentSequenceEdits
import com.xnote.app.domain.document.*

// -- Functions

internal fun rebaseEditorSelection(before: NoteDocument, after: NoteDocument, selection: EditorSelection): EditorSelection {
    fun text(document: NoteDocument): String? = when (val block = document.block(selection.blockId)) {
        is TextBlock -> block.inlines.plainText()
        is TableBlock -> block.rows.getOrNull(selection.tableRow ?: -1)?.cells?.getOrNull(selection.tableColumn ?: -1)?.inlines?.plainText()
        else -> null
    }
    val old = text(before) ?: return selection
    val latest = text(after) ?: return EditorSelection(after.blocks.filterIsInstance<TextBlock>().firstOrNull()?.id.orEmpty())
    if (old == latest) return selection
    val edits = agentSequenceEdits(old.codePoints().toArray().toList(), latest.codePoints().toArray().toList())
    fun position(offset: Int): Int {
        val at = old.codePointCount(0, offset.coerceIn(0, old.length))
        var mapped = at
        if (edits != null) for (edit in edits) {
            if (at < edit.start) break
            if (at <= edit.end) {
                mapped += edit.replacement.size - (at - edit.start)
                break
            }
            mapped += edit.replacement.size - (edit.end - edit.start)
        }
        return latest.offsetByCodePoints(0, mapped.coerceIn(0, latest.codePointCount(0, latest.length)))
    }
    return selection.copy(start = position(selection.start), end = position(selection.end))
}
