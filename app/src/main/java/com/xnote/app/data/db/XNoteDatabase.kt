package com.xnote.app.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.AutoMigration
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

// -- Type Definitions

@Database(
    entities = [
        NotebookEntity::class,
        NoteEntity::class,
        NoteFtsEntity::class,
        NoteRevisionEntity::class,
        AttachmentEntity::class,
        StickerEntity::class,
        AgentPermissionEntity::class,
        AgentSegmentEntity::class,
        AgentMessageEntity::class,
        AgentRunEntity::class,
        AgentQueueEntity::class,
        AgentToolEventEntity::class,
        AgentSnapshotEntity::class,
        AgentSnapshotRefEntity::class,
        AgentToolSnapshotRefEntity::class,
        AgentChangeEntity::class,
        AgentReviewEntity::class,
        AgentAttachmentRefEntity::class,
        ModelProfileEntity::class,
        AgentDraftEntity::class,
        AgentEpisodeJobEntity::class,
        AgentEpisodeEntity::class,
        AgentEpisodeFtsEntity::class,
        AgentDerivedUsageEntity::class,
        AgentMemorySettingsEntity::class,
        AgentProfileFactEntity::class,
        AgentProfileForgottenEntity::class,
        AgentMessageProfileRefEntity::class,
        AgentMemorySourceRefEntity::class,
        AgentMessageFtsEntity::class,
        AgentNoteMemoryEntity::class,
        AgentNoteMemoryFtsEntity::class,
        AgentFileEntity::class,
    ],
    version = 17,
    autoMigrations = [AutoMigration(from = 16, to = 17)],
    exportSchema = true,
)
abstract class XNoteDatabase : RoomDatabase() {
    abstract fun notebooks(): NotebookDao
    abstract fun notes(): NoteDao
    abstract fun noteFts(): NoteFtsDao
    abstract fun revisions(): NoteRevisionDao
    abstract fun attachments(): AttachmentDao
    abstract fun stickers(): StickerDao
    abstract fun agent(): AgentDao
    abstract fun memory(): AgentMemoryDao
    abstract fun profileMemory(): AgentProfileDao
    abstract fun noteMemory(): AgentNoteMemoryDao
    abstract fun agentFiles(): AgentFileDao

    companion object {
        const val FileName = "xnote.db"

        fun create(context: Context, name: String = FileName): XNoteDatabase {
            return newBuilder(context.applicationContext, name).build()
        }

        fun createInMemory(context: Context): XNoteDatabase {
            return Room.inMemoryDatabaseBuilder(context.applicationContext, XNoteDatabase::class.java)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }

        private fun newBuilder(context: Context, name: String): Builder<XNoteDatabase> {
            return Room.databaseBuilder(context, XNoteDatabase::class.java, name)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
        }
    }
}
