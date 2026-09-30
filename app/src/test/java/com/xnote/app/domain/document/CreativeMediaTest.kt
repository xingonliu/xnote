package com.xnote.app.domain.document

import com.xnote.app.domain.agent.AgentEditValidation
import com.xnote.app.domain.agent.validateAgentEdit
import org.junit.Assert.*
import org.junit.Test

// -- Type Definitions

class CreativeMediaTest {
    // -- Functions

    @Test fun stickerRoundTripsAndStaysProtectedFromAgentWrites() {
        val sticker = StickerBlock("sticker", "alpha", "library", layout = MediaLayout.Wrap, rotationDegrees = 37f)
        val document = NoteDocument(blocks = listOf(sticker))
        assertEquals(document, decodeNoteDocument(document.encodeToJson()))
        assertEquals(setOf("alpha"), document.attachmentIds())
        val changed = document.copy(blocks = listOf(sticker.copy(attachmentId = "other")))
        assertEquals(AgentEditValidation.ProtectedContent, validateAgentEdit(document, changed, "version", "version"))
    }

    @Test fun sharedLayersPreserveIndependentStickerInstancesAndSourceAttachment() {
        val image = ImageBlock("image", "image-file")
        val sticker = StickerBlock("sticker", "alpha", "library", zIndex = 1)
        val document = NoteDocument(blocks = listOf(image, sticker))
        val moved = document.editMedia("sticker", MediaAction.Backward, "unused").document
        assertEquals(0, (moved.block("sticker") as StickerBlock).zIndex)
        assertEquals(1, (moved.block("image") as ImageBlock).zIndex)
        val copied = moved.editMedia("sticker", MediaAction.Duplicate, "copy").document.block("copy") as StickerBlock
        assertEquals("alpha", copied.attachmentId)
        assertEquals("library", copied.libraryEntryId)
        assertEquals(16f, copied.offsetX)
        assertEquals(0.15f, copied.transformed(0f, -30f, 40f, 20f).scale)
    }

    @Test fun flowGroupsStopAtTableAndRetainEveryBlock() {
        val blocks = listOf(ImageBlock("wrap", "i", MediaLayout.Wrap), TextBlock("a"),
            StickerBlock("float", "s", layout = MediaLayout.Float), TextBlock("b"),
            emptyTableBlock("table"), TextBlock("c"))
        val groups = blocks.flowGroups()
        assertEquals(blocks, groups.flatMap { it.blocks })
        assertEquals(3, groups.size)
        assertTrue(groups.first().wraps)
        assertFalse(groups.last().wraps)
        val geometry = (blocks.first() as PlacedMediaBlock).geometry(400f, 1f)
        assertEquals(168f, geometry.width)
        assertEquals(168f, geometry.right)
    }

    @Test fun stickerInsertionSplitsInlineStylesAtCaret() {
        val doc = NoteDocument(blocks = listOf(TextBlock("text", inlines = listOf(InlineRun("前后", bold = true)))))
        val sticker = StickerBlock("s", "file")
        val next = doc.insertMedia(EditorSelection("text", 1, 1), sticker, "tail").document
        assertEquals("前", (next.blocks[0] as TextBlock).inlines.plainText())
        assertEquals(sticker, next.blocks[1])
        assertEquals(listOf(InlineRun("后", bold = true)), (next.blocks[2] as TextBlock).inlines)
    }
}
