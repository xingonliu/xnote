package com.xnote.app.domain.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// -- Type Definitions

@Serializable
enum class ParagraphStyle {
    @SerialName("body")
    Body,

    @SerialName("heading")
    Heading,

    @SerialName("subheading")
    Subheading,

    @SerialName("monospace")
    Monospace,
}

@Serializable
enum class TextAlignment {
    @SerialName("left")
    Left,

    @SerialName("center")
    Center,

    @SerialName("right")
    Right,
}

@Serializable
enum class ListMarker {
    @SerialName("none")
    None,

    @SerialName("bullet")
    Bullet,

    @SerialName("dash")
    Dash,

    @SerialName("numbered")
    Numbered,

    @SerialName("checklist")
    Checklist,
}

@Serializable
enum class MediaLayout {
    @SerialName("block")
    Block,

    @SerialName("wrap")
    Wrap,

    @SerialName("float")
    Float,
}

@Serializable
data class InlineRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val highlight: Boolean = false,
    val linkUrl: String? = null,
)

@Serializable
sealed interface NoteBlock {
    val id: String
}

@Serializable
@SerialName("text")
data class TextBlock(
    override val id: String,
    val paragraphStyle: ParagraphStyle = ParagraphStyle.Body,
    val alignment: TextAlignment = TextAlignment.Left,
    val listMarker: ListMarker = ListMarker.None,
    val indent: Int = 0,
    val quoted: Boolean = false,
    val collapsed: Boolean = false,
    val checked: Boolean = false,
    val inlines: List<InlineRun> = emptyList(),
) : NoteBlock

@Serializable
data class TableCell(
    val inlines: List<InlineRun> = emptyList(),
)

@Serializable
data class TableRow(
    val cells: List<TableCell> = emptyList(),
)

@Serializable
@SerialName("table")
data class TableBlock(
    override val id: String,
    val rows: List<TableRow> = emptyList(),
) : NoteBlock

@Serializable
@SerialName("image")
data class ImageBlock(
    override val id: String,
    val attachmentId: String,
    val layout: MediaLayout = MediaLayout.Block,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val zIndex: Int = 0,
) : NoteBlock

@Serializable
@SerialName("sticker")
data class StickerBlock(
    override val id: String,
    val attachmentId: String,
    val libraryEntryId: String? = null,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val zIndex: Int = 0,
) : NoteBlock

@Serializable
@SerialName("drawing")
data class DrawingBlock(
    override val id: String,
    val attachmentId: String,
    val width: Float,
    val height: Float,
) : NoteBlock

// -- Functions

fun List<InlineRun>.plainText(): String = joinToString(separator = "") { it.text }

fun emptyBodyBlock(id: String): TextBlock = TextBlock(id = id)

fun emptyTableBlock(id: String, rowCount: Int = 2, columnCount: Int = 2): TableBlock {
    val rows = List(rowCount.coerceAtLeast(1)) {
        TableRow(cells = List(columnCount.coerceAtLeast(1)) { TableCell() })
    }
    return TableBlock(id = id, rows = rows)
}
