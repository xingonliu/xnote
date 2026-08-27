package com.xnote.app.data.repository

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.xnote.app.data.db.NoteFtsEntity
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.db.referencedAttachmentIds
import com.xnote.app.data.db.toDomain
import com.xnote.app.data.db.toEntity
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.domain.document.emptyNoteDocument
import com.xnote.app.domain.document.referencedAttachmentIds
import com.xnote.app.domain.markdown.richNoteMarkdown
import com.xnote.app.domain.model.Attachment
import com.xnote.app.domain.model.AttachmentKind
import com.xnote.app.domain.model.EpochClock
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteKind
import com.xnote.app.domain.model.NoteListSort
import com.xnote.app.domain.model.NoteRevision
import com.xnote.app.domain.model.Notebook
import com.xnote.app.domain.model.NotebookStats
import com.xnote.app.domain.model.RevisionReason
import com.xnote.app.domain.model.newNoteId
import com.xnote.app.domain.rules.RecycleBinPolicy
import com.xnote.app.domain.rules.conversionBlockers
import com.xnote.app.domain.rules.notebookIdAfterRestore
import com.xnote.app.domain.rules.patchesForDeletedNotebook
import com.xnote.app.domain.text.FtsIndexText
import com.xnote.app.domain.text.extractPlainText
import com.xnote.app.domain.text.summarizePlainText
import com.xnote.app.domain.text.visibleTextStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// -- Type Definitions

