package com.xnote.app.domain.document

// -- Type Definitions

data class EditorSelection(
    val blockId: String,
    val start: Int = 0,
    val end: Int = 0,
    val tableRow: Int? = null,
    val tableColumn: Int? = null,
) {
    val min: Int
        get() = minOf(start, end)

    val max: Int
        get() = maxOf(start, end)

    val isCollapsed: Boolean
        get() = start == end

    val isTable: Boolean
        get() = tableRow != null && tableColumn != null
}

data class EditorChange(
    val document: NoteDocument,
    val selection: EditorSelection,
)

// -- Constants

const val MaxTextIndent = 5

// -- Functions

fun NoteDocument.blockIndex(blockId: String): Int = blocks.indexOfFirst { it.id == blockId }

fun NoteDocument.block(blockId: String): NoteBlock? = blocks.firstOrNull { it.id == blockId }

fun NoteDocument.hiddenBlockIds(): Set<String> {
    val hidden = linkedSetOf<String>()
    var hidingFor: ParagraphStyle? = null
    for (block in blocks) {
        val style = (block as? TextBlock)?.paragraphStyle
        if (hidingFor != null) {
            val boundary = when (hidingFor) {
                ParagraphStyle.Heading -> style == ParagraphStyle.Heading
                ParagraphStyle.Subheading ->
                    style == ParagraphStyle.Heading || style == ParagraphStyle.Subheading
                else -> false
            }
            if (!boundary) {
                hidden += block.id
                continue
            }
            hidingFor = null
        }
        val text = block as? TextBlock ?: continue
        if (text.collapsed &&
            (text.paragraphStyle == ParagraphStyle.Heading ||
                text.paragraphStyle == ParagraphStyle.Subheading)
        ) {
            hidingFor = text.paragraphStyle
        }
    }
    return hidden
}

fun NoteDocument.numberedLabels(): Map<String, Int> {
    val labels = linkedMapOf<String, Int>()
    val counters = ArrayList<Int>()
    for (block in blocks) {
        val text = block as? TextBlock
        if (text == null || text.listMarker != ListMarker.Numbered) {
            counters.clear()
            continue
        }
        val indent = text.indent.coerceIn(0, MaxTextIndent)
        while (counters.size > indent + 1) {
            counters.removeAt(counters.lastIndex)
        }
        while (counters.size < indent + 1) {
            counters.add(0)
        }
        counters[indent] = counters[indent] + 1
        for (deeper in (indent + 1) until counters.size) {
            counters[deeper] = 0
        }
        labels[text.id] = counters[indent]
    }
    return labels
}

fun NoteDocument.replaceSelectedText(
    selection: EditorSelection,
    newText: String,
    marks: InlineMarks,
): EditorChange {
    if (selection.isTable) {
        return replaceTableCellText(selection, newText, marks)
    }
    val block = block(selection.blockId) as? TextBlock ?: return EditorChange(this, selection)
    if ('\n' in newText) {
        return replaceTextWithParagraphs(selection, newText, marks)
    }
    val updated = block.copy(
        inlines = block.inlines.replaceRange(selection.min, selection.max, marks.toRun(newText)),
    )
    val caret = selection.min + newText.length
    return EditorChange(
        document = replaceBlock(updated),
        selection = selection.copy(start = caret, end = caret),
    )
}

