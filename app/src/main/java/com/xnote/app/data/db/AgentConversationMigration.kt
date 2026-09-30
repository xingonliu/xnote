package com.xnote.app.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// -- Constants

val AgentConversationMigration = object : Migration(17, 18) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE agent_segments ADD COLUMN conversationId TEXT NOT NULL DEFAULT ''")
        // Keep automatic memory segments together until the user explicitly starts a conversation.
        connection.execSQL("""
            UPDATE agent_segments AS current SET conversationId = (
                SELECT first.id FROM agent_segments AS first
                WHERE first.rowid <= current.rowid AND NOT EXISTS (
                    SELECT 1 FROM agent_segments AS boundary
                    WHERE boundary.rowid >= first.rowid AND boundary.rowid < current.rowid
                    AND boundary.closeReason IN ('new_topic', 'clear_chat')
                ) ORDER BY first.rowid LIMIT 1
            )
        """.trimIndent())
        connection.execSQL("ALTER TABLE agent_messages ADD COLUMN isFinal INTEGER NOT NULL DEFAULT 0")
        // Existing successful tasks already have a final prose reply; preserve their visible endings.
        connection.execSQL("""
            UPDATE agent_messages SET isFinal = 1 WHERE sequence IN (
                SELECT MAX(message.sequence) FROM agent_messages AS message
                JOIN agent_runs AS run ON run.id = message.runId
                WHERE run.status = 'Complete' AND message.role = 'Assistant' AND message.status = 'Complete'
                AND message.text != '' AND message.modelJson IS NULL GROUP BY message.runId
            )
        """.trimIndent())
    }
}
