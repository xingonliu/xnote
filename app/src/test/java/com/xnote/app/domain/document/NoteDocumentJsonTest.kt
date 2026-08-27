package com.xnote.app.domain.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// -- Tests

class NoteDocumentJsonTest {
    @Test
    fun roundTripsEveryBlockType() {
        val document = NoteDocument(
            blocks = listOf(
                TextBlock(
                    id = "text-1",
                    paragraphStyle = ParagraphStyle.Heading,
                    alignment = TextAlignment.Center,
                    listMarker = ListMarker.Checklist,
                    indent = 1,
                    quoted = true,
                    collapsed = true,
                    checked = true,
                    inlines = listOf(
                        InlineRun(
                            text = "标题",
                            bold = true,
                            italic = true,
                            underline = true,
                            strikethrough = true,
                            highlight = true,
                            linkUrl = "https://example.com",
                        ),
                    ),
                ),
                emptyTableBlock("table-1"),
                ImageBlock(id = "image-1", attachmentId = "att-image", layout = MediaLayout.Wrap),
                StickerBlock(id = "sticker-1", attachmentId = "att-sticker", libraryEntryId = "lib-1"),
                DrawingBlock(id = "drawing-1", attachmentId = "att-drawing", width = 320f, height = 240f),
            ),
        )
        val json = document.encodeToJson()
        val decoded = decodeNoteDocument(json)
        assertEquals(document, decoded)
        assertTrue(json.contains("\"type\":\"text\""))
        assertTrue(json.contains("\"type\":\"table\""))
        assertTrue(json.contains("\"type\":\"image\""))
        assertTrue(json.contains("\"type\":\"sticker\""))
        assertTrue(json.contains("\"type\":\"drawing\""))
        assertEquals(
            setOf("att-image", "att-sticker", "att-drawing"),
            decoded.attachmentIds(),
        )
    }
}
