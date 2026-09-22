package com.xnote.app.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

// -- Type Definitions

@Dao
interface AgentDao {
    @Query("SELECT * FROM agent_draft WHERE id = 1")
    suspend fun draft(): AgentDraftEntity?

    @Upsert
    suspend fun saveDraft(value: AgentDraftEntity)

    @Query("SELECT * FROM agent_runs ORDER BY createdAtEpochMs, id")
    fun observeRuns(): Flow<List<AgentRunEntity>>

    @Query("SELECT * FROM agent_runs WHERE id = :id")
    suspend fun run(id: String): AgentRunEntity?

    @Query("SELECT * FROM model_profiles ORDER BY id")
    fun observeProfiles(): Flow<List<ModelProfileEntity>>

    @Query("SELECT * FROM model_profiles ORDER BY id")
    suspend fun profiles(): List<ModelProfileEntity>

    @Upsert
    suspend fun saveProfile(value: ModelProfileEntity)

    @Query("DELETE FROM model_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("SELECT * FROM agent_permissions WHERE id = 1")
    suspend fun permission(): AgentPermissionEntity?

    @Upsert
    suspend fun savePermission(value: AgentPermissionEntity)

    @Query("SELECT * FROM agent_messages ORDER BY sequence")
    fun observeMessages(): Flow<List<AgentMessageEntity>>

    @Query("SELECT * FROM agent_messages ORDER BY sequence")
    suspend fun messages(): List<AgentMessageEntity>

    @Insert
    suspend fun insertMessage(value: AgentMessageEntity): Long

    @Query("UPDATE agent_messages SET text = :text, status = :status WHERE sequence = :sequence")
    suspend fun updateMessage(sequence: Long, text: String, status: com.xnote.app.domain.agent.AgentMessageStatus)

    @Upsert
    suspend fun saveSegment(value: AgentSegmentEntity)

    @Query("SELECT * FROM agent_segments WHERE closedAtEpochMs IS NULL ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun openSegment(): AgentSegmentEntity?

    @Upsert
    suspend fun saveRun(value: AgentRunEntity)

    @Query("SELECT * FROM agent_runs WHERE status NOT IN ('Complete', 'Failed', 'Cancelled')")
    suspend fun unfinishedRuns(): List<AgentRunEntity>

    @Upsert
    suspend fun saveQueueItem(value: AgentQueueEntity)

    @Query("SELECT * FROM agent_queue WHERE status != 'Dispatched' ORDER BY position")
    suspend fun pendingQueue(): List<AgentQueueEntity>

    @Upsert
    suspend fun saveToolEvent(value: AgentToolEventEntity)

    @Query("SELECT * FROM agent_tool_events WHERE runId = :runId AND callId = :callId")
    suspend fun toolEvent(runId: String, callId: String): AgentToolEventEntity?

    @Insert
    suspend fun insertSnapshot(value: AgentSnapshotEntity)

    @Insert
    suspend fun insertSnapshotRef(value: AgentSnapshotRefEntity)

    @Insert
    suspend fun insertChange(value: AgentChangeEntity)

    @Upsert
    suspend fun saveReview(value: AgentReviewEntity)

    @Insert
    suspend fun insertAttachmentRef(value: AgentAttachmentRefEntity)

    @Query("SELECT DISTINCT attachmentId FROM agent_attachment_refs")
    suspend fun referencedAttachmentIds(): List<String>
}