fun NoteDocument.splitAt(selection: EditorSelection, newBlockId: String): EditorChange {
    if (selection.isTable) {
        return insertNewlineInTableCell(selection)
    }
    val index = blockIndex(selection.blockId)
    val block = blocks.getOrNull(index) as? TextBlock ?: return EditorChange(this, selection)
    val emptyListExit = selection.isCollapsed &&
        selection.min == 0 &&
        block.inlines.plainText().isEmpty() &&
        (block.listMarker != ListMarker.None || block.quoted || block.indent > 0)
    if (emptyListExit) {
        return exitStructuredBlock(block)
    }
    val (left, right) = block.inlines.splitAt(selection.min)
    val leftBlock = block.copy(inlines = left)
    val newStyle = when (block.paragraphStyle) {
        ParagraphStyle.Heading, ParagraphStyle.Subheading -> ParagraphStyle.Body
        ParagraphStyle.Body, ParagraphStyle.Monospace -> block.paragraphStyle
    }
    val rightBlock = TextBlock(
        id = newBlockId,
        paragraphStyle = newStyle,
        alignment = block.alignment,
        listMarker = block.listMarker,
        indent = block.indent,
        quoted = block.quoted,
        collapsed = false,
        checked = false,
        inlines = right,
    )
    val updated = blocks.toMutableList()
    updated[index] = leftBlock
    updated.add(index + 1, rightBlock)
    return EditorChange(
        document = copy(blocks = updated),
        selection = EditorSelection(blockId = newBlockId, start = 0, end = 0),
    )
}

fun NoteDocument.deleteBackward(selection: EditorSelection): EditorChange {
    if (!selection.isCollapsed) {
        return replaceSelectedText(selection, "", InlineMarks())
    }
    if (selection.isTable) {
        return deleteBackwardInTableCell(selection)
    }
    if (selection.min > 0) {
        return replaceSelectedText(selection.copy(start = selection.min - 1, end = selection.min), "", InlineMarks())
    }
    val index = blockIndex(selection.blockId)
    val block = blocks.getOrNull(index) as? TextBlock ?: return EditorChange(this, selection)
    when {
        block.indent > 0 -> {
            val updated = block.copy(indent = block.indent - 1)
            return EditorChange(replaceBlock(updated), selection)
        }
        block.listMarker != ListMarker.None -> {
            val updated = block.copy(listMarker = ListMarker.None, checked = false)
            return EditorChange(replaceBlock(updated), selection)
        }
        block.quoted -> {
            return EditorChange(replaceBlock(block.copy(quoted = false)), selection)
        }
    }
    if (index <= 0) return EditorChange(this, selection)
    val previous = blocks[index - 1]
    if (previous !is TextBlock) {
        if (block.inlines.plainText().isEmpty() && blocks.size > 1) {
            val remaining = blocks.toMutableList()
            remaining.removeAt(index)
            val fallback = remaining.lastOrNull()?.id ?: selection.blockId
            return EditorChange(
                document = copy(blocks = remaining).ensureNotEmpty(selection.blockId),
                selection = EditorSelection(blockId = fallback),
            )
        }
        return EditorChange(this, selection)
    }
    val caret = previous.inlines.plainText().length
    val merged = previous.copy(inlines = (previous.inlines + block.inlines).coalesce())
    val remaining = blocks.toMutableList()
    remaining[index - 1] = merged
    remaining.removeAt(index)
    return EditorChange(
        document = copy(blocks = remaining),
        selection = EditorSelection(blockId = previous.id, start = caret, end = caret),
    )
}

fun NoteDocument.setParagraphStyle(selection: EditorSelection, style: ParagraphStyle): EditorChange {
    val block = selectedTextBlock(selection) ?: return EditorChange(this, selection)
    val updated = block.copy(
        paragraphStyle = style,
        collapsed = if (
            style == ParagraphStyle.Heading || style == ParagraphStyle.Subheading
        ) {
            block.collapsed
        } else {
            false
        },
        listMarker = if (style == ParagraphStyle.Monospace) ListMarker.None else block.listMarker,
        checked = if (style == ParagraphStyle.Monospace) false else block.checked,
    )
    return EditorChange(replaceBlock(updated), selection)
}

fun NoteDocument.setListMarker(selection: EditorSelection, marker: ListMarker): EditorChange {
    val block = selectedTextBlock(selection) ?: return EditorChange(this, selection)
    val next = if (block.listMarker == marker) ListMarker.None else marker
    val updated = block.copy(
        listMarker = next,
        checked = if (next == ListMarker.Checklist) block.checked else false,
        paragraphStyle = if (next == ListMarker.None) {
            block.paragraphStyle
        } else {
            ParagraphStyle.Body
        },
    )
    return EditorChange(replaceBlock(updated), selection)
}

