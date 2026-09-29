package com.xnote.app.domain.document

// -- Functions

fun PlacedMediaBlock.withPlacement(
    scale: Float = this.scale,
    rotationDegrees: Float = this.rotationDegrees,
    offsetX: Float = this.offsetX,
    offsetY: Float = this.offsetY,
    zIndex: Int = this.zIndex,
    layout: MediaLayout = this.layout,
): PlacedMediaBlock = when (this) {
    is ImageBlock -> copy(scale = scale, rotationDegrees = rotationDegrees, offsetX = offsetX, offsetY = offsetY, zIndex = zIndex, layout = layout)
    is StickerBlock -> copy(scale = scale, rotationDegrees = rotationDegrees, offsetX = offsetX, offsetY = offsetY, zIndex = zIndex, layout = layout)
}

fun PlacedMediaBlock.withAttachment(attachmentId: String): PlacedMediaBlock = when (this) {
    is ImageBlock -> copy(attachmentId = attachmentId)
    is StickerBlock -> copy(attachmentId = attachmentId, libraryEntryId = null)
}

fun PlacedMediaBlock.duplicate(id: String): PlacedMediaBlock = when (this) {
    is ImageBlock -> copy(id = id)
    is StickerBlock -> copy(id = id)
}
