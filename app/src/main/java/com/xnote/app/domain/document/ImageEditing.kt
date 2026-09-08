package com.xnote.app.domain.document

// -- Type Definitions

enum class ImageAction { Smaller, Larger, RotateLeft, RotateRight, MoveUp, MoveDown, Backward, Forward, Reset, Duplicate, Delete }

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
    val index = blockIndex(id)
    val updated = blocks.toMutableList()
    when (action) {
        ImageAction.Delete -> return deleteBlock(id, newId)
        ImageAction.Duplicate -> {
            updated.add(index + 1, image.copy(id = newId))
            return EditorChange(copy(blocks = updated), EditorSelection(newId))
        }
        ImageAction.MoveUp, ImageAction.MoveDown -> {
            val target = index + if (action == ImageAction.MoveUp) -1 else 1
            if (target in updated.indices) {
                updated.removeAt(index)
                updated.add(target, image)
            }
            return EditorChange(copy(blocks = updated), EditorSelection(id))
        }
        else -> Unit
    }
    val next = when (action) {
        ImageAction.Smaller -> image.copy(scale = (image.scale / 1.2f).coerceAtLeast(0.2f))
        ImageAction.Larger -> image.copy(scale = (image.scale * 1.2f).coerceAtMost(3f))
        ImageAction.RotateLeft -> image.copy(rotationDegrees = (image.rotationDegrees - 90f) % 360f)
        ImageAction.RotateRight -> image.copy(rotationDegrees = (image.rotationDegrees + 90f) % 360f)
        ImageAction.Backward -> image.copy(zIndex = (image.zIndex - 1).coerceAtLeast(-1000))
        ImageAction.Forward -> image.copy(zIndex = (image.zIndex + 1).coerceAtMost(1000))
        ImageAction.Reset -> image.copy(scale = 1f, rotationDegrees = 0f, offsetX = 0f, offsetY = 0f, zIndex = 0)
    }
    return EditorChange(replaceBlock(next), EditorSelection(id))
}
