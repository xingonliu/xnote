package com.xnote.app.data.db

import androidx.room3.*
import kotlinx.coroutines.flow.Flow

// -- Type Definitions

@Entity(tableName = "agent_memory_settings")
data class AgentMemorySettingsEntity(@PrimaryKey val id: Int = 1, val automatic: Boolean = true, val enabledAfterSequence: Long = 0, val clearedThroughSequence: Long = 0)

@Entity(tableName = "agent_profile_facts", indices = [Index(value = ["key", "status"])])
data class AgentProfileFactEntity(
    @PrimaryKey val id: String,
    val key: String,
    val value: String,
    val evidence: String,
    val priority: Int,
    val sourceMessageId: String?,
    val sourceQuote: String,
    val sourceSequence: Long,
    val status: String,
    val supersedesId: String?,
    val createdAtEpochMs: Long,
    val validToEpochMs: Long? = null,
)

@Entity(tableName = "agent_profile_forgotten")
data class AgentProfileForgottenEntity(@PrimaryKey val key: String, val createdAtEpochMs: Long)

@Entity(tableName = "agent_message_profile_refs", primaryKeys = ["messageId", "factId"])
data class AgentMessageProfileRefEntity(val messageId: String, val factId: String)

@Dao
interface AgentProfileDao {
    @Query("SELECT * FROM agent_memory_settings WHERE id = 1")
    suspend fun settings(): AgentMemorySettingsEntity?

    @Query("SELECT * FROM agent_memory_settings WHERE id = 1")
    fun observeSettings(): Flow<AgentMemorySettingsEntity?>

    @Upsert suspend fun saveSettings(value: AgentMemorySettingsEntity)
    @Upsert suspend fun saveFact(value: AgentProfileFactEntity)
    @Upsert suspend fun saveForgotten(value: AgentProfileForgottenEntity)
    @Upsert suspend fun saveReference(value: AgentMessageProfileRefEntity)

    @Query("SELECT * FROM agent_message_profile_refs WHERE messageId = :messageId")
    suspend fun references(messageId: String): List<AgentMessageProfileRefEntity>

    @Query("SELECT * FROM agent_profile_facts ORDER BY createdAtEpochMs, id")
    suspend fun facts(): List<AgentProfileFactEntity>

    @Query("SELECT * FROM agent_profile_facts ORDER BY createdAtEpochMs, id")
    fun observeFacts(): Flow<List<AgentProfileFactEntity>>

    @Query("SELECT * FROM agent_profile_forgotten WHERE `key` = :key")
    suspend fun forgotten(key: String): AgentProfileForgottenEntity?

    @Query("DELETE FROM agent_profile_forgotten WHERE `key` = :key")
    suspend fun allowKey(key: String)
}
