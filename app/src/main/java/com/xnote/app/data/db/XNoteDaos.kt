package com.xnote.app.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

// -- Type Definitions

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY sortIndex ASC, createdAtEpochMs ASC")
    fun observeAll(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun get(id: String): NotebookEntity?

    @Query("SELECT id FROM notebooks")
    suspend fun getAllIds(): List<String>

    @Upsert
    suspend fun upsert(entity: NotebookEntity)

    @Upsert
    suspend fun upsertAll(entities: List<NotebookEntity>)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAtEpochMs IS NULL")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAtEpochMs IS NULL AND notebookId IS NULL")
    fun observeUnfiled(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAtEpochMs IS NULL AND notebookId = :notebookId")
    fun observeInNotebook(notebookId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAtEpochMs IS NOT NULL")
    fun observeTrashed(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<NoteEntity>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE notebookId = :notebookId")
    suspend fun getByNotebook(notebookId: String): List<NoteEntity>

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAtEpochMs IS NOT NULL AND deletedAtEpochMs <= :expireBeforeInclusive
        """,
    )
    suspend fun expiredTrash(expireBeforeInclusive: Long): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deletedAtEpochMs IS NOT NULL")
    suspend fun getTrashed(): List<NoteEntity>

    @Upsert
    suspend fun upsert(entity: NoteEntity)

    @Upsert
    suspend fun upsertAll(entities: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query(
        """
        SELECT notebookId AS notebookId,
               COUNT(*) AS noteCount,
               COALESCE(SUM(visibleCharacterCount), 0) AS characterCount
        FROM notes
        WHERE deletedAtEpochMs IS NULL AND notebookId IS NOT NULL
        GROUP BY notebookId
        """,
    )
    suspend fun statsByNotebook(): List<NotebookStatsRow>

    @Query(
        """
        SELECT COUNT(*) AS noteCount,
               COALESCE(SUM(visibleCharacterCount), 0) AS characterCount
        FROM notes
        WHERE deletedAtEpochMs IS NULL AND notebookId IS NULL
        """,
    )
    suspend fun unfiledStats(): CountRow
}

@Dao
interface NoteFtsDao {
    @Insert
    suspend fun insert(entity: NoteFtsEntity)

    @Query("DELETE FROM notes_fts WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("DELETE FROM notes_fts WHERE noteId IN (:noteIds)")
    suspend fun deleteByNoteIds(noteIds: List<String>)

    @Query("SELECT noteId FROM notes_fts WHERE notes_fts MATCH :query")
    suspend fun searchNoteIds(query: String): List<String>
}

@Dao
interface NoteRevisionDao {
    @Upsert
    suspend fun upsert(entity: NoteRevisionEntity)

    @Query("SELECT * FROM note_revisions WHERE noteId = :noteId ORDER BY createdAtEpochMs DESC")
    suspend fun getByNote(noteId: String): List<NoteRevisionEntity>

    @Query("SELECT * FROM note_revisions")
    suspend fun getAll(): List<NoteRevisionEntity>

    @Query("DELETE FROM note_revisions WHERE noteId IN (:noteIds)")
    suspend fun deleteByNoteIds(noteIds: List<String>)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: String): AttachmentEntity?

    @Query("SELECT * FROM attachments")
    suspend fun getAll(): List<AttachmentEntity>

    @Upsert
    suspend fun upsert(entity: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
