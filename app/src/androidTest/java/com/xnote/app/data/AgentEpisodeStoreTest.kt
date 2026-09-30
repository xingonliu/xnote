package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentEpisodeStoreTest {
    @Test fun recalledProfileProvenanceSurvivesIntoNextReplyAndForgetRevokesIt() = runBlocking {
        fixture { db, profiles ->
            seed(db)
            val memories = AgentProfileMemoryStore(db)
            memories.coordinate(AgentFactCandidate("user.preference.travel", "高铁", "user", "出行选择高铁"), setOf("user"))
            val fact = memories.active().single()
            val store = AgentEpisodeStore(db)
            store.close(db.agent().segment("segment")!!, "idle")
            store.process(profiles, client { emit(ModelEvent.Text(summary())); emit(ModelEvent.Finished(ModelFinish.Complete)) })
            val run = db.agent().run("run")!!.copy(id = "next", segmentId = "next-segment", userMessageId = "next-user", status = AgentRunStatus.Running)
            db.agent().saveSegment(AgentSegmentEntity(run.segmentId, 10))
            db.agent().saveRun(run)
            db.agent().insertMessage(AgentMessageEntity(id = run.userMessageId, segmentId = run.segmentId, runId = run.id,
                role = AgentMessageRole.User, text = "高铁", status = AgentMessageStatus.Complete, createdAtEpochMs = 10))
            assertEquals(listOf(fact.id), store.recall(run, "高铁").single().profileFactIds)
            val prepared = AgentConversationContext(db, AgentNoteStore(db)).prepare(run, profiles.active())
            assertTrue(fact.id in prepared.profileFactIds)
            db.agent().insertMessage(AgentMessageEntity(id = "derived", segmentId = run.segmentId, runId = run.id,
                role = AgentMessageRole.Assistant, text = "你偏好高铁", status = AgentMessageStatus.Complete, createdAtEpochMs = 11))
            prepared.profileFactIds.forEach { db.profileMemory().saveReference(AgentMessageProfileRefEntity("derived", it)) }
            memories.forget(fact.key)
            assertTrue(store.recall(run, "高铁").isEmpty())
            assertNull(AgentNoteStore(db).projectMessage(run.id, db.agent().message("derived")!!))
        }
    }

    @Test fun authenticationFailureIsNotRetriedAndOldPromptDoesNotSilentlyChange() = runBlocking {
        fixture { db, profiles ->
            seed(db)
            val store = AgentEpisodeStore(db)
            store.close(db.agent().segment("segment")!!, "idle")
            var calls = 0
            val denied = client { calls++; throw ModelException(ModelError.Authentication) }
            assertFalse(store.process(profiles, denied))
            assertFalse(store.process(profiles, denied))
            assertEquals(1, calls)
            assertEquals("blocked", db.memory().job("segment")!!.status)
            db.memory().saveJob(db.memory().job("segment")!!.copy(status = "pending", promptVersion = 0, retryAfter = 0))
            store.process(profiles, denied)
            assertEquals(1, calls)
            assertEquals("prompt_version_changed", db.memory().job("segment")!!.error)
        }
    }

    @Test fun automaticCandidateChecksSwitchAgainAtCommit() = runBlocking {
        fixture { db, profiles ->
            seed(db)
            val store = AgentEpisodeStore(db)
            store.close(db.agent().segment("segment")!!, "idle")
            store.process(profiles, client {
                AgentProfileMemoryStore(db).setAutomatic(false)
                val result = Json.decodeFromString<AgentEpisodeSummary>(summary()).copy(userFactCandidates = listOf(
                    AgentFactCandidate("user.preference.travel", "高铁", "user", "出行选择高铁")))
                emit(ModelEvent.Text(Json.encodeToString(result))); emit(ModelEvent.Finished(ModelFinish.Complete))
            })
            assertEquals(1, db.memory().episodes().size)
            assertTrue(AgentProfileMemoryStore(db).active().isEmpty())
        }
    }

    @Test fun summaryIsToollessFixedIdempotentSearchableAndInvalidatedBySourceDeletion() = runBlocking {
        fixture { db, profiles ->
            seed(db)
            val store = AgentEpisodeStore(db)
            store.close(db.agent().segment("segment")!!, "new_conversation")
            var calls = 0
            val model = client { request ->
                calls++
                assertTrue(request.tools.isEmpty())
                assertFalse(request.forceTool)
                emit(ModelEvent.Text(summary()))
                emit(ModelEvent.Usage(100, 40))
                emit(ModelEvent.Finished(ModelFinish.Complete))
            }
            assertFalse(store.process(profiles, model))
            assertFalse(store.process(profiles, model))
            assertEquals(1, calls)
            assertEquals("complete", db.memory().job("segment")!!.status)
            assertEquals(100L, db.memory().usage(0).single().inputTokens)
            val reader = db.agent().run("run")!!.copy(id = "reader", segmentId = "current")
            assertTrue(store.recall(reader, "高铁").single().text.contains("选择高铁"))
            assertEquals(listOf("segment"), db.memory().search("\"高 铁\"", listOf("segment")))
            db.agent().deleteMessage("user")
            assertTrue(store.recall(reader, "高铁").isEmpty())
        }
    }

    @Test fun invalidSchemaDoesNotPublishPartialSummaryAndRetryIsBounded() = runBlocking {
        fixture { db, profiles ->
            seed(db)
            var now = System.currentTimeMillis()
            val store = AgentEpisodeStore(db) { now }
            store.close(db.agent().segment("segment")!!, "capacity")
            var calls = 0
            val model = client { calls++; emit(ModelEvent.Text("{}")); emit(ModelEvent.Finished(ModelFinish.Complete)) }
            repeat(5) { store.process(profiles, model); now += AgentMemoryLimits.RetryDelayMs + 1 }
            assertEquals(3, calls)
            assertTrue(db.memory().episodes().isEmpty())
            assertEquals("failed", db.memory().job("segment")!!.status)
        }
    }

    @Test fun sourceMutationDuringGenerationRejectsResultAndProfileChangeNeverSwitchesModel() = runBlocking {
        fixture { db, profiles ->
            seed(db)
            val store = AgentEpisodeStore(db)
            store.close(db.agent().segment("segment")!!, "idle")
            store.process(profiles, client {
                db.agent().deleteMessage("user")
                emit(ModelEvent.Text(summary())); emit(ModelEvent.Finished(ModelFinish.Complete))
            })
            assertTrue(db.memory().episodes().isEmpty())
            profiles.save(profiles.active().copy(modelId = "different"), null)
            db.memory().saveJob(db.memory().job("segment")!!.copy(retryAfter = 0))
            store.process(profiles, client { fail("must not switch model") })
            assertEquals("model_configuration_changed", db.memory().job("segment")!!.error)
        }
    }

    @Test fun expiredAttachmentPermissionCannotEnterSummary() = runBlocking {
        fixture { db, profiles ->
            seed(db)
            val user = db.agent().message("user")!!
            db.agent().updateMessageContext(user.id, Json.encodeToString(listOf(AgentMessageSource("unavailable", "snapshot"))), null)
            val answer = db.agent().message("answer")!!
            db.agent().updateMessageContext(answer.id, Json.encodeToString(listOf(AgentMessageSource("unavailable", "snapshot"))), null)
            val store = AgentEpisodeStore(db)
            store.close(db.agent().segment("segment")!!, "idle")
            store.process(profiles, client { fail("revoked content must not be dispatched") })
            assertEquals("invalid", db.memory().job("segment")!!.status)
            assertTrue(db.memory().usage(0).isEmpty())
        }
    }

    @Test fun sweepRecoversClosedSegmentsAndForegroundDefersWork() = runBlocking {
        fixture { db, profiles ->
            seed(db)
            db.agent().saveSegment(db.agent().segment("segment")!!.copy(closedAtEpochMs = 10, closeReason = "idle"))
            val store = AgentEpisodeStore(db)
            store.sweep(); store.sweep()
            assertNotNull(db.memory().job("segment"))
            db.agent().saveRun(db.agent().run("run")!!.copy(id = "foreground", status = AgentRunStatus.Running))
            assertTrue(store.process(profiles, client { fail("foreground first") }))
            assertEquals(0, db.memory().job("segment")!!.attempts)
        }
    }

    // -- Functions

    private fun summary() = Json.encodeToString(AgentEpisodeSummary("旅行", "选择高铁", listOf("旅行"), listOf("高铁"), emptyList(), emptyList(), listOf("高铁"), "user", "answer"))

    private suspend fun seed(db: XNoteDatabase) {
        db.agent().saveSegment(AgentSegmentEntity("segment", 1))
        db.agent().saveRun(AgentRunEntity("run", "segment", "user", "profile", 1, AgentRunStatus.Complete, 1, 3))
        db.agent().insertMessage(AgentMessageEntity(id = "user", segmentId = "segment", runId = "run", role = AgentMessageRole.User, text = "出行选择高铁", status = AgentMessageStatus.Complete, createdAtEpochMs = 1))
        db.agent().insertMessage(AgentMessageEntity(id = "answer", segmentId = "segment", runId = "run", role = AgentMessageRole.Assistant, text = "已选择高铁", status = AgentMessageStatus.Complete, createdAtEpochMs = 2))
    }

    private fun client(events: suspend FlowCollector<ModelEvent>.(ModelRequest) -> Unit) = object : ModelClient {
        override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest): Flow<ModelEvent> = flow { events(request) }
    }

    private suspend fun fixture(block: suspend (XNoteDatabase, ModelProfileStore) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = XNoteDatabase.createInMemory(context)
        val profiles = ModelProfileStore(db, AndroidModelCredentialStore(context))
        try {
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionMode.FullAccess))
            profiles.save(ModelProfile("profile", name = "测试", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "test-key")
            block(db, profiles)
        } finally {
            db.agent().unfinishedRuns().forEach { db.agent().saveRun(it.copy(status = AgentRunStatus.Cancelled)) }
            profiles.delete("profile")
            db.close()
        }
    }
}
