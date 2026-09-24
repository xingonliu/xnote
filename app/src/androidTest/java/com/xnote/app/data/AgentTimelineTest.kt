package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import com.xnote.app.domain.document.*
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

    @Test fun supplementsJoinOneRunAndQueueDispatchesExactlyOnceInEditedOrder() = runBlocking {
        withFixture { db, profiles, scope ->
            val release = CompletableDeferred<Unit>()
            val requests = java.util.concurrent.CopyOnWriteArrayList<ModelRequest>()
            val timeline = AgentTimeline(db, profiles, client { request ->
                requests += request
                emit(ModelEvent.Text("回复 ${requests.size}"))
                if (requests.size == 1) release.await()
                emit(ModelEvent.Finished(ModelFinish.Complete))
            }, scope)
            timeline.send("当前目标")
            withTimeout(5000) { timeline.messages.first { it.any { m -> m.text == "回复 1" } } }
            timeline.send("新的约束")
            timeline.enqueue("队列甲")
            timeline.enqueue("队列乙")
            val queued = db.agent().pendingQueue()
            timeline.editQueued(queued.last().id, "队列乙已编辑")
            timeline.moveQueued(queued.last().id, -1)
            release.complete(Unit)
            withTimeout(5000) { timeline.runs.first { it.size == 3 && it.all { row -> row.status == AgentRunStatus.Complete } } }
            assertEquals(4, requests.size)
            assertTrue(requests[1].messages.last().text.contains("新的约束"))
            assertEquals("队列乙已编辑", requests[2].messages.last().text)
            assertEquals("队列甲", requests[3].messages.last().text)
            assertTrue(db.agent().pendingQueue().isEmpty())
            assertEquals(1, db.agent().messages().count { it.text == "新的约束" })
            assertEquals(1, db.agent().messages().filter { it.role == AgentMessageRole.User && it.text in setOf("当前目标", "新的约束") }.map { it.runId }.distinct().size)
        }
    }

    @Test fun cancellationPausesQueueUntilExplicitResumeAndKeepsModelLocked() = runBlocking {
        withFixture { db, profiles, scope ->
            val started = CompletableDeferred<Unit>()
            var calls = 0
            val timeline = AgentTimeline(db, profiles, client {
                calls++
                if (calls == 1) { started.complete(Unit); awaitCancellation() }
                emit(ModelEvent.Text("完成队列")); emit(ModelEvent.Finished(ModelFinish.Complete))
            }, scope)
            timeline.send("停止这个")
            started.await()
            timeline.enqueue("保留队列")
            timeline.stop()
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            assertEquals(AgentQueueStatus.Paused, db.agent().pendingQueue().single().status)
            assertEquals(1, calls)
            try { profiles.delete("profile"); fail("queued model must stay locked") } catch (error: ModelException) { assertEquals(ModelError.Busy, error.error) }
            timeline.resumeQueue()
            withTimeout(5000) { timeline.runs.first { it.size == 2 && it.last().status == AgentRunStatus.Complete } }
            assertEquals(2, calls)
        }
    }

    @Test fun retryIsBoundedAndNeverRepeatsPartialOutputOrAuthenticationErrors() = runBlocking {
        withFixture { db, profiles, scope ->
            var calls = 0
            val timeline = AgentTimeline(db, profiles, client { calls++; throw ModelException(ModelError.Network) }, scope)
            timeline.send("有限重试")
            withTimeout(10000) { timeline.state.first { it.ready && !it.running } }
            assertEquals(3, calls)
            assertEquals(AgentRunStatus.Failed, db.agent().run(db.agent().messages().first().runId!!)!!.status)
            val auth = AgentTimeline(db, profiles, client { calls++; throw ModelException(ModelError.Authentication) }, scope)
            auth.send("认证失败")
            withTimeout(5000) { auth.state.first { it.ready && !it.running } }
            assertEquals(4, calls)
            val partial = AgentTimeline(db, profiles, client { calls++; emit(ModelEvent.Text("不可重复")); throw ModelException(ModelError.Network) }, scope)
            partial.send("部分输出")
            withTimeout(5000) { partial.state.first { it.ready && !it.running } }
            assertEquals(5, calls)
        }
    }

    @Test fun clearChatCancelsActiveRequestAndQueueWithoutDeadlock() = runBlocking {
        withFixture { db, profiles, scope ->
            val started = CompletableDeferred<Unit>()
            var calls = 0
            val timeline = AgentTimeline(db, profiles, client { calls++; started.complete(Unit); awaitCancellation() }, scope)
            timeline.send("运行")
            started.await()
            timeline.enqueue("不能启动")
            withTimeout(5000) { timeline.clearChat() }
            assertTrue(db.agent().messages().isEmpty())
            assertTrue(db.agent().pendingQueue().isEmpty())
            assertTrue(db.agent().unfinishedRuns().isEmpty())
            assertEquals(1, calls)
            assertFalse(timeline.state.value.running)
        }
    }

    @Test fun interruptedRunRequiresUserContinuationAndRetainsPendingSupplement() = runBlocking {
        withFixture { db, profiles, scope ->
            val started = CompletableDeferred<Unit>()
            val requests = java.util.concurrent.CopyOnWriteArrayList<ModelRequest>()
            val timeline = AgentTimeline(db, profiles, client { request ->
                requests += request
                if (requests.size == 1) { emit(ModelEvent.Text("中断前")); started.complete(Unit); awaitCancellation() }
                emit(ModelEvent.Text("已继续")); emit(ModelEvent.Finished(ModelFinish.Complete))
            }, scope)
            timeline.send("原始目标")
            started.await()
            timeline.send("尚未处理的补充")
            timeline.interrupt("test_background_limit")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            val run = db.agent().unfinishedRuns().single()
            assertEquals(AgentRunStatus.Interrupted, run.status)
            assertEquals(1, requests.size)
            timeline.continueRun(run.id)
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            assertEquals(2, requests.size)
            assertTrue(requests.last().messages.last().text.contains("尚未处理的补充"))
            assertTrue(db.agent().messages().any { it.text == "中断前" && it.status == AgentMessageStatus.Interrupted })
            assertEquals(AgentRunStatus.Complete, db.agent().run(run.id)?.status)
        }
    }

    @Test fun deletingOneMessagePreservesTimelineButExcludesTheExchangeFromContext() = runBlocking {
        withFixture { db, profiles, scope ->
            val requests = java.util.concurrent.CopyOnWriteArrayList<ModelRequest>()
            val timeline = AgentTimeline(db, profiles, client { request -> requests += request; emit(ModelEvent.Text("相关内容")); emit(ModelEvent.Finished(ModelFinish.Complete)) }, scope)
            timeline.send("需要删除的内容")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            timeline.deleteMessage(db.agent().messages().first { it.role == AgentMessageRole.User }.id)
            assertTrue(db.agent().messages().any { it.text == "相关内容" })
            assertFalse(db.agent().messages().any { it.text == "需要删除的内容" })
            timeline.send("新问题")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            assertEquals(listOf("新问题"), requests.last().messages.map { it.text })
        }
    }

    @Test fun requestLimitPersistsAcrossContinuationAndDoesNotSendAgain() = runBlocking {
        withFixture { db, profiles, scope ->
            db.agent().saveSegment(AgentSegmentEntity("limited-segment", 1))
            db.agent().saveRun(AgentRunEntity("limited-run", "limited-segment", "limited-user", "profile", 1, AgentRunStatus.Interrupted, 1, 1))
            db.agent().insertMessage(AgentMessageEntity(id = "limited-user", segmentId = "limited-segment", runId = "limited-run", role = AgentMessageRole.User,
                text = "完整目标", status = AgentMessageStatus.Complete, createdAtEpochMs = 1))
            repeat(AgentRunLimits.MaxRequests) { index ->
                db.agent().insertMessage(AgentMessageEntity(id = "attempt-$index", segmentId = "limited-segment", runId = "limited-run", role = AgentMessageRole.Event,
                    text = "模型请求", status = AgentMessageStatus.Complete, createdAtEpochMs = 1))
            }
            var called = false
            val timeline = AgentTimeline(db, profiles, client { called = true }, scope)
            timeline.continueRun("limited-run")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            assertFalse(called)
            assertEquals(AgentRunStatus.PausedBudget, db.agent().run("limited-run")?.status)
            assertEquals("完整目标", db.agent().messages().first().text)
        }
    }

    @Test fun deniedBackgroundStartCreatesRecoverableInterruptionWithoutRequest() = runBlocking {
        withFixture { db, profiles, scope ->
            var called = false
            val timeline = AgentTimeline(db, profiles, client { called = true }, scope, startBackground = { throw IllegalStateException("restricted") })
            timeline.send("保留待执行内容")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            assertFalse(called)
            assertEquals(AgentRunStatus.Interrupted, db.agent().unfinishedRuns().single().status)
            assertEquals("保留待执行内容", db.agent().messages().single().text)
        }
    }

    @Test fun synchronousServiceInterruptionCannotLeaveAnUnstartedRunMarkedRunning() = runBlocking {
        withFixture { db, profiles, scope ->
            var called = false
            lateinit var timeline: AgentTimeline
            timeline = AgentTimeline(db, profiles, client { called = true }, scope,
                startBackground = { timeline.interrupt("immediate_service_failure") })
            timeline.send("还未开始的任务")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            assertFalse(called)
            assertEquals(AgentRunStatus.Interrupted, db.agent().unfinishedRuns().single().status)
        }
    }

    @Test fun readToolLoopPersistsProtocolAndExcludesRevokedDerivedHistory() = runBlocking {
        withFixture { db, profiles, scope ->
            seedReadableNote(db)
            val profile = profiles.active()
            profiles.recordCapabilities(profile, ModelCapabilities(true, true, 1))
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Read, AgentScope.All))
            val requests = java.util.concurrent.CopyOnWriteArrayList<ModelRequest>()
            val timeline = AgentTimeline(db, profiles, client { request ->
                requests += request
                if (requests.size == 1) {
                    emit(ModelEvent.ToolCall(ModelToolCall("read-once", "read", buildJsonObject { put("note_id", "readable") })))
                    emit(ModelEvent.Finished(ModelFinish.ToolCalls))
                } else {
                    emit(ModelEvent.Text(if (requests.size == 2) "笔记包含受保护正文" else "新的回答"))
                    emit(ModelEvent.Finished(ModelFinish.Complete))
                }
            }, scope)
            timeline.send("读取笔记")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            assertEquals(2, requests.size)
            assertEquals(listOf("read", "note_search", "write"), requests.first().tools.map { it.name })
            assertEquals(listOf(AgentMessageRole.User, AgentMessageRole.Assistant, AgentMessageRole.Tool), requests[1].messages.map { it.role })
            assertTrue(requests[1].messages.last().results.single().content.contains("受保护正文"))
            val tool = db.agent().toolEvents(db.agent().messages().first().runId!!).single()
            assertEquals(AgentToolStatus.Committed, tool.status)
            assertTrue(db.agent().messages().last().sourcesJson.contains("readable"))
            timeline.savePermission(AgentPermission())
            timeline.send("无关的新问题")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            assertEquals(listOf("无关的新问题"), requests.last().messages.map { it.text })
        }
    }

    @Test fun permissionWaitSurvivesRuntimeRecreationAndNeverDispatchesQueueBeforeAnswer() = runBlocking {
        withFixture { db, profiles, scope ->
            seedReadableNote(db)
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(true, true, 1))
            val release = CompletableDeferred<Unit>()
            val requested = CompletableDeferred<Unit>()
            val requests = java.util.concurrent.CopyOnWriteArrayList<ModelRequest>()
            val model = client { request ->
                requests += request
                if (requests.size == 1) {
                    requested.complete(Unit); release.await()
                    emit(ModelEvent.ToolCall(ModelToolCall("authorize", "read", buildJsonObject { put("note_id", "readable") })))
                    emit(ModelEvent.Finished(ModelFinish.ToolCalls))
                } else { emit(ModelEvent.Text("已读取")); emit(ModelEvent.Finished(ModelFinish.Complete)) }
            }
            val timeline = AgentTimeline(db, profiles, model, scope)
            timeline.send("需要读取")
            requested.await()
            timeline.enqueue("下一项")
            release.complete(Unit)
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            val waiting = db.agent().unfinishedRuns().single()
            assertEquals(AgentRunStatus.WaitingPermission, waiting.status)
            assertEquals(1, requests.size)
            val reopened = AgentTimeline(db, profiles, model, scope)
            reopened.awaitReady()
            assertEquals(1, requests.size)
            reopened.answerPermission(waiting.id, "authorize", AgentPermission(AgentPermissionLevel.Read, AgentScope.Unfiled))
            withTimeout(5000) { reopened.state.first { it.ready && !it.running } }
            assertEquals(2, requests.size)
            assertTrue(requests.last().messages.last().results.single().content.contains("受保护正文"))
            assertEquals(1, db.agent().toolEvents(waiting.id).size)
            assertEquals(AgentPermission(), AgentPermissionStore(db).current())
            assertEquals(AgentQueueStatus.Paused, db.agent().pendingQueue().single().status)
            reopened.removeQueued(db.agent().pendingQueue().single().id)
        }
    }

    @Test fun deniedToolReturnsACompleteProtocolPairWithoutReadingNote() = runBlocking {
        withFixture { db, profiles, scope ->
            seedReadableNote(db)
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(true, true, 1))
            val requests = java.util.concurrent.CopyOnWriteArrayList<ModelRequest>()
            val timeline = AgentTimeline(db, profiles, client { request ->
                requests += request
                if (requests.size == 1) {
                    emit(ModelEvent.ToolCall(ModelToolCall("denied", "read", buildJsonObject { put("note_id", "readable") })))
                    emit(ModelEvent.Finished(ModelFinish.ToolCalls))
                } else { emit(ModelEvent.Text("未获授权")); emit(ModelEvent.Finished(ModelFinish.Complete)) }
            }, scope)
            timeline.send("需要授权")
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            timeline.answerPermission(db.agent().unfinishedRuns().single().id, "denied", null)
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            val result = requests.last().messages.last().results.single()
            assertTrue(result.content.contains("user_denied"))
            assertFalse(result.content.contains("受保护正文"))
        }
    }

    @Test fun attachedNoteUsesSendSnapshotEvenAfterOriginalChanges() = runBlocking {
        withFixture { db, profiles, scope ->
            seedReadableNote(db)
            val release = CompletableDeferred<Unit>()
            val started = CompletableDeferred<Unit>()
            val requests = java.util.concurrent.CopyOnWriteArrayList<ModelRequest>()
            val timeline = AgentTimeline(db, profiles, client { request ->
                requests += request
                emit(ModelEvent.Text("已收到快照"))
                if (requests.size == 1) { started.complete(Unit); release.await() }
                emit(ModelEvent.Finished(ModelFinish.Complete))
            }, scope)
            timeline.selectDraftNotes(listOf("readable"))
            timeline.send("总结发送时内容")
            started.await()
            val original = db.notes().get("readable")!!
            db.notes().upsert(original.copy(title = "最新标题", documentJson = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun("最新正文"))))).encodeToJson()))
            timeline.send("继续使用之前的版本")
            release.complete(Unit)
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            assertTrue(requests.last().messages.first().text.contains("受保护正文"))
            assertFalse(requests.last().messages.joinToString { it.text }.contains("最新正文"))
            assertTrue(timeline.draftNotes.value.isEmpty())
            assertEquals(1, db.agent().snapshotRefs(db.agent().messages().first().id).size)
        }
    }

    @Test fun writeConflictSurvivesRuntimeRecreationAndReplansWithQueuePaused() = runBlocking {
        withFixture { db, profiles, scope ->
            seedReadableNote(db)
            profiles.recordCapabilities(profiles.active(), ModelCapabilities(true, true, 1))
            AgentPermissionStore(db).saveFromUser(AgentPermission(AgentPermissionLevel.Edit, AgentScope.All))
            val requests = java.util.concurrent.CopyOnWriteArrayList<ModelRequest>()
            val reachedWrite = CompletableDeferred<Unit>()
            val releaseWrite = CompletableDeferred<Unit>()
            val model = client { request ->
                requests += request
                when (requests.size) {
                    1, 3 -> {
                        emit(ModelEvent.ToolCall(ModelToolCall("read-${requests.size}", "read", buildJsonObject { put("note_id", "readable") })))
                        emit(ModelEvent.Finished(ModelFinish.ToolCalls))
                    }
                    2, 4 -> {
                        val version = Json.parseToJsonElement(request.messages.last().results.single().content).jsonObject.getValue("version").jsonPrimitive.content
                        if (requests.size == 2) {
                            val original = db.notes().get("readable")!!
                            db.notes().upsert(original.copy(documentJson = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun("用户重写"))))).encodeToJson()))
                            reachedWrite.complete(Unit); releaseWrite.await()
                        }
                        emit(ModelEvent.ToolCall(ModelToolCall("write-${requests.size}", "write", buildJsonObject {
                            put("note_id", "readable"); put("base_version", version); put("title", "受保护标题")
                            put("document_json", NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun(if (requests.size == 2) "Agent 重写" else "用户重写，补充说明"))))).encodeToJson())
                        })))
                        emit(ModelEvent.Finished(ModelFinish.ToolCalls))
                    }
                    else -> { emit(ModelEvent.Text("已完成调整，可逐篇审阅。")); emit(ModelEvent.Finished(ModelFinish.Complete)) }
                }
            }
            val timeline = AgentTimeline(db, profiles, model, scope)
            timeline.send("修改笔记")
            reachedWrite.await()
            timeline.enqueue("下一项")
            releaseWrite.complete(Unit)
            withTimeout(5000) { timeline.state.first { it.ready && !it.running } }
            val waiting = db.agent().unfinishedRuns().single()
            assertEquals(AgentRunStatus.WaitingConflict, waiting.status)
            assertEquals(AgentQueueStatus.Paused, db.agent().pendingQueue().single().status)
            assertTrue(db.agent().noteChanges("readable").isEmpty())
            val restored = AgentTimeline(db, profiles, model, scope)
            restored.awaitReady()
            assertEquals(2, requests.size)
            restored.replanConflict(waiting.id, "write-2")
            withTimeout(5000) { restored.state.first { it.ready && !it.running } }
            assertEquals(5, requests.size)
            assertTrue(requests[2].messages.last().results.single().content.contains("edit_conflict"))
            assertEquals(AgentRunStatus.Complete, db.agent().run(waiting.id)!!.status)
            assertEquals(1, db.agent().noteChanges("readable").size)
            assertTrue(db.notes().get("readable")!!.documentJson.contains("用户重写，补充说明"))
            assertEquals(AgentQueueStatus.Paused, db.agent().pendingQueue().single().status)
            restored.reviewStore.reject("readable")
            assertTrue(db.notes().get("readable")!!.documentJson.contains("用户重写"))
            assertFalse(db.notes().get("readable")!!.documentJson.contains("补充说明"))
        }
    }

    private suspend fun seedReadableNote(db: XNoteDatabase) {
        val document = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun("受保护正文"))))).encodeToJson()
        db.notes().upsert(NoteEntity("readable", null, "受保护标题", document, null, 0, 0, 0, "受保护正文", 1, 1, null, null))
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
            db.agent().pendingQueue().forEach { db.agent().deleteQueueItem(it.id) }
            profiles.delete("profile")
            db.close()
        }
    }
}
