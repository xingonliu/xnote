package com.xnote.app.domain.document

import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.newNoteId
import kotlinx.serialization.Serializable

// -- Type Definitions

@Serializable
data class NoteDocument(
    val schemaVersion: Int = CurrentSchemaVersion,
    val blocks: List<NoteBlock> = emptyList(),
)

// -- Constants

const val CurrentSchemaVersion = 1

// -- Functions

fun emptyNoteDocument(): NoteDocument = NoteDocument(
    schemaVersion = CurrentSchemaVersion,
    blocks = listOf(emptyBodyBlock(newNoteId())),
)

fun NoteDocument.attachmentIds(): Set<String> {
    val ids = linkedSetOf<String>()
    for (block in blocks) {
        when (block) {
            is ImageBlock -> ids += block.attachmentId
            is StickerBlock -> ids += block.attachmentId
            is DrawingBlock -> ids += block.attachmentId
            is TextBlock, is TableBlock -> Unit
        }
    }
    return ids
}

fun Note.referencedAttachmentIds(): Set<String> = document?.attachmentIds().orEmpty()
