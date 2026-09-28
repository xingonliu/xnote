package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentProfileMemoryTest {
    @Test fun disablingAndReenablingNeverBackfillsButExplicitRequestsStillWork() = runBlocking {
        fixture { db, store ->
            val old = message(db, "old", "我偏好简短回复")
            assertEquals(AgentFactDecision.Add, store.coordinate(old, setOf("old")))
            store.setAutomatic(false)
            val during = message(db, "during", "我偏好详细回复", "user.response.detail", "详细")
            assertEquals(AgentFactDecision.Ignore, store.coordinate(during, setOf("during")))
            assertEquals("简短", store.active().single().value)
            val explicit = message(db, "explicit", "请记住我喜欢中文", "user.response.language", "中文")
            assertEquals(AgentFactDecision.Add, store.coordinate(explicit, setOf("explicit"), explicit = true))
            store.setAutomatic(true)
            assertEquals(AgentFactDecision.Ignore, store.coordinate(during, setOf("during")))
            val fresh = message(db, "fresh", "以后我偏好详细回复", "user.response.detail", "详细")
            assertEquals(AgentFactDecision.Supersede, store.coordinate(fresh, setOf("fresh")))
            assertEquals("详细", store.active().single { it.key == fresh.key }.value)
            assertNotNull(db.profileMemory().facts().single { it.sourceMessageId == "fresh" }.supersedesId)
        }
    }

    @Test fun forgottenKeysRequireConfirmationAndClearBlocksPreviouslyUnseenCandidates() = runBlocking {
        fixture { db, store ->
            val original = message(db, "old", "我偏好简短回复")
            store.coordinate(original, setOf("old"))
            store.forget(original.key)
            assertFalse(store.canUseMessage("old"))
            assertEquals(AgentFactDecision.Ignore, store.coordinate(original, setOf("old")))
            val explicit = message(db, "new", "请记住我偏好详细回复", value = "详细")
            assertEquals(AgentFactDecision.Review, store.coordinate(explicit, setOf("new"), true))
            assertTrue(store.active().isEmpty())
            val pending = store.facts.first().single()
            store.confirm(pending.id, "详细")
            assertEquals("详细", store.active().single().value)
            val latent = message(db, "latent", "我喜欢中文", "user.response.language", "中文")
            store.clear()
            assertEquals(AgentFactDecision.Ignore, store.coordinate(latent, setOf("latent")))
            assertTrue(store.active().isEmpty())
        }
    }

    @Test fun sourceDeletionAndCorrectionInvalidateDerivedReplies() = runBlocking {
        fixture { db, store ->
            val candidate = message(db, "source", "我偏好简短回复")
            store.coordinate(candidate, setOf("source"))
            val first = store.active().single()
            db.profileMemory().saveReference(AgentMessageProfileRefEntity("reply", first.id))
            assertTrue(store.canUseMessage("reply"))
            val corrected = message(db, "correction", "更正，我偏好详细回复", value = "详细").copy(evidence = AgentFactEvidence.Corrected)
            assertEquals(AgentFactDecision.Supersede, store.coordinate(corrected, setOf("correction")))
            assertFalse(store.canUseMessage("reply"))
            db.agent().deleteMessage("correction")
            assertTrue(store.active().isEmpty())
            assertFalse(store.canUseMessage("source"))
        }
    }

    @Test fun sensitiveFactsWaitAndLowerPriorityCannotOverwriteCorrection() = runBlocking {
        fixture { db, store ->
            val sensitive = message(db, "location", "我住上海", "user.location.current", "上海")
            assertEquals(AgentFactDecision.Review, store.coordinate(sensitive, setOf("location")))
            assertTrue(store.active().isEmpty())
            store.confirm(store.facts.first().single().id, "上海")
            val lower = message(db, "location2", "我住北京", "user.location.current", "北京")
            assertEquals(AgentFactDecision.Ignore, store.coordinate(lower, setOf("location2")))
            assertEquals("上海", store.active().single().value)
        }
    }

    @Test fun rejectsAssistantFactsForeignSourcesSecretsAndFalsifiedQuotes() = runBlocking {
        fixture { db, store ->
            val candidate = message(db, "source", "我偏好简短回复")
            assertEquals(AgentFactDecision.Ignore, store.coordinate(candidate, emptySet()))
            assertEquals(AgentFactDecision.Ignore, store.coordinate(candidate.copy(quote = "凭空编造"), setOf("source")))
            assertEquals(AgentFactDecision.Ignore, store.coordinate(candidate.copy(value = "password=secret"), setOf("source")))
            assertEquals(AgentFactDecision.Ignore, store.coordinate(candidate.copy(key = "agent.permissions.all"), setOf("source")))
            assertEquals(AgentFactDecision.Ignore, store.coordinate(candidate, setOf("source"), true))
            val source = db.agent().message("source")!!
            db.agent().deleteMessage(source.id)
            db.agent().insertMessage(source.copy(role = AgentMessageRole.Assistant))
            assertEquals(AgentFactDecision.Ignore, store.coordinate(candidate, setOf("source")))
        }
    }

    // -- Functions

    private suspend fun message(db: XNoteDatabase, id: String, text: String, key: String = "user.response.detail", value: String = "简短"): AgentFactCandidate {
        db.agent().insertMessage(AgentMessageEntity(id = id, segmentId = "segment", runId = "run", role = AgentMessageRole.User, text = text, status = AgentMessageStatus.Complete, createdAtEpochMs = 1))
        return AgentFactCandidate(key, value, id, text)
    }

    private suspend fun fixture(block: suspend (XNoteDatabase, AgentProfileMemoryStore) -> Unit) {
        val db = XNoteDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        try {
            db.agent().saveSegment(AgentSegmentEntity("segment", 1))
            db.agent().saveRun(AgentRunEntity("run", "segment", "source", "profile", 1, AgentRunStatus.Complete, 1, 1))
            block(db, AgentProfileMemoryStore(db))
        } finally { db.close() }
    }
}