class NoteLibrary(
    private val database: XNoteDatabase,
    private val files: AttachmentFileStore,
    private val clock: EpochClock,
) {
    private val notebooks = database.notebooks()
    private val notes = database.notes()
    private val noteFts = database.noteFts()
    private val revisions = database.revisions()
    private val attachments = database.attachments()

    fun observeNotebooks(): Flow<List<Notebook>> {
        return notebooks.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getNotebook(id: String): Notebook? = notebooks.get(id)?.toDomain()

    suspend fun createNotebook(name: String): Notebook {
        val now = clock.nowMs()
        val notebook = Notebook(
            id = newNoteId(),
            name = name.trim(),
            sortIndex = now,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        notebooks.upsert(notebook.toEntity())
        return notebook
    }

    suspend fun renameNotebook(id: String, name: String): Notebook {
        val existing = notebooks.get(id)?.toDomain()
            ?: error("Notebook not found: $id")
        val updated = existing.copy(
            name = name.trim(),
            updatedAtEpochMs = clock.nowMs(),
        )
        notebooks.upsert(updated.toEntity())
        return updated
    }

    suspend fun reorderNotebooks(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        write {
            val updated = orderedIds.mapIndexedNotNull { index, id ->
                notebooks.get(id)?.toDomain()?.copy(sortIndex = index.toLong())?.toEntity()
            }
            if (updated.isNotEmpty()) {
                notebooks.upsertAll(updated)
            }
        }
    }

    suspend fun reorderNotes(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        write {
            val current = notes.getAll(orderedIds).associateBy { it.id }
            val updated = orderedIds.mapIndexedNotNull { index, id ->
                current[id]?.toDomain()?.copy(sortIndex = index.toLong())?.toEntity()
            }
            if (updated.isNotEmpty()) {
                notes.upsertAll(updated)
            }
        }
    }

    suspend fun deleteNotebook(id: String) {
        val notebook = notebooks.get(id)?.toDomain() ?: return
        write {
            val assigned = notes.getByNotebook(id).map { it.toDomain() }
            val patches = patchesForDeletedNotebook(notebook, assigned, clock.nowMs())
                .associateBy { it.noteId }
            if (patches.isNotEmpty()) {
                val patched = assigned.map { note ->
                    val patch = patches.getValue(note.id)
                    note.copy(
                        notebookId = null,
                        originalNotebookName = patch.originalNotebookName,
                        deletedAtEpochMs = patch.deletedAtEpochMs,
                    ).withDerivedText()
                }
                notes.upsertAll(patched.map { it.toEntity() })
                noteFts.deleteByNoteIds(patched.map { it.id })
            }
            notebooks.deleteById(id)
        }
    }

    fun observeActiveNotes(): Flow<List<Note>> {
        return notes.observeActive().map { entities -> entities.map { it.toDomain() } }
    }

    fun observeAllActiveNotes(sort: NoteListSort = NoteListSort.UpdatedAt): Flow<List<Note>> {
        return notes.observeActive().map { entities ->
            entities.map { it.toDomain() }.sortedWith(noteComparator(sort))
        }
    }

    fun observeUnfiledNotes(sort: NoteListSort = NoteListSort.UpdatedAt): Flow<List<Note>> {
        return notes.observeUnfiled().map { entities ->
            entities.map { it.toDomain() }.sortedWith(noteComparator(sort))
        }
    }

    fun observeNotesInNotebook(
        notebookId: String,
        sort: NoteListSort = NoteListSort.Manual,
    ): Flow<List<Note>> {
        return notes.observeInNotebook(notebookId).map { entities ->
            entities.map { it.toDomain() }.sortedWith(noteComparator(sort))
        }
    }

    fun observeTrashedNotes(): Flow<List<Note>> {
        return notes.observeTrashed().map { entities ->
            entities.map { it.toDomain() }.sortedByDescending { it.deletedAtEpochMs ?: 0L }
        }
    }

    suspend fun getNote(id: String): Note? = notes.get(id)?.toDomain()

    suspend fun createRichNote(notebookId: String?): Note {
        if (notebookId != null) {
            notebooks.get(notebookId) ?: error("Notebook not found: $notebookId")
        }
        val now = clock.nowMs()
        val note = Note(
            id = newNoteId(),
            notebookId = notebookId,
            title = "",
            kind = NoteKind.Rich,
            document = emptyNoteDocument(),
            markdownText = null,
            backgroundKey = null,
            sortIndex = now,
            visibleCharacterCount = 0,
            latinWordCount = 0,
            summary = "",
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            deletedAtEpochMs = null,
            originalNotebookName = null,
        ).withDerivedText()
        write {
            notes.upsert(note.toEntity())
            indexForSearch(note)
        }
        return note
    }

    suspend fun saveNote(note: Note): Note {
        return write {
            val existing = notes.get(note.id)?.toDomain()
                ?: error("Note not found: ${note.id}")
            require(note.kind == existing.kind) {
                "Note kind can only change through a dedicated conversion"
            }
            require(note.hasValidContent()) {
                "Rich notes require a document and Markdown notes require Markdown text"
            }
            val saved = note.copy(
                createdAtEpochMs = existing.createdAtEpochMs,
                deletedAtEpochMs = existing.deletedAtEpochMs,
                originalNotebookName = existing.originalNotebookName,
                updatedAtEpochMs = clock.nowMs(),
            ).withDerivedText()
            notes.upsert(saved.toEntity())
            if (saved.isTrashed) {
                noteFts.deleteByNoteId(saved.id)
            } else {
                indexForSearch(saved)
            }
            saved
        }
    }

    suspend fun convertToMarkdown(noteId: String): Note {
        return write {
            val existing = notes.get(noteId)?.toDomain()
                ?: error("Note not found: $noteId")
            check(!existing.isTrashed) { "Trashed notes cannot be converted" }
            val blockers = conversionBlockers(existing)
            check(blockers.isEmpty()) {
                "Note cannot be converted to Markdown: ${blockers.joinToString()}"
            }
            val now = clock.nowMs()
            val revision = NoteRevision(
                id = newNoteId(),
                noteId = existing.id,
                reason = RevisionReason.ConvertToMarkdown,
                kind = existing.kind,
                title = existing.title,
                document = existing.document,
                markdownText = existing.markdownText,
                createdAtEpochMs = now,
            )
            val converted = existing.copy(
                kind = NoteKind.Markdown,
                document = null,
                markdownText = richNoteMarkdown(existing.title, checkNotNull(existing.document)),
                updatedAtEpochMs = now,
            ).withDerivedText()
            revisions.upsert(revision.toEntity())
            notes.upsert(converted.toEntity())
            indexForSearch(converted)
            converted
        }
    }

    suspend fun moveNotes(ids: Collection<String>, notebookId: String?) {
        if (ids.isEmpty()) return
        if (notebookId != null) {
            notebooks.get(notebookId) ?: error("Notebook not found: $notebookId")
        }
        val now = clock.nowMs()
        write {
            val current = notes.getAll(ids.toList())
            val updated = current.map { entity ->
                entity.toDomain().copy(
                    notebookId = notebookId,
                    sortIndex = now,
                    updatedAtEpochMs = now,
                ).withDerivedText()
            }
            notes.upsertAll(updated.map { it.toEntity() })
            updated.filterNot { it.isTrashed }.forEach { indexForSearch(it) }
        }
    }

    suspend fun trashNotes(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val now = clock.nowMs()
        write {
            val current = notes.getAll(ids.toList()).map { it.toDomain() }
            val updated = current.map { note ->
                if (note.isTrashed) {
                    note
                } else {
                    note.copy(deletedAtEpochMs = now).withDerivedText()
                }
            }
            notes.upsertAll(updated.map { it.toEntity() })
            noteFts.deleteByNoteIds(updated.map { it.id })
        }
    }

    suspend fun restoreNotes(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val notebookIds = notebooks.getAllIds().toSet()
        val now = clock.nowMs()
        write {
            val current = notes.getAll(ids.toList()).map { it.toDomain() }
            val updated = current.map { note ->
                val restoredNotebookId = notebookIdAfterRestore(note) { it in notebookIds }
                note.copy(
                    notebookId = restoredNotebookId,
                    deletedAtEpochMs = null,
                    originalNotebookName = null,
                    updatedAtEpochMs = now,
                ).withDerivedText()
            }
            notes.upsertAll(updated.map { it.toEntity() })
            updated.forEach { indexForSearch(it) }
        }
    }

    suspend fun permanentlyDeleteNotes(
        ids: Collection<String>,
        extraReferencedAttachmentIds: Set<String> = emptySet(),
    ) {
        if (ids.isEmpty()) return
        write {
            val idList = ids.toList()
            revisions.deleteByNoteIds(idList)
            noteFts.deleteByNoteIds(idList)
            notes.deleteByIds(idList)
            deleteOrphanAttachments(extraReferencedAttachmentIds)
        }
    }

    suspend fun emptyTrash(extraReferencedAttachmentIds: Set<String> = emptySet()) {
        val trashedIds = notes.getTrashed().map { it.id }
        permanentlyDeleteNotes(trashedIds, extraReferencedAttachmentIds)
    }

    suspend fun purgeExpiredTrash(extraReferencedAttachmentIds: Set<String> = emptySet()) {
        val expireBefore = clock.nowMs() - java.util.concurrent.TimeUnit.DAYS.toMillis(
            RecycleBinPolicy.RetentionDays.toLong(),
        )
        val expiredIds = notes.expiredTrash(expireBefore).map { it.id }
        permanentlyDeleteNotes(expiredIds, extraReferencedAttachmentIds)
    }

    suspend fun saveRevision(noteId: String, reason: RevisionReason): NoteRevision {
        val note = notes.get(noteId)?.toDomain()
            ?: error("Note not found: $noteId")
        val revision = NoteRevision(
            id = newNoteId(),
            noteId = note.id,
            reason = reason,
            kind = note.kind,
            title = note.title,
            document = note.document,
            markdownText = note.markdownText,
            createdAtEpochMs = clock.nowMs(),
        )
        revisions.upsert(revision.toEntity())
        return revision
    }

    suspend fun getNoteRevisions(noteId: String): List<NoteRevision> {
        return revisions.getByNote(noteId).map { it.toDomain() }
    }

    suspend fun searchNoteIds(query: String): List<String> {
        val matchQuery = FtsIndexText.matchQuery(query) ?: return emptyList()
        return noteFts.searchNoteIds(matchQuery)
    }

    suspend fun notebookStats(): Map<String, NotebookStats> {
        return notes.statsByNotebook().associate { row ->
            row.notebookId to NotebookStats(
                noteCount = row.noteCount,
                characterCount = row.characterCount,
            )
        }
    }

    suspend fun unfiledStats(): NotebookStats {
        val row = notes.unfiledStats()
        return NotebookStats(noteCount = row.noteCount, characterCount = row.characterCount)
    }

    suspend fun putAttachment(
        kind: AttachmentKind,
        mimeType: String,
        extension: String,
        bytes: ByteArray,
        originalFileName: String? = null,
        widthPx: Int? = null,
        heightPx: Int? = null,
    ): Attachment {
        val id = newNoteId()
        val relativePath = AttachmentFileStore.relativePath(id, extension)
        files.write(relativePath, bytes)
        val attachment = Attachment(
            id = id,
            kind = kind,
            mimeType = mimeType,
            originalFileName = originalFileName,
            relativePath = relativePath,
            byteSize = bytes.size.toLong(),
            widthPx = widthPx,
            heightPx = heightPx,
            createdAtEpochMs = clock.nowMs(),
        )
        attachments.upsert(attachment.toEntity())
        return attachment
    }

    suspend fun getAttachment(id: String): Attachment? = attachments.get(id)?.toDomain()

    fun attachmentFile(attachment: Attachment) = files.resolve(attachment.relativePath)

    private suspend fun indexForSearch(note: Note) {
        noteFts.deleteByNoteId(note.id)
        val plainText = extractPlainText(note)
        noteFts.insert(
            NoteFtsEntity(
                rowId = 0,
                noteId = note.id,
                title = FtsIndexText.prepare(note.title),
                body = FtsIndexText.prepare(plainText),
            ),
        )
    }

    private suspend fun deleteOrphanAttachments(extraKeepIds: Set<String>) {
        val remainingNotes = notes.getAll().map { it.toDomain() }
        val remainingRevisions = revisions.getAll().map { it.toDomain() }
        val referenced = linkedSetOf<String>()
        referenced += extraKeepIds
        remainingNotes.forEach { referenced += it.referencedAttachmentIds() }
        remainingRevisions.forEach { referenced += it.referencedAttachmentIds() }
        val orphans = attachments.getAll().filter { it.id !in referenced }
        if (orphans.isEmpty()) return
        attachments.deleteByIds(orphans.map { it.id })
        orphans.forEach { files.delete(it.relativePath) }
    }

    private suspend fun <R> write(block: suspend () -> R): R {
        return database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                block()
            }
        }
    }
}

// -- Functions

private fun Note.withDerivedText(): Note {
    val stats = visibleTextStats(this)
    return copy(
        visibleCharacterCount = stats.characterCount,
        latinWordCount = stats.latinWordCount,
        summary = summarizePlainText(extractPlainText(this)),
    )
}

private fun Note.hasValidContent(): Boolean = when (kind) {
    NoteKind.Rich -> document != null && markdownText == null
    NoteKind.Markdown -> document == null && markdownText != null
}

private fun noteComparator(sort: NoteListSort): Comparator<Note> = when (sort) {
    NoteListSort.UpdatedAt -> compareByDescending { it.updatedAtEpochMs }
    NoteListSort.CreatedAt -> compareByDescending { it.createdAtEpochMs }
    NoteListSort.Title -> compareBy { it.title.lowercase() }
    NoteListSort.Manual -> compareBy { it.sortIndex }
}
