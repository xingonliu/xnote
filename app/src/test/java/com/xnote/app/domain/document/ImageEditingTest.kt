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
        val copy = duplicate.block("copy") as ImageBlock
        val transformed = duplicate.replaceBlock(copy.transformed(1f, 90f, copy.offsetX, copy.offsetY))
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
    fun layersSwapOneImageAtATimeAndDeletingLastBlockKeepsText() {
        val original = NoteDocument(blocks = listOf(ImageBlock("a", "file"), TextBlock("text"),
            ImageBlock("b", "file"), ImageBlock("c", "file")))
        val forward = original.editImage("a", ImageAction.Forward, "unused").document
        assertEquals(original.blocks.map { it.id }, forward.blocks.map { it.id })
        assertEquals(listOf("b", "a", "c"), forward.blocks.filterIsInstance<ImageBlock>().sortedBy { it.zIndex }.map { it.id })
        val back = forward.editImage("a", ImageAction.Backward, "unused").document
        assertEquals(listOf("a", "b", "c"), back.blocks.filterIsInstance<ImageBlock>().sortedBy { it.zIndex }.map { it.id })
        val deleted = NoteDocument(blocks = listOf(ImageBlock("image", "file")))
            .editImage("image", ImageAction.Delete, "fallback")
        assertEquals(listOf(TextBlock("fallback")), deleted.document.blocks)
    }

    @Test
    fun polarHandleCombinesScaleAndRotationAcrossAngleSeam() {
        val image = ImageBlock("image", "file", rotationDegrees = 350f)
        val moved = image.transformFromHandle(100f, 0f, 0f, 200f)
        assertEquals(2f, moved.scale, 0.001f)
        assertEquals(80f, moved.rotationDegrees, 0.001f)
        val reverse = image.transformFromHandle(100f, 0f, 0f, -100f)
        assertEquals(260f, reverse.rotationDegrees, 0.001f)
    }

    @Test
    fun freeTranslationAndScaleLimitsSurviveSerialization() {
        val image = ImageBlock("image", "file")
        assertEquals(0.15f, image.transformed(0.01f, -450f, -700f, 900f).scale)
        val large = image.transformed(20f, -450f, -700f, 900f)
        assertEquals(4f, large.scale)
        assertEquals(270f, large.rotationDegrees)
        assertEquals(-700f, large.offsetX)
        assertEquals(900f, large.offsetY)
        val document = NoteDocument(blocks = listOf(large))
        assertEquals(document, decodeNoteDocument(document.encodeToJson()))
    }
}
