package com.xnote.app.domain.model

// -- Type Definitions

enum class AttachmentKind {
    Image,
    Sticker,
    Drawing,
}

data class Attachment(
    val id: String,
    val kind: AttachmentKind,
    val mimeType: String,
    val originalFileName: String?,
    val relativePath: String,
    val byteSize: Long,
    val widthPx: Int?,
    val heightPx: Int?,
    val createdAtEpochMs: Long,
)
