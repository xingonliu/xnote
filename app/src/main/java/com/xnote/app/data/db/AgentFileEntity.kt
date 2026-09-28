package com.xnote.app.data.db

import androidx.room3.*
import kotlinx.coroutines.flow.Flow

// -- Type Definitions

@Entity(tableName = "agent_files", foreignKeys = [ForeignKey(entity = AttachmentEntity::class,
    parentColumns = ["id"], childColumns = ["attachmentId"], onDelete = ForeignKey.CASCADE)])
data class AgentFileEntity(
    @PrimaryKey val attachmentId: String,
    val text: String,
    val pages: Int,
    val textComplete: Boolean,
    val origin: String,
)

data class AgentFileCard(val id: String, val name: String, val mimeType: String, val byteSize: Long, val pages: Int,
    val text: String, val origin: String, val ownerType: String, val ownerId: String)

@Dao
interface AgentFileDao {
    @Upsert suspend fun save(row: AgentFileEntity)
    @Query("SELECT * FROM agent_files WHERE attachmentId = :id") suspend fun get(id: String): AgentFileEntity?
    @Query("SELECT * FROM agent_files") suspend fun all(): List<AgentFileEntity>
    @Query("SELECT attachmentId FROM agent_attachment_refs WHERE ownerType = :type AND ownerId = :id")
    suspend fun ids(type: String, id: String): List<String>
    @Query("DELETE FROM agent_attachment_refs WHERE ownerType = :type AND ownerId = :owner AND attachmentId = :id")
    suspend fun removeRef(type: String, owner: String, id: String)
    @Query("DELETE FROM agent_attachment_refs WHERE ownerType = :type AND ownerId = :owner")
    suspend fun clearRefs(type: String, owner: String)
    @Query("""SELECT attachments.id AS id, attachments.originalFileName AS name, attachments.mimeType AS mimeType,
        attachments.byteSize AS byteSize, agent_files.pages AS pages, agent_files.text AS text, agent_files.origin AS origin,
        agent_attachment_refs.ownerType AS ownerType, agent_attachment_refs.ownerId AS ownerId
        FROM agent_files INNER JOIN attachments ON attachments.id = agent_files.attachmentId
        INNER JOIN agent_attachment_refs ON agent_attachment_refs.attachmentId = attachments.id""")
    fun observeCards(): Flow<List<AgentFileCard>>
}
