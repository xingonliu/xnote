package com.xnote.app.data.db

import androidx.room3.*
import kotlinx.coroutines.flow.Flow

// -- Type Definitions

@Entity(tableName = "agent_note_memory", foreignKeys = [ForeignKey(entity = NoteEntity::class,
    parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)])
data class AgentNoteMemoryEntity(
    @PrimaryKey val noteId: String,
    val generationId: String,
    val contentHash: String,
    val sourceVersion: String,
    val title: String,
    val plainText: String,
    val summaryJson: String,
    val profileId: String,
    val profileVersion: Long,
    val modelId: String,
    val modelProvider: String,
    val promptVersion: Int,
    val updatedAtEpochMs: Long,
    val status: String,
    val attempts: Int = 0,
    val leaseUntil: Long = 0,
    val retryAfter: Long = 0,
    val error: String? = null,
)

@Entity(tableName = "agent_note_memory_fts")
@Fts5(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["noteId"])
data class AgentNoteMemoryFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val noteId: String,
    val text: String,
)

@Dao
interface AgentNoteMemoryDao {
    @Upsert suspend fun save(value: AgentNoteMemoryEntity)
    @Insert suspend fun insertFts(value: AgentNoteMemoryFtsEntity)
    @Query("SELECT * FROM agent_note_memory WHERE noteId = :id") suspend fun get(id: String): AgentNoteMemoryEntity?
    @Query("SELECT * FROM agent_note_memory") suspend fun all(): List<AgentNoteMemoryEntity>
    @Query("SELECT * FROM agent_note_memory") fun observe(): Flow<List<AgentNoteMemoryEntity>>
    @Query("DELETE FROM agent_note_memory WHERE noteId = :id") suspend fun delete(id: String)
    @Query("DELETE FROM agent_note_memory_fts WHERE noteId = :id") suspend fun deleteFts(id: String)
    @Query("DELETE FROM agent_note_memory") suspend fun clear()
    @Query("DELETE FROM agent_note_memory_fts") suspend fun clearFts()
    @Query("SELECT noteId FROM agent_note_memory_fts WHERE agent_note_memory_fts MATCH :query AND noteId IN (:allowed)")
    suspend fun search(query: String, allowed: List<String>): List<String>
}
