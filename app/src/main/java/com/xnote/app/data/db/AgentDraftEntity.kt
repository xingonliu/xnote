package com.xnote.app.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

// -- Type Definitions

@Entity(tableName = "agent_draft")
data class AgentDraftEntity(
    @PrimaryKey val id: Int = 1,
    val text: String,
    @ColumnInfo(defaultValue = "'[]'") val noteIdsJson: String = "[]",
)
