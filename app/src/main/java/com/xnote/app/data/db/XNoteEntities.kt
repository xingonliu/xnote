package com.xnote.app.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Fts5
import androidx.room3.FtsOptions
import androidx.room3.Index
import androidx.room3.PrimaryKey

// -- Type Definitions

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortIndex: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["notebookId"]),
        Index(value = ["deletedAtEpochMs"]),
        Index(value = ["updatedAtEpochMs"]),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val notebookId: String?,
    val title: String,
    val kind: String,
    val documentJson: String?,
    val markdownText: String?,
    val backgroundKey: String?,
    val sortIndex: Long,
    val visibleCharacterCount: Int,
    val latinWordCount: Int,
    val summary: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val originalNotebookName: String?,
)

@Entity(tableName = "notes_fts")
@Fts5(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    notIndexed = ["noteId"],
)
data class NoteFtsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val noteId: String,
    val title: String,
    val body: String,
)

@Entity(
    tableName = "note_revisions",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index(value = ["noteId"])],
)
data class NoteRevisionEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val reason: String,
    val kind: String,
    val title: String,
    val documentJson: String?,
    val markdownText: String?,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val mimeType: String,
    val originalFileName: String?,
    val relativePath: String,
    val byteSize: Long,
    val widthPx: Int?,
    val heightPx: Int?,
    val createdAtEpochMs: Long,
)

data class NotebookStatsRow(
    val notebookId: String,
    val noteCount: Int,
    val characterCount: Int,
)

data class CountRow(
    val noteCount: Int,
    val characterCount: Int,
)
