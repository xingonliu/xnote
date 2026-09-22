package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentTimelineTest {
    @Test fun streamingAndFinalFactsArePersistedBeforeDisplayCompletes() = runBlocking {
        withFixture { db, profiles, scope ->
            val release = CompletableDeferred<Unit>()
            val captured = mutableListOf<ModelRequest>()
            val client = client { request -> captured += request; emit(ModelEvent.Text("部分回复")); release.await(); emit(ModelEvent.Text("，完成")); emit(ModelEvent.Usage(3, 5)); emit(ModelEvent.Finished(ModelFinish.Complete)) }
            val timeline = AgentTimeline(db, profiles, client, scope)
            timeline.send("用户输入")
            withTimeout(5000) { timeline.messages.first { it.any { message -> message.text == "部分回复" } } }
            assertEquals(AgentMessageStatus.Streaming, db.agent().messages().last().status)
            assertTrue(captured.single().tools.isEmpty())
            release.complete(Unit)
            withTimeout(5000) { timeline.runs.first { it.lastOrNull()?.status == AgentRunStatus.Complete } }
            assertEquals("部分回复，完成", db.agent().messages().last().text)
            assertEquals(5L, db.agent().run(db.agent().messages().last().runId!!)!!.outputTokens)
        }
    }

    @Test fun brokenStreamAndOutputLimitNeverBecomeComplete() = runBlocking {
        withFixture { db, profiles, scope ->
            val timeline = AgentTimeline(db, profiles, client { emit(ModelEvent.Text("已经生成")); throw ModelException(ModelError.Interrupted) }, scope)
            timeline.send("问题")
            withTimeout(5000) { timeline.runs.first { it.lastOrNull()?.status == AgentRunStatus.Failed } }
            assertEquals("已经生成", db.agent().messages().last().text)
            assertEquals(AgentMessageStatus.Failed, db.agent().messages().last().status)
            val limited = AgentTimeline(db, profiles, client { emit(ModelEvent.Text("未完成")); emit(ModelEvent.Finished(ModelFinish.OutputLimit)) }, scope)
            limited.send("限制输出")
            withTimeout(5000) { limited.runs.first { it.lastOrNull()?.status == AgentRunStatus.PausedBudget } }
            assertEquals(AgentMessageStatus.Interrupted, db.agent().messages().last().status)
            withTimeout(5000) { limited.state.first { !it.running } }
            limited.finishUnresolved()
            assertTrue(db.agent().unfinishedRuns().isEmpty())
        }
    }

    @Test fun oversizeInputRetainsDraftAndDoesNotSendOrCreateRun() = runBlocking {
        withFixture { db, profiles, scope ->
            var sent = false
            val timeline = AgentTimeline(db, profiles, client { sent = true }, scope)
            val draft = "大".repeat(20000)
            try { timeline.send(draft); fail("must reject") } catch (_: AgentBudgetException) { }
            assertFalse(sent)
            assertEquals(draft, db.agent().draft()?.text)
            assertTrue(db.agent().messages().isEmpty())
            assertTrue(db.agent().unfinishedRuns().isEmpty())
        }
    }

    @Test fun serviceContextLimitPausesWithInputIntact() = runBlocking {
        withFixture { db, profiles, scope ->
            val timeline = AgentTimeline(db, profiles, client { throw ModelException(ModelError.ContextLimit) }, scope)
            timeline.send("实际服务容量更小")
            withTimeout(5000) { timeline.runs.first { it.lastOrNull()?.status == AgentRunStatus.PausedBudget } }
            assertEquals("实际服务容量更小", db.agent().messages().first().text)
            assertEquals(AgentMessageStatus.Interrupted, db.agent().messages().last().status)
        }
    }

    @Test fun coldStartRecoversSameTimelineAndMarksInterrupted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "timeline-reopen-${System.nanoTime()}.db"
        var db = XNoteDatabase.create(context, name)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            db.agent().saveSegment(AgentSegmentEntity("segment", 1))
            db.agent().saveRun(AgentRunEntity("run", "segment", "user", "profile", 1, AgentRunStatus.Running, 1, 1))
            db.agent().insertMessage(AgentMessageEntity(id = "user", segmentId = "segment", runId = "run", role = AgentMessageRole.User, text = "保留问题", status = AgentMessageStatus.Complete, createdAtEpochMs = 1))
            db.agent().insertMessage(AgentMessageEntity(id = "answer", segmentId = "segment", runId = "run", role = AgentMessageRole.Assistant, text = "断电前内容", status = AgentMessageStatus.Streaming, createdAtEpochMs = 1))
            db.agent().saveDraft(AgentDraftEntity(text = "保留草稿"))
            db.close()
            db = XNoteDatabase.create(context, name)
            val timeline = AgentTimeline(db, ModelProfileStore(db, AndroidModelCredentialStore(context)), client { fail("must not auto resume") }, scope)
            timeline.awaitReady()
            assertEquals(listOf("保留问题", "断电前内容"), db.agent().messages().map { it.text })
            assertEquals(AgentRunStatus.Interrupted, db.agent().run("run")?.status)
            assertEquals(AgentMessageStatus.Interrupted, db.agent().messages().last().status)
            assertEquals("保留草稿", timeline.draft.value)
        } finally { scope.cancel(); db.close(); context.deleteDatabase(name) }
    }

    @Test fun newTopicPreservesHistoryAndResetsContext() = runBlocking {
        withFixture { db, profiles, scope ->
            val requests = mutableListOf<ModelRequest>()
            val timeline = AgentTimeline(db, profiles, client { request -> requests += request; emit(ModelEvent.Text("回答")); emit(ModelEvent.Finished(ModelFinish.Complete)) }, scope)
            timeline.send("旧话题")
            withTimeout(5000) { timeline.runs.first { it.lastOrNull()?.status == AgentRunStatus.Complete }; timeline.state.first { !it.running } }
            timeline.newTopic()
            timeline.send("新话题")
            withTimeout(5000) { timeline.runs.first { it.size == 2 && it.all { run -> run.status == AgentRunStatus.Complete } } }
            assertTrue(db.agent().messages().any { it.text == "旧话题" })
            assertEquals(listOf("新话题"), requests.last().messages.map { it.text })
        }
    }

    @Test fun stopKeepsPartialTextAndDoesNotSendTools() = runBlocking {
        withFixture { db, profiles, scope ->
            val timeline = AgentTimeline(db, profiles, client { emit(ModelEvent.Text("停止前")); awaitCancellation() }, scope)
            timeline.send("请求")
            withTimeout(5000) { timeline.messages.first { it.any { message -> message.text == "停止前" } } }
            timeline.stop()
            withTimeout(5000) { timeline.runs.first { it.lastOrNull()?.status == AgentRunStatus.Cancelled } }
            assertEquals("停止前", db.agent().messages().last().text)
        }
    }

    // -- Functions

    private fun client(events: suspend FlowCollector<ModelEvent>.(ModelRequest) -> Unit) = object : ModelClient {
        override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest): Flow<ModelEvent> = flow { events(request) }
    }

    private suspend fun withFixture(block: suspend (XNoteDatabase, ModelProfileStore, CoroutineScope) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = XNoteDatabase.createInMemory(context)
        val profiles = ModelProfileStore(db, AndroidModelCredentialStore(context))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            profiles.save(ModelProfile("profile", name = "模型", protocol = ModelProtocol.OpenAI, modelId = "test", isDefault = true), "local-test-key")
            block(db, profiles, scope)
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            db.agent().unfinishedRuns().forEach { db.agent().saveRun(it.copy(status = AgentRunStatus.Cancelled)) }
            profiles.delete("profile")
            db.close()
        }
    }
}
