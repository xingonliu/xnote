package com.xnote.app.domain.document

// -- Type Definitions

data class TextAddress(val blockId: String, val row: Int? = null, val column: Int? = null)

data class TextPosition(val address: TextAddress, val offset: Int)

// -- Functions

fun EditorSelection.anchor() = TextPosition(TextAddress(blockId, tableRow, tableColumn), start)

fun EditorSelection.focus() = TextPosition(
    if (isCrossField) TextAddress(requireNotNull(endBlockId), endTableRow, endTableColumn)
    else TextAddress(blockId, tableRow, tableColumn), end,
)

fun selectionBetween(anchor: TextPosition, focus: TextPosition): EditorSelection = EditorSelection(
    blockId = anchor.address.blockId, start = anchor.offset, end = focus.offset,
    tableRow = anchor.address.row, tableColumn = anchor.address.column,
    endBlockId = focus.address.blockId.takeIf { anchor.address != focus.address },
    endTableRow = focus.address.row.takeIf { anchor.address != focus.address },
    endTableColumn = focus.address.column.takeIf { anchor.address != focus.address },
)

fun NoteDocument.textAddresses(): List<TextAddress> = blocks.flatMap { block ->
    when (block) {
        is TextBlock -> listOf(TextAddress(block.id))
        is TableBlock -> block.rows.flatMapIndexed { row, value ->
            value.cells.indices.map { column -> TextAddress(block.id, row, column) }
        }
        else -> emptyList()
    }
}

fun NoteDocument.inlinesAt(address: TextAddress): List<InlineRun> = when (val block = block(address.blockId)) {
    is TextBlock -> block.inlines
    is TableBlock -> block.cell(address.row ?: -1, address.column ?: -1)?.inlines.orEmpty()
    else -> emptyList()
}

fun NoteDocument.selectionParts(selection: EditorSelection): List<EditorSelection> {
    if (!selection.isCrossField) return listOf(selection.copy(start = selection.min, end = selection.max))
    val addresses = textAddresses()
    val anchor = selection.anchor()
    val focus = selection.focus()
    val a = addresses.indexOf(anchor.address)
    val b = addresses.indexOf(focus.address)
    if (a < 0 || b < 0) return emptyList()
    val first = if (a <= b) anchor else focus
    val last = if (a <= b) focus else anchor
    return (minOf(a, b)..maxOf(a, b)).map { index ->
        val address = addresses[index]
        val length = inlinesAt(address).plainText().length
        EditorSelection(address.blockId,
            if (address == first.address) first.offset.coerceIn(0, length) else 0,
            if (address == last.address) last.offset.coerceIn(0, length) else length,
            address.row, address.column)
    }
}

fun NoteDocument.selectAllText(): EditorSelection? {
    val addresses = textAddresses()
    val first = addresses.firstOrNull() ?: return null
    val last = addresses.last()
    return selectionBetween(TextPosition(first, 0), TextPosition(last, inlinesAt(last).plainText().length))
}

fun NoteDocument.selectedText(selection: EditorSelection): String {
    val parts = selectionParts(selection)
    return parts.mapIndexed { index, part ->
        val previous = parts.getOrNull(index - 1)
        val separator = when {
            previous == null -> ""
            part.isTable && previous.blockId == part.blockId && previous.tableRow == part.tableRow -> "\t"
            else -> "\n"
        }
        separator + selectedInlines(part).orEmpty().plainText().let {
            it.substring(part.min.coerceIn(0, it.length), part.max.coerceIn(0, it.length))
        }
    }.joinToString("")
}

fun NoteDocument.replaceAcrossFields(selection: EditorSelection, text: String, marks: InlineMarks): EditorChange {
    val parts = selectionParts(selection)
    val first = parts.firstOrNull() ?: return EditorChange(this, selection)
    val last = parts.last()
    // Consecutive paragraphs merge at the selection boundary. Media and table structure stay in place.
    val startIndex = blockIndex(first.blockId)
    val endIndex = blockIndex(last.blockId)
    val onlyParagraphs = !first.isTable && !last.isTable &&
        blocks.subList(startIndex, endIndex + 1).all { it is TextBlock }
    val cleared = if (onlyParagraphs) {
        val head = block(first.blockId) as TextBlock
        val tail = block(last.blockId) as TextBlock
        val runs = (head.inlines.splitAt(first.min).first + tail.inlines.splitAt(last.max).second).coalesce()
        copy(blocks = blocks.take(startIndex) + head.copy(inlines = runs) + blocks.drop(endIndex + 1))
    } else {
        parts.fold(this) { current, part -> current.replaceSelectedText(part, "", marks).document }
    }
    return cleared.replaceSelectedText(first.copy(start = first.min, end = first.min), text, marks)
}

fun NoteDocument.selectedParagraphs(selection: EditorSelection): List<TextBlock> {
    val parts = selectionParts(selection)
    val ids = parts.filterIndexed { index, part -> !part.isTable &&
        !(selection.isCrossField && index == parts.lastIndex && part.end == 0) }
        .map { it.blockId }.toSet()
    return blocks.filterIsInstance<TextBlock>().filter { it.id in ids }
}

fun NoteDocument.mapSelectedParagraphs(selection: EditorSelection, transform: (TextBlock) -> TextBlock): EditorChange {
    val ids = selectedParagraphs(selection).map { it.id }.toSet()
    return EditorChange(copy(blocks = blocks.map { if (it is TextBlock && it.id in ids) transform(it) else it }), selection)
}
