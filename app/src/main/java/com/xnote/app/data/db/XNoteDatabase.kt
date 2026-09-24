package com.xnote.app.data.db

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
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
        AgentPermissionEntity::class,
        AgentSegmentEntity::class,
        AgentMessageEntity::class,
        AgentRunEntity::class,
        AgentQueueEntity::class,
        AgentToolEventEntity::class,
        AgentSnapshotEntity::class,
        AgentSnapshotRefEntity::class,
        AgentChangeEntity::class,
        AgentReviewEntity::class,
        AgentAttachmentRefEntity::class,
        ModelProfileEntity::class,
        AgentDraftEntity::class,
    ],
    version = 8,
    autoMigrations = [AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8)],
    exportSchema = true,
)
abstract class XNoteDatabase : RoomDatabase() {
    abstract fun notebooks(): NotebookDao
    abstract fun notes(): NoteDao
    abstract fun noteFts(): NoteFtsDao
    abstract fun revisions(): NoteRevisionDao
    abstract fun attachments(): AttachmentDao
    abstract fun agent(): AgentDao

    companion object {
        const val FileName = "xnote.db"

        fun create(context: Context, name: String = FileName): XNoteDatabase {
            return newBuilder(context.applicationContext, name).build()
        }

        fun createInMemory(context: Context): XNoteDatabase {
            return Room.inMemoryDatabaseBuilder(context.applicationContext, XNoteDatabase::class.java)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }

        private fun newBuilder(context: Context, name: String): Builder<XNoteDatabase> {
            return Room.databaseBuilder(context, XNoteDatabase::class.java, name)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
        }
    }
}
