package com.xnote.app.data.db

import androidx.room3.*

// -- Type Definitions

@Entity(tableName = "agent_episode_jobs")
data class AgentEpisodeJobEntity(
    @PrimaryKey val segmentId: String,
    val messageIdsJson: String,
    val sourceHash: String,
    val profileId: String,
    val profileVersion: Long,
    val promptVersion: Int,
    val status: String = "pending",
    val attempts: Int = 0,
    val leaseUntil: Long = 0,
    val retryAfter: Long = 0,
    val error: String? = null,
)

@Entity(tableName = "agent_episode_summaries")
data class AgentEpisodeEntity(
    @PrimaryKey val segmentId: String,
    val summaryJson: String,
    val sourcesJson: String,
    val sourceHash: String,
    val profileId: String,
    val profileVersion: Long,
    val modelProvider: String,
    val modelId: String,
    val promptVersion: Int,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "agent_episode_fts")
@Fts5(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["segmentId"])
data class AgentEpisodeFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val segmentId: String,
    val text: String,
)

@Entity(tableName = "agent_derived_usage")
data class AgentDerivedUsageEntity(
    @PrimaryKey val id: String,
    val taskType: String,
    val createdAtEpochMs: Long,
    val reservedTokens: Long,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
)

@Dao
interface AgentMemoryDao {
    @Query("SELECT * FROM agent_segments WHERE closedAtEpochMs IS NOT NULL AND closeReason != 'clear_chat' ORDER BY createdAtEpochMs")
    suspend fun closedSegments(): List<AgentSegmentEntity>

    @Query("SELECT * FROM agent_episode_jobs WHERE segmentId = :id")
    suspend fun job(id: String): AgentEpisodeJobEntity?

    @Upsert suspend fun saveJob(value: AgentEpisodeJobEntity)
    @Upsert suspend fun saveEpisode(value: AgentEpisodeEntity)
    @Insert suspend fun insertFts(value: AgentEpisodeFtsEntity)
    @Upsert suspend fun saveUsage(value: AgentDerivedUsageEntity)

    @Query("SELECT * FROM agent_derived_usage WHERE createdAtEpochMs >= :since")
    suspend fun usage(since: Long): List<AgentDerivedUsageEntity>

    @Query("SELECT * FROM agent_episode_summaries ORDER BY createdAtEpochMs DESC")
    suspend fun episodes(): List<AgentEpisodeEntity>

    @Query("SELECT segmentId FROM agent_episode_fts WHERE agent_episode_fts MATCH :query AND segmentId IN (:allowed)")
    suspend fun search(query: String, allowed: List<String>): List<String>

    @Query("DELETE FROM agent_episode_fts WHERE segmentId = :id")
    suspend fun deleteFts(id: String)

    @Query("DELETE FROM agent_episode_summaries WHERE segmentId = :id")
    suspend fun deleteEpisode(id: String)
}