fun NoteDocument.toggleQuoted(selection: EditorSelection): EditorChange {
    val block = selectedTextBlock(selection) ?: return EditorChange(this, selection)
    return EditorChange(replaceBlock(block.copy(quoted = !block.quoted)), selection)
}

fun NoteDocument.changeIndent(selection: EditorSelection, delta: Int): EditorChange {
    val block = selectedTextBlock(selection) ?: return EditorChange(this, selection)
    val indent = (block.indent + delta).coerceIn(0, MaxTextIndent)
    return EditorChange(replaceBlock(block.copy(indent = indent)), selection)
}

fun NoteDocument.setAlignment(selection: EditorSelection, alignment: TextAlignment): EditorChange {
    val block = selectedTextBlock(selection) ?: return EditorChange(this, selection)
    return EditorChange(replaceBlock(block.copy(alignment = alignment)), selection)
}

fun NoteDocument.toggleChecked(selection: EditorSelection): EditorChange {
    val block = selectedTextBlock(selection) ?: return EditorChange(this, selection)
    if (block.listMarker != ListMarker.Checklist) return EditorChange(this, selection)
    return EditorChange(replaceBlock(block.copy(checked = !block.checked)), selection)
}

fun NoteDocument.toggleCollapsed(selection: EditorSelection): EditorChange {
    val block = selectedTextBlock(selection) ?: return EditorChange(this, selection)
    if (block.paragraphStyle != ParagraphStyle.Heading &&
        block.paragraphStyle != ParagraphStyle.Subheading
    ) {
        return EditorChange(this, selection)
    }
    return EditorChange(replaceBlock(block.copy(collapsed = !block.collapsed)), selection)
}

fun NoteDocument.applyInlineMark(
    selection: EditorSelection,
    mark: InlineMark,
    typingMarks: InlineMarks,
): Pair<EditorChange, InlineMarks> {
    val inlines = selectedInlines(selection) ?: return EditorChange(this, selection) to typingMarks
    if (selection.isCollapsed) {
        val enable = !typingMarks.has(mark)
        return EditorChange(this, selection) to typingMarks.toggle(mark, enable)
    }
    val enable = !inlines.rangeHasMark(selection.min, selection.max, mark)
    val updated = inlines.mapRange(selection.min, selection.max) { run ->
        run.withMarks(run.marks().toggle(mark, enable))
    }
    return EditorChange(replaceSelectedInlines(selection, updated), selection) to typingMarks.toggle(mark, enable)
}

fun NoteDocument.setLink(
    selection: EditorSelection,
    url: String?,
    typingMarks: InlineMarks,
): Pair<EditorChange, InlineMarks> {
    val inlines = selectedInlines(selection) ?: return EditorChange(this, selection) to typingMarks
    val normalized = url?.trim()?.takeIf { it.isNotEmpty() }
    if (selection.isCollapsed) {
        if (normalized == null) {
            return EditorChange(this, selection) to typingMarks.copy(linkUrl = null)
        }
        val linkedMarks = typingMarks.copy(linkUrl = normalized)
        return replaceSelectedText(selection, normalized, linkedMarks) to linkedMarks
    }
    val updated = inlines.mapRange(selection.min, selection.max) { run ->
        run.copy(linkUrl = normalized)
    }
    return EditorChange(replaceSelectedInlines(selection, updated), selection) to
        typingMarks.copy(linkUrl = normalized)
}

fun NoteDocument.insertTable(
    selection: EditorSelection,
    tableId: String,
    firstCellSelection: EditorSelection = EditorSelection(
        blockId = tableId,
        tableRow = 0,
        tableColumn = 0,
    ),
): EditorChange {
    val table = emptyTableBlock(tableId)
    val index = blockIndex(selection.blockId)
    val updated = blocks.toMutableList()
    if (index < 0) {
        updated += table
    } else {
        updated.add(index + 1, table)
    }
    return EditorChange(copy(blocks = updated), firstCellSelection)
}

