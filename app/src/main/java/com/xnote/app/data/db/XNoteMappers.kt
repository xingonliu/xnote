package com.xnote.app.data.db

import com.xnote.app.domain.document.attachmentIds
import com.xnote.app.domain.document.decodeNoteDocument
import com.xnote.app.domain.document.encodeToJson
import com.xnote.app.domain.model.Attachment
import com.xnote.app.domain.model.AttachmentKind
import com.xnote.app.domain.model.encode
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteRevision
import com.xnote.app.domain.model.Notebook
import com.xnote.app.domain.model.RevisionReason
import com.xnote.app.domain.model.parseBackgroundKey

// -- Functions

fun NotebookEntity.toDomain(): Notebook = Notebook(
    id = id,
    name = name,
    sortIndex = sortIndex,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    color = color,
    icon = icon,
)

fun Notebook.toEntity(): NotebookEntity = NotebookEntity(
    id = id,
    name = name,
    sortIndex = sortIndex,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    color = color,
    icon = icon,
)

fun NoteEntity.toDomain(): Note = Note(
    id = id,
    notebookId = notebookId,
    title = title,
    document = decodeNoteDocument(documentJson),
    backgroundKey = parseBackgroundKey(backgroundKey),
    sortIndex = sortIndex,
    visibleCharacterCount = visibleCharacterCount,
    latinWordCount = latinWordCount,
    summary = summary,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    deletedAtEpochMs = deletedAtEpochMs,
    originalNotebookName = originalNotebookName,
)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    notebookId = notebookId,
    title = title,
    documentJson = document.encodeToJson(),
    backgroundKey = backgroundKey?.encode(),
    sortIndex = sortIndex,
    visibleCharacterCount = visibleCharacterCount,
    latinWordCount = latinWordCount,
    summary = summary,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    deletedAtEpochMs = deletedAtEpochMs,
    originalNotebookName = originalNotebookName,
)

fun NoteRevisionEntity.toDomain(): NoteRevision = NoteRevision(
    id = id,
    noteId = noteId,
    reason = reason.toRevisionReason(),
    title = title,
    document = decodeNoteDocument(documentJson),
    createdAtEpochMs = createdAtEpochMs,
)

fun NoteRevision.toEntity(): NoteRevisionEntity = NoteRevisionEntity(
    id = id,
    noteId = noteId,
    reason = reason.storageValue(),
    title = title,
    documentJson = document.encodeToJson(),
    createdAtEpochMs = createdAtEpochMs,
)

fun AttachmentEntity.toDomain(): Attachment = Attachment(
    id = id,
    kind = kind.toAttachmentKind(),
    mimeType = mimeType,
    originalFileName = originalFileName,
    relativePath = relativePath,
    byteSize = byteSize,
    widthPx = widthPx,
    heightPx = heightPx,
    createdAtEpochMs = createdAtEpochMs,
)

fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id,
    kind = kind.storageValue(),
    mimeType = mimeType,
    originalFileName = originalFileName,
    relativePath = relativePath,
    byteSize = byteSize,
    widthPx = widthPx,
    heightPx = heightPx,
    createdAtEpochMs = createdAtEpochMs,
)

fun RevisionReason.storageValue(): String = when (this) {
    RevisionReason.AgentPolish -> "agent_polish"
}

fun String.toRevisionReason(): RevisionReason = RevisionReason.AgentPolish

fun AttachmentKind.storageValue(): String = when (this) {
    AttachmentKind.Image -> "image"
    AttachmentKind.Sticker -> "sticker"
    AttachmentKind.Drawing -> "drawing"
}

fun String.toAttachmentKind(): AttachmentKind = when (this) {
    "sticker" -> AttachmentKind.Sticker
    "drawing" -> AttachmentKind.Drawing
    else -> AttachmentKind.Image
}

fun NoteRevision.referencedAttachmentIds(): Set<String> = document.attachmentIds()
