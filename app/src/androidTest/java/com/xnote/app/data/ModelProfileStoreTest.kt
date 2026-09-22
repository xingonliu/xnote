package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

// -- Tests

class ModelProfileStoreTest {
    @Test fun credentialsAreEncryptedExcludedFromProfileAndDeletedWithProfile() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = XNoteDatabase.createInMemory(context)
        val credentials = AndroidModelCredentialStore(context)
        val store = ModelProfileStore(db, credentials)
        try {
            val secret = "test-secret-${UUID.randomUUID()}"
            val saved = store.save(ModelProfile("one", name = "模型一", protocol = ModelProtocol.OpenAI, modelId = "model", isDefault = true), secret)
            val row = db.agent().profiles().single()
            assertFalse(row.profileJson.contains(secret))
            val file = File(context.noBackupFilesDir, "model-credentials/${row.credentialReference}")
            assertFalse(file.readText().contains(secret))
            assertEquals(secret, store.credential(saved))
            assertEquals(secret, AndroidModelCredentialStore(context).read(row.credentialReference))
            store.delete(saved.id)
            assertFalse(file.exists())
            assertTrue(store.list().isEmpty())
        } finally { db.close() }
    }

    @Test fun activeRunAndPendingQueuePreventConfigurationReplacement() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = XNoteDatabase.createInMemory(context)
        val store = ModelProfileStore(db, AndroidModelCredentialStore(context))
        try {
            val first = store.save(ModelProfile("one", name = "一", protocol = ModelProtocol.OpenAI, modelId = "model", isDefault = true), "test-one")
            val second = store.save(ModelProfile("two", name = "二", protocol = ModelProtocol.Gemini, modelId = "model"), "test-two")
            db.agent().saveRun(AgentRunEntity("run", "segment", "message", first.id, first.version, AgentRunStatus.Running, 1, 1))
            suspend fun blocked(action: suspend () -> Unit) {
                try { action(); fail("must block") } catch (error: ModelException) { assertEquals(ModelError.Busy, error.error) }
            }
            blocked { store.save(first.copy(modelId = "other"), null) }
            blocked { store.delete(first.id) }
            blocked { store.save(second.copy(isDefault = true), null) }
            assertEquals(first, store.active())
            db.agent().saveRun(db.agent().unfinishedRuns().single().copy(status = AgentRunStatus.Complete))
            db.agent().saveQueueItem(AgentQueueEntity("queue", "next", first.id, first.version, 0, AgentQueueStatus.Paused, 1))
            blocked { store.delete(first.id) }
            db.agent().saveQueueItem(db.agent().pendingQueue().single().copy(status = AgentQueueStatus.Dispatched))
            val updated = store.save(second.copy(isDefault = true), null)
            assertEquals(second.version + 1, updated.version)
            assertEquals(second.id, store.active().id)
            store.delete(first.id); store.delete(second.id)
        } finally { db.close() }
    }
}