fun NoteDocument.insertTableRow(selection: EditorSelection, afterRow: Int): EditorChange {
    val table = selectedTable(selection) ?: return EditorChange(this, selection)
    val columnCount = table.columnCount().coerceAtLeast(1)
    val rows = table.rows.toMutableList()
    val insertAt = (afterRow + 1).coerceIn(0, rows.size)
    rows.add(insertAt, TableRow(cells = List(columnCount) { TableCell() }))
    val caret = selection.copy(tableRow = insertAt, tableColumn = selection.tableColumn ?: 0, start = 0, end = 0)
    return EditorChange(replaceBlock(table.copy(rows = rows)), caret)
}

fun NoteDocument.insertTableColumn(selection: EditorSelection, afterColumn: Int): EditorChange {
    val table = selectedTable(selection) ?: return EditorChange(this, selection)
    val insertAt = (afterColumn + 1).coerceIn(0, table.columnCount())
    val rows = table.rows.map { row ->
        val cells = row.cells.toMutableList()
        while (cells.size < insertAt) cells += TableCell()
        cells.add(insertAt, TableCell())
        row.copy(cells = cells)
    }
    val caret = selection.copy(tableColumn = insertAt, tableRow = selection.tableRow ?: 0, start = 0, end = 0)
    return EditorChange(replaceBlock(table.copy(rows = rows)), caret)
}

fun NoteDocument.deleteTableRow(selection: EditorSelection, row: Int, fallbackTextId: String): EditorChange {
    val table = selectedTable(selection) ?: return EditorChange(this, selection)
    if (table.rows.size <= 1) {
        return deleteBlock(table.id, fallbackTextId)
    }
    val rows = table.rows.filterIndexed { index, _ -> index != row }
    val nextRow = row.coerceAtMost(rows.lastIndex).coerceAtLeast(0)
    return EditorChange(
        replaceBlock(table.copy(rows = rows)),
        selection.copy(tableRow = nextRow, start = 0, end = 0),
    )
}

fun NoteDocument.deleteTableColumn(selection: EditorSelection, column: Int, fallbackTextId: String): EditorChange {
    val table = selectedTable(selection) ?: return EditorChange(this, selection)
    if (table.columnCount() <= 1) {
        return deleteBlock(table.id, fallbackTextId)
    }
    val rows = table.rows.map { row ->
        row.copy(cells = row.cells.filterIndexed { index, _ -> index != column })
    }
    val nextColumn = column.coerceAtMost((table.columnCount() - 2).coerceAtLeast(0)).coerceAtLeast(0)
    return EditorChange(
        replaceBlock(table.copy(rows = rows)),
        selection.copy(tableColumn = nextColumn, start = 0, end = 0),
    )
}

fun NoteDocument.deleteBlock(blockId: String, fallbackTextId: String): EditorChange {
    val remaining = blocks.filterNot { it.id == blockId }
    val document = copy(blocks = remaining).ensureNotEmpty(fallbackTextId)
    val nextId = document.blocks.first().id
    return EditorChange(document, EditorSelection(blockId = nextId))
}

fun NoteDocument.ensureNotEmpty(fallbackTextId: String): NoteDocument {
    if (blocks.isNotEmpty()) return this
    return copy(blocks = listOf(emptyBodyBlock(fallbackTextId)))
}

fun TableBlock.columnCount(): Int = rows.maxOfOrNull { it.cells.size } ?: 0

fun TableBlock.cell(row: Int, column: Int): TableCell? = rows.getOrNull(row)?.cells?.getOrNull(column)

