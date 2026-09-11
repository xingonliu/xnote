package com.xnote.app.domain.document

// -- Type Definitions

enum class ImageAction { Backward, Forward, Reset, Duplicate, Delete }

// -- Functions

fun NoteDocument.insertImage(selection: EditorSelection, image: ImageBlock, trailingTextId: String): EditorChange {
    val index = blockIndex(selection.blockId)
    val text = blocks.getOrNull(index) as? TextBlock
    val updated = blocks.toMutableList()
    if (text != null && !selection.isTable) {
        // Insertion preserves selected text and splits at the caret, including inline styles.
        val (left, right) = text.inlines.splitAt(selection.min)
        updated[index] = text.copy(inlines = left, collapsed = false)
        updated.add(index + 1, image)
        updated.add(index + 2, text.copy(id = trailingTextId, inlines = right, collapsed = false))
    } else {
        val insertion = if (index < 0) updated.size else index + 1
        updated.add(insertion, image)
        updated.add(insertion + 1, TextBlock(trailingTextId))
    }
    return EditorChange(copy(blocks = updated), EditorSelection(image.id))
}

fun NoteDocument.editImage(id: String, action: ImageAction, newId: String): EditorChange {
    val image = block(id) as? ImageBlock ?: return EditorChange(this, EditorSelection(id))
    return when (action) {
        ImageAction.Delete -> deleteBlock(id, newId)
        ImageAction.Duplicate -> {
            val updated = blocks.toMutableList()
            val top = blocks.filterIsInstance<ImageBlock>().maxOfOrNull { it.zIndex } ?: 0
            updated.add(blockIndex(id) + 1, image.copy(id = newId,
                offsetX = image.offsetX + 16f, offsetY = image.offsetY + 16f, zIndex = top + 1))
            EditorChange(copy(blocks = updated), EditorSelection(newId))
        }
        ImageAction.Backward, ImageAction.Forward -> {
            val layers = blocks.filterIsInstance<ImageBlock>().sortedBy { it.zIndex }.toMutableList()
            val index = layers.indexOfFirst { it.id == id }
            val target = index + if (action == ImageAction.Forward) 1 else -1
            if (target !in layers.indices) return EditorChange(this, EditorSelection(id))
            layers[index] = layers[target].also { layers[target] = image }
            val order = layers.mapIndexed { layer, item -> item.id to layer }.toMap()
            EditorChange(copy(blocks = blocks.map { if (it is ImageBlock) it.copy(zIndex = order.getValue(it.id)) else it }),
                EditorSelection(id))
        }
        ImageAction.Reset -> EditorChange(replaceBlock(image.copy(scale = 1f, rotationDegrees = 0f,
            offsetX = 0f, offsetY = 0f)), EditorSelection(id))
    }
}
