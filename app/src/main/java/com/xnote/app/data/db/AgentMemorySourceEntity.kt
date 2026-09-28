package com.xnote.app.data.db

import androidx.room3.*

// -- Type Definitions

@Entity(tableName = "agent_memory_source_refs", primaryKeys = ["ownerId", "sourceMessageId"])
data class AgentMemorySourceRefEntity(val ownerId: String, val sourceMessageId: String)

@Entity(tableName = "agent_message_fts")
@Fts5(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["messageId", "segmentId"])
data class AgentMessageFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val messageId: String,
    val segmentId: String,
    val text: String,
)