fun TableBlock.updateCell(row: Int, column: Int, inlines: List<InlineRun>): TableBlock {
    if (row !in rows.indices) return this
    val rows = this.rows.mapIndexed { rowIndex, tableRow ->
        if (rowIndex != row) {
            tableRow
        } else {
            tableRow.copy(
                cells = tableRow.cells.mapIndexed { columnIndex, cell ->
                    if (columnIndex != column) cell else cell.copy(inlines = inlines)
                },
            )
        }
    }
    return copy(rows = rows)
}

private fun NoteDocument.replaceBlock(block: NoteBlock): NoteDocument {
    return copy(blocks = blocks.map { current -> if (current.id == block.id) block else current })
}

private fun NoteDocument.selectedTextBlock(selection: EditorSelection): TextBlock? {
    if (selection.isTable) return null
    return block(selection.blockId) as? TextBlock
}

private fun NoteDocument.selectedTable(selection: EditorSelection): TableBlock? {
    return block(selection.blockId) as? TableBlock
}

private fun NoteDocument.selectedInlines(selection: EditorSelection): List<InlineRun>? {
    if (selection.isTable) {
        val table = selectedTable(selection) ?: return null
        val row = selection.tableRow ?: return null
        val column = selection.tableColumn ?: return null
        return table.cell(row, column)?.inlines
    }
    return selectedTextBlock(selection)?.inlines
}

private fun NoteDocument.replaceSelectedInlines(
    selection: EditorSelection,
    inlines: List<InlineRun>,
): NoteDocument {
    if (selection.isTable) {
        val table = selectedTable(selection) ?: return this
        val row = selection.tableRow ?: return this
        val column = selection.tableColumn ?: return this
        return replaceBlock(table.updateCell(row, column, inlines))
    }
    val block = selectedTextBlock(selection) ?: return this
    return replaceBlock(block.copy(inlines = inlines))
}

private fun NoteDocument.replaceTableCellText(
    selection: EditorSelection,
    newText: String,
    marks: InlineMarks,
): EditorChange {
    val table = selectedTable(selection) ?: return EditorChange(this, selection)
    val row = selection.tableRow ?: return EditorChange(this, selection)
    val column = selection.tableColumn ?: return EditorChange(this, selection)
    val cell = table.cell(row, column) ?: return EditorChange(this, selection)
    val updated = cell.inlines.replaceRange(selection.min, selection.max, marks.toRun(newText))
    val caret = selection.min + newText.length
    return EditorChange(
        document = replaceBlock(table.updateCell(row, column, updated)),
        selection = selection.copy(start = caret, end = caret),
    )
}

private fun NoteDocument.insertNewlineInTableCell(selection: EditorSelection): EditorChange {
    return replaceTableCellText(selection, "\n", selectedInlines(selection)?.marksAt(selection.min) ?: InlineMarks())
}

private fun NoteDocument.deleteBackwardInTableCell(selection: EditorSelection): EditorChange {
    if (selection.min > 0) {
        return replaceTableCellText(
            selection.copy(start = selection.min - 1, end = selection.min),
            "",
            InlineMarks(),
        )
    }
    return EditorChange(this, selection)
}

private fun NoteDocument.replaceTextWithParagraphs(
    selection: EditorSelection,
    newText: String,
    marks: InlineMarks,
): EditorChange {
    val lines = newText.split('\n')
    var current = replaceSelectedText(selection, lines.first(), marks)
    var caret = current.selection
    for (index in 1 until lines.size) {
        current = current.document.splitAt(caret, com.xnote.app.domain.model.newNoteId())
        caret = current.selection
        if (lines[index].isNotEmpty()) {
            current = current.document.replaceSelectedText(caret, lines[index], marks)
            caret = current.selection
        }
    }
    return EditorChange(current.document, caret)
}

private fun NoteDocument.exitStructuredBlock(block: TextBlock): EditorChange {
    val updated = when {
        block.indent > 0 -> block.copy(indent = block.indent - 1)
        block.listMarker != ListMarker.None -> block.copy(listMarker = ListMarker.None, checked = false)
        else -> block.copy(quoted = false)
    }
    return EditorChange(replaceBlock(updated), EditorSelection(blockId = block.id))
}
