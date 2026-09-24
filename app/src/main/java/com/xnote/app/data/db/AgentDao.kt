package com.xnote.app.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

// -- Type Definitions

@Dao
interface AgentDao {
    @Query("SELECT * FROM agent_reviews ORDER BY rowid")
    fun observeReviews(): Flow<List<AgentReviewEntity>>

    @Query("SELECT * FROM agent_reviews WHERE noteId = :noteId ORDER BY rowid")
    suspend fun reviews(noteId: String): List<AgentReviewEntity>

    @Query("SELECT * FROM agent_changes WHERE reviewId = :reviewId ORDER BY rowid")
    suspend fun reviewChanges(reviewId: String): List<AgentChangeEntity>

    @Query("SELECT * FROM agent_changes WHERE noteId = :noteId ORDER BY rowid")
    suspend fun noteChanges(noteId: String): List<AgentChangeEntity>

    @Query("DELETE FROM agent_attachment_refs WHERE ownerType = 'change' AND ownerId IN (SELECT id FROM agent_changes WHERE reviewId IN (SELECT id FROM agent_reviews WHERE status IN ('Rejected', 'Undone', 'Unrecoverable') OR (status = 'Accepted' AND reviewedAtEpochMs <= :expireBefore)))")
    suspend fun pruneReviewedAttachments(expireBefore: Long)

    @Query("SELECT * FROM agent_changes WHERE runId = :runId AND toolCallId = :callId LIMIT 1")
    suspend fun committedChange(runId: String, callId: String): AgentChangeEntity?

    @Query("DELETE FROM agent_attachment_refs WHERE ownerType = 'change' AND ownerId IN (SELECT id FROM agent_changes WHERE noteId IN (:noteIds))")
    suspend fun deleteChangeAttachments(noteIds: List<String>)

    @Query("UPDATE agent_reviews SET status = 'Unrecoverable' WHERE noteId IN (:noteIds)")
    suspend fun markReviewsUnrecoverable(noteIds: List<String>)

    @Query("DELETE FROM agent_changes WHERE noteId IN (:noteIds)")
    suspend fun deleteNoteChanges(noteIds: List<String>)

    @Query("SELECT * FROM agent_permissions WHERE id = 1")
    fun observePermission(): Flow<AgentPermissionEntity?>

    @Query("SELECT * FROM agent_tool_events ORDER BY createdAtEpochMs, id")
    fun observeToolEvents(): Flow<List<AgentToolEventEntity>>

    @Query("SELECT * FROM agent_snapshots ORDER BY createdAtEpochMs, id")
    fun observeSnapshots(): Flow<List<AgentSnapshotEntity>>

    @Query("SELECT * FROM agent_snapshot_refs WHERE messageId = :messageId")
    suspend fun snapshotRefs(messageId: String): List<AgentSnapshotRefEntity>

    @Query("SELECT * FROM agent_snapshot_refs WHERE segmentId = :segmentId")
    suspend fun segmentSnapshotRefs(segmentId: String): List<AgentSnapshotRefEntity>

    @Query("SELECT * FROM agent_snapshots WHERE id = :id")
    suspend fun snapshot(id: String): AgentSnapshotEntity?

    @Query("SELECT * FROM agent_snapshots WHERE noteId = :noteId AND version = :version")
    suspend fun snapshotForVersion(noteId: String, version: String): AgentSnapshotEntity?

    @Query("SELECT * FROM agent_segments WHERE id = :id")
    suspend fun segment(id: String): AgentSegmentEntity?

    @Query("SELECT * FROM agent_messages WHERE id = :id")
    suspend fun message(id: String): AgentMessageEntity?

    @Query("UPDATE agent_messages SET sourcesJson = :sourcesJson, modelJson = :modelJson WHERE id = :id")
    suspend fun updateMessageContext(id: String, sourcesJson: String, modelJson: String?)

    @Query("UPDATE agent_snapshot_refs SET segmentId = :segmentId WHERE messageId = :messageId")
    suspend fun moveSnapshotRefs(messageId: String, segmentId: String)

    @Query("DELETE FROM agent_tool_events WHERE runId = :runId")
    suspend fun deleteToolEvents(runId: String)

    @Query("SELECT * FROM agent_queue WHERE status != 'Dispatched' ORDER BY position, createdAtEpochMs, id")
    fun observeQueue(): Flow<List<AgentQueueEntity>>

    @Query("DELETE FROM agent_queue WHERE id = :id")
    suspend fun deleteQueueItem(id: String)

    @Query("UPDATE agent_messages SET runId = :runId, segmentId = :segmentId, status = 'Complete' WHERE id = :id")
    suspend fun dispatchMessage(id: String, runId: String, segmentId: String)

    @Query("SELECT * FROM agent_tool_events WHERE runId = :runId ORDER BY createdAtEpochMs, id")
    suspend fun toolEvents(runId: String): List<AgentToolEventEntity>

    @Query("DELETE FROM agent_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM agent_snapshot_refs WHERE messageId = :messageId")
    suspend fun deleteSnapshotRefs(messageId: String)

    @Query("DELETE FROM agent_snapshots WHERE id NOT IN (SELECT snapshotId FROM agent_snapshot_refs UNION SELECT snapshotId FROM agent_tool_snapshot_refs)")
    suspend fun deleteUnusedSnapshots()

    @Insert
    suspend fun insertToolSnapshotRef(value: AgentToolSnapshotRefEntity)

    @Query("SELECT s.* FROM agent_snapshots s INNER JOIN agent_tool_snapshot_refs r ON r.snapshotId = s.id INNER JOIN agent_tool_events e ON e.id = r.eventId INNER JOIN agent_runs run ON run.id = e.runId WHERE run.segmentId = :segmentId AND e.status = 'Committed' AND s.noteId = :noteId AND s.version = :version LIMIT 1")
    suspend fun readSnapshot(segmentId: String, noteId: String, version: String): AgentSnapshotEntity?

    @Query("DELETE FROM agent_attachment_refs WHERE ownerType = 'snapshot' AND ownerId NOT IN (SELECT id FROM agent_snapshots)")
    suspend fun deleteUnusedSnapshotAttachments()

    @Query("DELETE FROM agent_attachment_refs WHERE ownerType = 'message' AND ownerId = :messageId")
    suspend fun deleteMessageAttachments(messageId: String)

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
