package com.xnote.app.domain.document

// -- Type Definitions

data class EditorSnapshot(
    val title: String,
    val document: NoteDocument,
    val selection: EditorSelection,
)

class EditorHistory(
    private val limit: Int = 50,
) {
    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private var coalesceKey: String? = null

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun capture(snapshot: EditorSnapshot, key: String? = null) {
        if (key != null && key == coalesceKey) return
        coalesceKey = key
        undoStack.addLast(snapshot)
        while (undoStack.size > limit) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(current: EditorSnapshot): EditorSnapshot? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        coalesceKey = null
        return previous
    }

    fun redo(current: EditorSnapshot): EditorSnapshot? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        coalesceKey = null
        return next
    }
}
