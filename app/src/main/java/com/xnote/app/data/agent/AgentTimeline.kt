package com.xnote.app.data.agent

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

// -- Type Definitions

data class AgentTimelineState(val ready: Boolean = false, val running: Boolean = false, val notice: String? = null)

class AgentTimeline(
    private val database: XNoteDatabase,
    private val profiles: ModelProfileStore,
    private val client: ModelClient,
    private val scope: CoroutineScope,
) {
    // -- State and Variables

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(AgentTimelineState())
    private val mutableDraft = MutableStateFlow("")
    private var runningJob: Job? = null
    private val initialization = scope.async {
        database.useWriterConnection { connection -> connection.immediateTransaction {
            database.agent().unfinishedRuns().filter { it.status == AgentRunStatus.Running || it.status == AgentRunStatus.Pending }.forEach { run ->
                database.agent().saveRun(run.copy(status = AgentRunStatus.Interrupted, updatedAtEpochMs = now(), errorCode = "process_interrupted"))
            }
            database.agent().messages().filter { it.status == AgentMessageStatus.Streaming || it.status == AgentMessageStatus.Pending }.forEach {
                database.agent().updateMessage(it.sequence, it.text, AgentMessageStatus.Interrupted)
            }
            mutableDraft.value = database.agent().draft()?.text.orEmpty()
        } }
        mutableState.value = AgentTimelineState(ready = true)
    }

    // -- Derived Values

    val state = mutableState.asStateFlow()
    val draft = mutableDraft.asStateFlow()
    val messages = database.agent().observeMessages()
    val runs = database.agent().observeRuns()

    // -- Functions

    suspend fun awaitReady() { initialization.await() }

    suspend fun saveDraft(text: String) {
        awaitReady()
        mutex.withLock {
            database.agent().saveDraft(AgentDraftEntity(text = text))
            mutableDraft.value = text
        }
    }

    suspend fun send(text: String) {
        awaitReady()
        mutex.withLock {
            if (text.isBlank()) return
            if (mutableState.value.running) throw ModelException(ModelError.Busy)
            database.agent().saveDraft(AgentDraftEntity(text = text))
            mutableDraft.value = text
            var plan: AgentContextPlan? = null
            var profile: ModelProfile? = null
            var run: AgentRunEntity? = null
            var assistantSequence = 0L
            database.useWriterConnection { connection -> connection.immediateTransaction {
                if (database.agent().unfinishedRuns().isNotEmpty()) throw ModelException(ModelError.Busy)
                val selected = profiles.active().also { it.validate() }
                val history = database.agent().messages()
                val topicBoundary = history.lastOrNull { it.role == AgentMessageRole.Event && it.text == "开始新话题" }?.sequence ?: 0
                val safeHistory = history.filter { it.sequence > topicBoundary && it.sourcesJson == "[]" }
                val completed = safeHistory.filter { it.runId != null }.groupBy { it.runId }.values.mapNotNull { exchange ->
                    val user = exchange.singleOrNull { it.role == AgentMessageRole.User && it.status == AgentMessageStatus.Complete }
                    val answer = exchange.singleOrNull { it.role == AgentMessageRole.Assistant && it.status == AgentMessageStatus.Complete }
                    if (user != null && answer != null) AgentContextTurn(user.text, answer.text) else null
                }
                val context = planAgentContext(selected, completed, text)
                var segment = database.agent().openSegment() ?: AgentSegmentEntity(id(), now()).also { database.agent().saveSegment(it) }
                val segmentCost = history.filter { it.segmentId == segment.id }.sumOf { estimatedAgentTokens(it.text).toLong() }
                if (segmentCost + estimatedAgentTokens(text) > selected.contextTokens - selected.outputTokens - ModelLimits.ToolReserveTokens) {
                    database.agent().saveSegment(segment.copy(closedAtEpochMs = now(), closeReason = "capacity"))
                    segment = AgentSegmentEntity(id(), now()).also { database.agent().saveSegment(it) }
                }
                val userId = id()
                val newRun = AgentRunEntity(id(), segment.id, userId, selected.id, selected.version, AgentRunStatus.Running, now(), now())
                database.agent().insertMessage(AgentMessageEntity(id = userId, segmentId = segment.id, runId = newRun.id,
                    role = AgentMessageRole.User, text = text, status = AgentMessageStatus.Complete, createdAtEpochMs = now()))
                assistantSequence = database.agent().insertMessage(AgentMessageEntity(id = id(), segmentId = segment.id, runId = newRun.id,
                    role = AgentMessageRole.Assistant, text = "", status = AgentMessageStatus.Streaming, createdAtEpochMs = now()))
                database.agent().saveRun(newRun)
                database.agent().saveDraft(AgentDraftEntity(text = ""))
                profile = selected; plan = context; run = newRun
            } }
            val submittedPlan = checkNotNull(plan)
            val submittedProfile = checkNotNull(profile)
            val submittedRun = checkNotNull(run)
            mutableDraft.value = ""
            mutableState.value = AgentTimelineState(true, true,
                if (submittedPlan.compressed) "已压缩完成的历史，当前消息保留全文；完整历史仍在时间线。" else null)
            runningJob = scope.launch { generate(submittedProfile, submittedRun, assistantSequence, submittedPlan) }
        }
    }

    fun stop() { runningJob?.cancel() }

    suspend fun finishUnresolved() {
        awaitReady()
        mutex.withLock {
            if (mutableState.value.running) throw ModelException(ModelError.Busy)
            database.useWriterConnection { connection -> connection.immediateTransaction {
                database.agent().unfinishedRuns().forEach { database.agent().saveRun(it.copy(status = AgentRunStatus.Cancelled, updatedAtEpochMs = now())) }
            } }
            mutableState.value = AgentTimelineState(true)
        }
    }

    suspend fun newTopic() {
        awaitReady()
        mutex.withLock {
            if (mutableState.value.running) throw ModelException(ModelError.Busy)
            database.useWriterConnection { connection -> connection.immediateTransaction {
                if (database.agent().unfinishedRuns().isNotEmpty()) throw ModelException(ModelError.Busy)
                database.agent().openSegment()?.let { database.agent().saveSegment(it.copy(closedAtEpochMs = now(), closeReason = "new_topic")) }
                val segment = AgentSegmentEntity(id(), now())
                database.agent().saveSegment(segment)
                database.agent().insertMessage(AgentMessageEntity(id = id(), segmentId = segment.id, runId = null,
                    role = AgentMessageRole.Event, text = "开始新话题", status = AgentMessageStatus.Complete, createdAtEpochMs = now()))
            } }
            mutableState.value = AgentTimelineState(true, notice = "新话题已开始，时间线记录保留。")
        }
    }

    private suspend fun generate(profile: ModelProfile, run: AgentRunEntity, sequence: Long, plan: AgentContextPlan) {
        var text = ""
        var finalRun = run
        var finish: ModelFinish? = null
        try {
            val secret = profiles.credential(profile)
            client.stream(profile, secret, ModelRequest(AgentSystemPrompt, plan.messages)).collect { event ->
                currentCoroutineContext().ensureActive()
                when (event) {
                    is ModelEvent.Text -> {
                        text += event.value
                        database.agent().updateMessage(sequence, text, AgentMessageStatus.Streaming)
                    }
                    is ModelEvent.Usage -> finalRun = finalRun.copy(inputTokens = event.inputTokens, outputTokens = event.outputTokens)
                    is ModelEvent.Finished -> finish = event.reason
                    is ModelEvent.ToolCall -> throw ModelException(ModelError.Protocol)
                    is ModelEvent.NativeParts -> Unit
                }
            }
            when (finish) {
                ModelFinish.Complete -> {
                    if (text.isBlank()) throw ModelException(ModelError.Protocol)
                    persistFinal(finalRun.copy(status = AgentRunStatus.Complete), sequence, text, AgentMessageStatus.Complete)
                }
                ModelFinish.OutputLimit -> {
                    persistFinal(finalRun.copy(status = AgentRunStatus.PausedBudget, errorCode = "output_limit"), sequence, text, AgentMessageStatus.Interrupted)
                    mutableState.value = AgentTimelineState(true, notice = "输出达到配置上限，内容已保留。可结束本次任务，缩小请求后重新发送。")
                }
                ModelFinish.Filtered -> throw ModelException(ModelError.InvalidRequest)
                else -> throw ModelException(ModelError.Interrupted)
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                try { persistFinal(finalRun.copy(status = AgentRunStatus.Cancelled), sequence, text, AgentMessageStatus.Cancelled) }
                catch (_: Exception) { mutableState.value = AgentTimelineState(true, notice = "停止状态保存失败，请检查存储后重试。") }
            }
            throw cancelled
        } catch (error: Exception) {
            try {
                val budgetExceeded = (error as? ModelException)?.error == ModelError.ContextLimit
                persistFinal(finalRun.copy(status = if (budgetExceeded) AgentRunStatus.PausedBudget else AgentRunStatus.Failed,
                    errorCode = (error as? ModelException)?.error?.name ?: "storage_or_service"),
                    sequence, text, if (budgetExceeded) AgentMessageStatus.Interrupted else AgentMessageStatus.Failed)
                mutableState.value = AgentTimelineState(true, notice = safeModelError(error))
            } catch (_: Exception) { mutableState.value = AgentTimelineState(true, notice = "回复状态保存失败，请检查存储；已保存内容保留，任务尚未完成。") }
        } finally { mutableState.value = mutableState.value.copy(running = false) }
    }

    private suspend fun persistFinal(run: AgentRunEntity, sequence: Long, text: String, status: AgentMessageStatus) {
        database.useWriterConnection { connection -> connection.immediateTransaction {
            database.agent().updateMessage(sequence, text, status)
            database.agent().saveRun(run.copy(updatedAtEpochMs = now()))
        } }
    }

    private fun id(): String = UUID.randomUUID().toString()
    private fun now(): Long = System.currentTimeMillis()
}
