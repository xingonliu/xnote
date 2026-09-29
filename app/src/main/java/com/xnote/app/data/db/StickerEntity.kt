package com.xnote.app.data.db

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

// -- Type Definitions

@Entity(tableName = "stickers")
data class StickerEntity(
    @PrimaryKey val id: String,
    val attachmentId: String,
    val name: String,
    val createdAtEpochMs: Long,
)

@Dao
interface StickerDao {
    @Query("SELECT * FROM stickers ORDER BY createdAtEpochMs DESC, id ASC")
    fun observeAll(): Flow<List<StickerEntity>>

    @Query("SELECT attachmentId FROM stickers")
    suspend fun attachmentIds(): List<String>

    @Upsert
    suspend fun upsert(sticker: StickerEntity)

    @Query("UPDATE stickers SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM stickers WHERE id = :id")
    suspend fun delete(id: String)
}
