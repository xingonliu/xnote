package com.xnote.app.domain.document

import org.junit.Assert.*
import org.junit.Test

// -- Tests

class ImageEditingTest {
    @Test
    fun insertionSplitsStyledTextWithoutDeletingSelection() {
        val original = NoteDocument(blocks = listOf(TextBlock("text", inlines = listOf(InlineRun("前后", bold = true)))))
        val result = original.insertImage(EditorSelection("text", 1, 2), ImageBlock("image", "file"), "tail")
        assertEquals(listOf("text", "image", "tail"), result.document.blocks.map { it.id })
        assertEquals(listOf(InlineRun("前", bold = true)), (result.document.blocks[0] as TextBlock).inlines)
        assertEquals(listOf(InlineRun("后", bold = true)), (result.document.blocks[2] as TextBlock).inlines)
        assertEquals("image", result.selection.blockId)
    }

    @Test
    fun imageAfterTableIsNeverNestedAndHasEditableTrailingParagraph() {
        val table = emptyTableBlock("table")
        val result = NoteDocument(blocks = listOf(table)).insertImage(
            EditorSelection("table", tableRow = 0, tableColumn = 0), ImageBlock("image", "file"), "tail",
        )
        assertEquals(table, result.document.blocks.first())
        assertTrue(result.document.blocks.last() is TextBlock)
    }

    @Test
    fun duplicateSharesAttachmentButHasIndependentTransformAndHistory() {
        val original = NoteDocument(blocks = listOf(ImageBlock("image", "file")))
        val duplicate = original.editImage("image", ImageAction.Duplicate, "copy").document
        val transformed = duplicate.editImage("copy", ImageAction.RotateRight, "unused").document
        assertEquals(0f, (transformed.block("image") as ImageBlock).rotationDegrees)
        assertEquals(90f, (transformed.block("copy") as ImageBlock).rotationDegrees)
        assertEquals(setOf("file"), transformed.attachmentIds())
        assertEquals(transformed, decodeNoteDocument(transformed.encodeToJson()))
        val history = EditorHistory()
        val before = EditorSnapshot("", original, EditorSelection("image"))
        val after = EditorSnapshot("", transformed, EditorSelection("copy"))
        history.capture(before)
        assertEquals(before, history.undo(after))
        assertEquals(after, history.redo(before))
    }

    @Test
    fun reorderBoundsLayersScaleAndDeletingLastBlockRemainValid() {
        var document = NoteDocument(blocks = listOf(ImageBlock("image", "file"), TextBlock("text")))
        assertEquals(document, document.editImage("image", ImageAction.MoveUp, "unused").document)
        document = document.editImage("image", ImageAction.MoveDown, "unused").document
        assertEquals(listOf("text", "image"), document.blocks.map { it.id })
        repeat(30) { document = document.editImage("image", ImageAction.Smaller, "unused").document }
        assertEquals(0.2f, (document.block("image") as ImageBlock).scale)
        document = document.editImage("image", ImageAction.Forward, "unused").document
        assertEquals(1, (document.block("image") as ImageBlock).zIndex)
        val deleted = NoteDocument(blocks = listOf(ImageBlock("image", "file")))
            .editImage("image", ImageAction.Delete, "fallback")
        assertEquals(listOf(TextBlock("fallback")), deleted.document.blocks)
        assertEquals("fallback", deleted.selection.blockId)
    }
}
