package com.xnote.app.domain.document

// -- Type Definitions

enum class MediaAction { Backward, Forward, Reset, Duplicate, Delete }

// -- Functions

fun NoteDocument.insertMedia(selection: EditorSelection, media: NoteBlock, trailingTextId: String): EditorChange {
    if (selection.isCrossField) {
        val start = selectionParts(selection).firstOrNull() ?: return EditorChange(this, selection)
        return insertMedia(start.copy(end = start.start), media, trailingTextId)
    }
    val index = blockIndex(selection.blockId)
    val text = blocks.getOrNull(index) as? TextBlock
    val updated = blocks.toMutableList()
    if (text != null && !selection.isTable) {
        // Insertion preserves selected text and splits at the caret, including inline styles.
        val (left, right) = text.inlines.splitAt(selection.min)
        updated[index] = text.copy(inlines = left, collapsed = false)
        updated.add(index + 1, media)
        updated.add(index + 2, text.copy(id = trailingTextId, inlines = right, collapsed = false))
    } else {
        val insertion = if (index < 0) updated.size else index + 1
        updated.add(insertion, media)
        updated.add(insertion + 1, TextBlock(trailingTextId))
    }
    return EditorChange(copy(blocks = updated), EditorSelection(media.id))
}

fun NoteDocument.editMedia(id: String, action: MediaAction, newId: String): EditorChange {
    val media = block(id) as? PlacedMediaBlock ?: return EditorChange(this, EditorSelection(id))
    return when (action) {
        MediaAction.Delete -> deleteBlock(id, newId)
        MediaAction.Duplicate -> {
            val updated = blocks.toMutableList()
            val top = blocks.filterIsInstance<PlacedMediaBlock>().maxOfOrNull { it.zIndex } ?: 0
            updated.add(blockIndex(id) + 1, media.duplicate(newId).withPlacement(
                offsetX = media.offsetX + 16f, offsetY = media.offsetY + 16f, zIndex = top + 1))
            EditorChange(copy(blocks = updated), EditorSelection(newId))
        }
        MediaAction.Backward, MediaAction.Forward -> {
            val layers = blocks.filterIsInstance<PlacedMediaBlock>().sortedBy { it.zIndex }.toMutableList()
            val index = layers.indexOfFirst { it.id == id }
            val target = index + if (action == MediaAction.Forward) 1 else -1
            if (target !in layers.indices) return EditorChange(this, EditorSelection(id))
            layers[index] = layers[target].also { layers[target] = media }
            val order = layers.mapIndexed { layer, item -> item.id to layer }.toMap()
            EditorChange(copy(blocks = blocks.map { if (it is PlacedMediaBlock) it.withPlacement(zIndex = order.getValue(it.id)) else it }),
                EditorSelection(id))
        }
        MediaAction.Reset -> EditorChange(replaceBlock(media.withPlacement(scale = 1f, rotationDegrees = 0f,
            offsetX = 0f, offsetY = 0f)), EditorSelection(id))
    }
}
