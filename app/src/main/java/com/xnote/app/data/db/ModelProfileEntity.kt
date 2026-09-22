package com.xnote.app.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

// -- Type Definitions

@Entity(tableName = "model_profiles")
data class ModelProfileEntity(@PrimaryKey val id: String, val profileJson: String, val credentialReference: String)
