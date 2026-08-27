package com.xnote.app.data.db

import com.xnote.app.domain.document.attachmentIds
import com.xnote.app.domain.document.decodeNoteDocument
import com.xnote.app.domain.document.encodeToJson
import com.xnote.app.domain.model.Attachment
import com.xnote.app.domain.model.AttachmentKind
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteKind
import com.xnote.app.domain.model.NoteRevision
import com.xnote.app.domain.model.Notebook
import com.xnote.app.domain.model.RevisionReason

// -- Functions

fun NotebookEntity.toDomain(): Notebook = Notebook(
    id = id,
    name = name,
    sortIndex = sortIndex,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun Notebook.toEntity(): NotebookEntity = NotebookEntity(
    id = id,
    name = name,
    sortIndex = sortIndex,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun NoteEntity.toDomain(): Note = Note(
    id = id,
    notebookId = notebookId,
    title = title,
    kind = kind.toNoteKind(),
    document = documentJson?.let(::decodeNoteDocument),
    markdownText = markdownText,
    backgroundKey = backgroundKey,
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
    kind = kind.storageValue(),
    documentJson = document?.encodeToJson(),
    markdownText = markdownText,
    backgroundKey = backgroundKey,
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
    kind = kind.toNoteKind(),
    title = title,
    document = documentJson?.let(::decodeNoteDocument),
    markdownText = markdownText,
    createdAtEpochMs = createdAtEpochMs,
)

fun NoteRevision.toEntity(): NoteRevisionEntity = NoteRevisionEntity(
    id = id,
    noteId = noteId,
    reason = reason.storageValue(),
    kind = kind.storageValue(),
    title = title,
    documentJson = document?.encodeToJson(),
    markdownText = markdownText,
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

fun NoteKind.storageValue(): String = when (this) {
    NoteKind.Rich -> "rich"
    NoteKind.Markdown -> "markdown"
}

fun String.toNoteKind(): NoteKind = when (this) {
    "markdown" -> NoteKind.Markdown
    else -> NoteKind.Rich
}

fun RevisionReason.storageValue(): String = when (this) {
    RevisionReason.ConvertToMarkdown -> "convert_to_markdown"
    RevisionReason.AgentPolish -> "agent_polish"
}

fun String.toRevisionReason(): RevisionReason = when (this) {
    "agent_polish" -> RevisionReason.AgentPolish
    else -> RevisionReason.ConvertToMarkdown
}

fun AttachmentKind.storageValue(): String = when (this) {
    AttachmentKind.UserBackground -> "user_background"
    AttachmentKind.Image -> "image"
    AttachmentKind.Sticker -> "sticker"
    AttachmentKind.Drawing -> "drawing"
}

fun String.toAttachmentKind(): AttachmentKind = when (this) {
    "user_background" -> AttachmentKind.UserBackground
    "sticker" -> AttachmentKind.Sticker
    "drawing" -> AttachmentKind.Drawing
    else -> AttachmentKind.Image
}

fun NoteRevision.referencedAttachmentIds(): Set<String> = document?.attachmentIds().orEmpty()
