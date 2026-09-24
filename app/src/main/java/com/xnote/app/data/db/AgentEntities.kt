package com.xnote.app.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.xnote.app.domain.agent.AgentChangeKind
import com.xnote.app.domain.agent.AgentChangeOrigin
import com.xnote.app.domain.agent.AgentMessageRole
import com.xnote.app.domain.agent.AgentMessageStatus
import com.xnote.app.domain.agent.AgentQueueStatus
import com.xnote.app.domain.agent.AgentReviewStatus
import com.xnote.app.domain.agent.AgentRunStatus
import com.xnote.app.domain.agent.AgentToolStatus

// -- Type Definitions

@Entity(tableName = "agent_permissions")
data class AgentPermissionEntity(@PrimaryKey val id: Int = 1, val permissionJson: String)

@Entity(tableName = "agent_segments")
data class AgentSegmentEntity(
    @PrimaryKey val id: String,
    val createdAtEpochMs: Long,
    val closedAtEpochMs: Long? = null,
    val closeReason: String? = null,
)

@Entity(tableName = "agent_messages", indices = [Index(value = ["id"], unique = true), Index(value = ["segmentId", "sequence"])])
data class AgentMessageEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val id: String,
    val segmentId: String,
    val runId: String?,
    val role: AgentMessageRole,
    val text: String,
    val status: AgentMessageStatus,
    val createdAtEpochMs: Long,
    val sourcesJson: String = "[]",
    @ColumnInfo(defaultValue = "NULL") val modelJson: String? = null,
)

@Entity(tableName = "agent_runs", indices = [Index(value = ["status"])])
data class AgentRunEntity(
    @PrimaryKey val id: String,
    val segmentId: String,
    val userMessageId: String,
    val profileId: String,
    val profileVersion: Long,
    val status: AgentRunStatus,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val grantJson: String? = null,
    val errorCode: String? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
)

@Entity(tableName = "agent_queue", indices = [Index(value = ["position"])])
data class AgentQueueEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val profileId: String,
    val profileVersion: Long,
    val position: Long,
    val status: AgentQueueStatus,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "agent_tool_events", indices = [Index(value = ["runId", "callId"], unique = true)])
data class AgentToolEventEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val callId: String,
    val name: String,
    val argumentsJson: String,
    val resultJson: String?,
    val status: AgentToolStatus,
    val permissionRevision: Long,
    val createdAtEpochMs: Long,
    val committedAtEpochMs: Long? = null,
    @ColumnInfo(defaultValue = "'[]'") val sourcesJson: String = "[]",
    @ColumnInfo(defaultValue = "'[]'") val decisionsJson: String = "[]",
)

@Entity(
    tableName = "agent_snapshots",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["noteId", "version"], unique = true)],
)
data class AgentSnapshotEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val version: String,
    val title: String,
    val documentJson: String,
    val backgroundKey: String?,
    val notebookId: String?,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "agent_snapshot_refs",
    primaryKeys = ["messageId", "snapshotId"],
    foreignKeys = [ForeignKey(entity = AgentSnapshotEntity::class, parentColumns = ["id"], childColumns = ["snapshotId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["snapshotId"])],
)
data class AgentSnapshotRefEntity(
    val messageId: String,
    val snapshotId: String,
    val segmentId: String,
    val permissionRevision: Long,
)

@Entity(
    tableName = "agent_tool_snapshot_refs",
    primaryKeys = ["eventId", "snapshotId"],
    foreignKeys = [
        ForeignKey(entity = AgentToolEventEntity::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AgentSnapshotEntity::class, parentColumns = ["id"], childColumns = ["snapshotId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["snapshotId"])],
)
data class AgentToolSnapshotRefEntity(val eventId: String, val snapshotId: String)

@Entity(tableName = "agent_changes", indices = [Index(value = ["noteId", "reviewId"])])
data class AgentChangeEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val runId: String?,
    val toolCallId: String?,
    val reviewId: String?,
    val origin: AgentChangeOrigin,
    val kind: AgentChangeKind,
    val beforeVersion: String?,
    val afterVersion: String,
    val beforeNoteJson: String?,
    val afterNoteJson: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "agent_reviews", indices = [Index(value = ["noteId", "status"])])
data class AgentReviewEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val status: AgentReviewStatus,
    val createdAtEpochMs: Long,
    val reviewedAtEpochMs: Long? = null,
)

@Entity(tableName = "agent_attachment_refs", primaryKeys = ["ownerType", "ownerId", "attachmentId"], indices = [Index(value = ["attachmentId"])])
data class AgentAttachmentRefEntity(val ownerType: String, val ownerId: String, val attachmentId: String)
