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
    private val startBackground: () -> Unit = {},
) {
    // -- State and Variables

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(AgentTimelineState())
    private val mutableDraft = MutableStateFlow("")
    private var runningJob: Job? = null
    @Volatile private var interruptionReason: String? = null
    @Volatile private var stopRequested = false
    private val initialization = scope.async {
        transaction {
            database.agent().unfinishedRuns().forEach { run ->
                if (run.status in setOf(AgentRunStatus.Running, AgentRunStatus.Pending)) {
                    database.agent().saveRun(run.copy(status = AgentRunStatus.Interrupted, updatedAtEpochMs = now(), errorCode = "process_interrupted"))
                }
            }
            database.agent().messages().filter { it.role == AgentMessageRole.Assistant && it.status in setOf(AgentMessageStatus.Streaming, AgentMessageStatus.Pending) }.forEach {
                database.agent().updateMessage(it.sequence, it.text, AgentMessageStatus.Interrupted)
            }
            pauseQueue()
            mutableDraft.value = database.agent().draft()?.text.orEmpty()
        }
        mutableState.value = AgentTimelineState(ready = true)
    }

    // -- Derived Values

    val state = mutableState.asStateFlow()
    val draft = mutableDraft.asStateFlow()
    val messages = database.agent().observeMessages()
    val runs = database.agent().observeRuns()
    val queue = database.agent().observeQueue()

    // -- Functions

    suspend fun awaitReady() { initialization.await() }

    suspend fun saveDraft(text: String) {
        awaitReady()
        mutex.withLock { persistDraft(text) }
    }

    suspend fun send(text: String) {
        awaitReady()
        mutex.withLock {
            if (text.isBlank()) return
            persistDraft(text)
            if (mutableState.value.running) {
                transaction {
                    val run = database.agent().unfinishedRuns().singleOrNull { it.status == AgentRunStatus.Running } ?: throw ModelException(ModelError.Busy)
                    val profile = boundProfile(run.profileId, run.profileVersion)
                    planAgentContext(profile, emptyList(), text)
                    insertMessage(run, AgentMessageRole.User, text, AgentMessageStatus.Pending)
                    persistDraft("")
                }
                return
            }
            if (database.agent().unfinishedRuns().isNotEmpty() || database.agent().pendingQueue().isNotEmpty()) throw ModelException(ModelError.Busy)
            launchRun(createRun(text))
        }
    }

    suspend fun enqueue(text: String) {
        awaitReady()
        mutex.withLock {
            if (text.isBlank()) return
            persistDraft(text)
            transaction {
                val queued = database.agent().pendingQueue()
                require(queued.size < AgentRunLimits.MaxQueuedMessages) { "队列最多保留 50 条消息。" }
                val run = database.agent().unfinishedRuns().singleOrNull()
                val profile = when {
                    run != null -> boundProfile(run.profileId, run.profileVersion)
                    queued.isNotEmpty() -> boundProfile(queued.first().profileId, queued.first().profileVersion)
                    else -> profiles.active()
                }
                planAgentContext(profile, emptyList(), text)
                val segment = database.agent().openSegment() ?: AgentSegmentEntity(id(), now()).also { database.agent().saveSegment(it) }
                val messageId = id()
                database.agent().insertMessage(AgentMessageEntity(id = messageId, segmentId = segment.id, runId = null,
                    role = AgentMessageRole.User, text = text, status = AgentMessageStatus.Pending, createdAtEpochMs = now()))
                database.agent().saveQueueItem(AgentQueueEntity(id(), messageId, profile.id, profile.version,
                    (queued.maxOfOrNull { it.position } ?: -1) + 1,
                    if (mutableState.value.running && queued.none { it.status == AgentQueueStatus.Paused }) AgentQueueStatus.Waiting else AgentQueueStatus.Paused, now()))
                persistDraft("")
            }
        }
    }

    suspend fun editQueued(queueId: String, text: String) {
        awaitReady()
        mutex.withLock { transaction {
            require(text.isNotBlank())
            val item = database.agent().pendingQueue().single { it.id == queueId }
            planAgentContext(boundProfile(item.profileId, item.profileVersion), emptyList(), text)
            val message = database.agent().messages().single { it.id == item.messageId }
            database.agent().updateMessage(message.sequence, text, AgentMessageStatus.Pending)
        } }
    }

    suspend fun moveQueued(queueId: String, direction: Int) {
        awaitReady()
        mutex.withLock { transaction {
            require(direction == -1 || direction == 1)
            val items = database.agent().pendingQueue().toMutableList()
            val index = items.indexOfFirst { it.id == queueId }
            if (index < 0 || index + direction !in items.indices) return@transaction
            val item = items.removeAt(index)
            items.add(index + direction, item)
            items.forEachIndexed { position, row -> database.agent().saveQueueItem(row.copy(position = position.toLong())) }
        } }
    }

    suspend fun removeQueued(queueId: String) {
        awaitReady()
        mutex.withLock { transaction {
            val item = database.agent().pendingQueue().singleOrNull { it.id == queueId } ?: return@transaction
            database.agent().deleteQueueItem(item.id)
            removeMessage(item.messageId)
        } }
    }

    suspend fun resumeQueue() {
        awaitReady()
        mutex.withLock {
            if (mutableState.value.running || database.agent().unfinishedRuns().isNotEmpty()) throw ModelException(ModelError.Busy)
            transaction { database.agent().pendingQueue().forEach { database.agent().saveQueueItem(it.copy(status = AgentQueueStatus.Waiting)) } }
            dispatchNext()
        }
    }

    suspend fun continueRun(runId: String) {
        awaitReady()
        mutex.withLock {
            if (mutableState.value.running) throw ModelException(ModelError.Busy)
            val resumed = transaction {
                val run = checkNotNull(database.agent().run(runId))
                require(run.status in setOf(AgentRunStatus.Interrupted, AgentRunStatus.Failed, AgentRunStatus.Cancelled, AgentRunStatus.PausedBudget))
                if (database.agent().unfinishedRuns().any { it.id != runId }) throw ModelException(ModelError.Busy)
                require(run.errorCode != "history_removed") { "这次任务的消息已删除，不能继续；请发送新的请求。" }
                boundProfile(run.profileId, run.profileVersion)
                // An unknown write must be reconciled by the tool executor before any model request can resume.
                require(database.agent().toolEvents(runId).none { it.status in setOf(AgentToolStatus.Executing, AgentToolStatus.Unknown) }) {
                    "工具提交结果尚未核实，暂不能继续。"
                }
                insertMessage(run, AgentMessageRole.Event, "用户继续任务")
                run.copy(status = AgentRunStatus.Running, updatedAtEpochMs = now(), errorCode = null).also { database.agent().saveRun(it) }
            }
            launchRun(resumed)
        }
    }

    fun stop() { stopRequested = true; runningJob?.cancel() }

    fun interrupt(reason: String) {
        interruptionReason = reason
        runningJob?.cancel()
    }

    suspend fun finishUnresolved() {
        awaitReady()
        mutex.withLock {
            if (mutableState.value.running) throw ModelException(ModelError.Busy)
            transaction {
                database.agent().unfinishedRuns().forEach { database.agent().saveRun(it.copy(status = AgentRunStatus.Cancelled, updatedAtEpochMs = now(), grantJson = null)) }
                database.agent().messages().filter { it.runId != null && it.status == AgentMessageStatus.Pending }.forEach {
                    database.agent().updateMessage(it.sequence, it.text, AgentMessageStatus.Cancelled)
                }
            }
            mutableState.value = AgentTimelineState(true)
        }
    }

    suspend fun deleteMessage(messageId: String) {
        awaitReady()
        mutex.withLock { transaction {
            if (mutableState.value.running || database.agent().unfinishedRuns().isNotEmpty()) throw ModelException(ModelError.Busy)
            require(database.agent().pendingQueue().none { it.messageId == messageId })
            val message = database.agent().messages().singleOrNull { it.id == messageId } ?: return@transaction
            // Invalidate the exchange for future context; related replies can contain deleted input.
            message.runId?.let { database.agent().run(it) }?.let { database.agent().saveRun(it.copy(errorCode = "history_removed")) }
            removeMessage(messageId)
        } }
    }

    suspend fun clearChat() {
        awaitReady()
        mutex.withLock {
            stopRequested = true
            runningJob?.cancelAndJoin()
            transaction {
                database.agent().unfinishedRuns().forEach { database.agent().saveRun(it.copy(status = AgentRunStatus.Cancelled, updatedAtEpochMs = now(), grantJson = null)) }
                database.agent().pendingQueue().forEach { database.agent().deleteQueueItem(it.id) }
                database.agent().messages().forEach { removeMessage(it.id) }
                database.agent().openSegment()?.let { database.agent().saveSegment(it.copy(closedAtEpochMs = now(), closeReason = "clear_chat")) }
                persistDraft("")
            }
            mutableState.value = AgentTimelineState(true)
        }
    }

    suspend fun newTopic() {
        awaitReady()
        mutex.withLock { transaction {
            if (mutableState.value.running || database.agent().unfinishedRuns().isNotEmpty() || database.agent().pendingQueue().isNotEmpty()) throw ModelException(ModelError.Busy)
            database.agent().openSegment()?.let { database.agent().saveSegment(it.copy(closedAtEpochMs = now(), closeReason = "new_topic")) }
            val segment = AgentSegmentEntity(id(), now())
            database.agent().saveSegment(segment)
            database.agent().insertMessage(AgentMessageEntity(id = id(), segmentId = segment.id, runId = null,
                role = AgentMessageRole.Event, text = "开始新话题", status = AgentMessageStatus.Complete, createdAtEpochMs = now()))
            mutableState.value = AgentTimelineState(true, notice = "新话题已开始，时间线记录保留。")
        } }
    }

    private suspend fun createRun(text: String, queued: AgentQueueEntity? = null): AgentRunEntity = transaction {
        val profile = if (queued == null) profiles.active() else boundProfile(queued.profileId, queued.profileVersion)
        profile.validate()
        planAgentContext(profile, emptyList(), text)
        var segment = database.agent().openSegment() ?: AgentSegmentEntity(id(), now()).also { database.agent().saveSegment(it) }
        val cost = database.agent().messages().filter { it.segmentId == segment.id }.sumOf { estimatedAgentTokens(it.text).toLong() }
        if (cost + estimatedAgentTokens(text) > profile.contextTokens - profile.outputTokens - ModelLimits.ToolReserveTokens) {
            database.agent().saveSegment(segment.copy(closedAtEpochMs = now(), closeReason = "capacity"))
            segment = AgentSegmentEntity(id(), now()).also { database.agent().saveSegment(it) }
        }
        val userId = queued?.messageId ?: id()
        val run = AgentRunEntity(id(), segment.id, userId, profile.id, profile.version, AgentRunStatus.Running, now(), now())
        if (queued == null) {
            database.agent().insertMessage(AgentMessageEntity(id = userId, segmentId = segment.id, runId = run.id,
                role = AgentMessageRole.User, text = text, status = AgentMessageStatus.Complete, createdAtEpochMs = now()))
        } else {
            database.agent().dispatchMessage(userId, run.id, segment.id)
            database.agent().saveQueueItem(queued.copy(status = AgentQueueStatus.Dispatched))
        }
        database.agent().saveRun(run)
        if (queued == null) persistDraft("")
        run
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun launchRun(run: AgentRunEntity) {
        interruptionReason = null
        stopRequested = false
        mutableState.value = AgentTimelineState(true, true)
        // Enter the cancellation handler even if the service interrupts before the coroutine starts.
        val backgroundReady = CompletableDeferred<Unit>()
        val job = scope.launch(start = CoroutineStart.ATOMIC) { generate(run, backgroundReady) }
        runningJob = job
        job.invokeOnCompletion {
            scope.launch { mutex.withLock {
                if (runningJob === job) {
                    runningJob = null
                    if (stopRequested) transaction { pauseQueue() }
                    else if (database.agent().run(run.id)?.status == AgentRunStatus.Complete) dispatchNext()
                    if (runningJob == null) mutableState.value = mutableState.value.copy(running = false)
                }
            } }
        }
        try { startBackground() } catch (_: Exception) { interruptionReason = "background_unavailable" }
        backgroundReady.complete(Unit)
    }

    private suspend fun dispatchNext() {
        if (database.agent().unfinishedRuns().isNotEmpty()) return
        val item = database.agent().pendingQueue().firstOrNull() ?: return
        if (item.status != AgentQueueStatus.Waiting) return
        try {
            val text = database.agent().messages().single { it.id == item.messageId }.text
            launchRun(createRun(text, item))
        } catch (error: Exception) {
            transaction { pauseQueue() }
            mutableState.value = AgentTimelineState(true, notice = safeModelError(error))
        }
    }

    private suspend fun generate(initialRun: AgentRunEntity, backgroundReady: Deferred<Unit>) {
        var run = initialRun
        var sequence: Long? = null
        var text = ""
        try {
            backgroundReady.await()
            if (interruptionReason != null) throw CancellationException()
            withTimeout(AgentRunLimits.MaxRunDurationMs) {
                val profile = boundProfile(run.profileId, run.profileVersion)
                val secret = profiles.credential(profile)
                var retry = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    sequence = null
                    text = ""
                    val plan = transaction {
                        val history = database.agent().messages()
                        val attempts = history.count { it.runId == run.id && it.role == AgentMessageRole.Event && it.text == "模型请求" }
                        if (attempts >= AgentRunLimits.MaxRequests) throw AgentBudgetException()
                        val current = history.filter { it.runId == run.id && it.role == AgentMessageRole.User && it.sourcesJson == "[]" }
                        val goal = current.joinToString("\n\n[用户补充]\n") { it.text }
                        require(goal.isNotBlank())
                        val boundary = history.lastOrNull { it.role == AgentMessageRole.Event && it.text == "开始新话题" }?.sequence ?: 0
                        val turns = history.filter { it.sequence > boundary && it.runId != run.id && it.runId != null }.groupBy { it.runId }.values.mapNotNull { exchange ->
                            val previousRun = database.agent().run(checkNotNull(exchange.first().runId))
                            if (previousRun?.status != AgentRunStatus.Complete || previousRun.errorCode == "history_removed" || exchange.any { it.sourcesJson != "[]" }) return@mapNotNull null
                            val users = exchange.filter { it.role == AgentMessageRole.User && it.status == AgentMessageStatus.Complete }
                            val answer = exchange.lastOrNull { it.role == AgentMessageRole.Assistant && it.status == AgentMessageStatus.Complete }
                            if (users.isNotEmpty() && answer != null) AgentContextTurn(users.joinToString("\n") { it.text }, answer.text) else null
                        }
                        val execution = history.filter { it.runId == run.id && it.sourcesJson == "[]" &&
                            it.role in setOf(AgentMessageRole.User, AgentMessageRole.Assistant) && it.text.isNotBlank() }
                            .map { ModelMessage(it.role, it.text) }.toMutableList()
                        if (execution.lastOrNull()?.role == AgentMessageRole.Assistant) execution += ModelMessage(AgentMessageRole.User, "请基于已保存的进度继续完成当前任务。")
                        val plan = planAgentExecutionContext(profile, turns, execution)
                        current.filter { it.status == AgentMessageStatus.Pending }.forEach { database.agent().updateMessage(it.sequence, it.text, AgentMessageStatus.Complete) }
                        insertMessage(run, AgentMessageRole.Event, "模型请求")
                        sequence = insertMessage(run, AgentMessageRole.Assistant, "", AgentMessageStatus.Streaming)
                        plan
                    }
                    text = ""
                    var finish: ModelFinish? = null
                    try {
                        client.stream(profile, secret, ModelRequest(AgentSystemPrompt, plan.messages)).collect { event ->
                            currentCoroutineContext().ensureActive()
                            when (event) {
                                is ModelEvent.Text -> {
                                    text += event.value
                                    database.agent().updateMessage(checkNotNull(sequence), text, AgentMessageStatus.Streaming)
                                }
                                is ModelEvent.Usage -> run = run.copy(inputTokens = addUsage(run.inputTokens, event.inputTokens), outputTokens = addUsage(run.outputTokens, event.outputTokens))
                                is ModelEvent.Finished -> finish = event.reason
                                is ModelEvent.ToolCall -> {
                                    database.agent().saveToolEvent(AgentToolEventEntity(id(), run.id, event.value.id, event.value.name,
                                        event.value.arguments.toString(), null, AgentToolStatus.Denied, 0, now()))
                                    throw ModelException(ModelError.Protocol)
                                }
                                is ModelEvent.NativeParts -> Unit
                            }
                        }
                    } catch (failure: ModelException) {
                        if (text.isEmpty() && failure.error in setOf(ModelError.Network, ModelError.Service, ModelError.Timeout) && retry < AgentRunLimits.MaxNetworkRetries) {
                            transaction {
                                database.agent().updateMessage(checkNotNull(sequence), text, AgentMessageStatus.Failed)
                                insertMessage(run, AgentMessageRole.Event, "网络重试 ${retry + 1}/${AgentRunLimits.MaxNetworkRetries}")
                            }
                            delay(AgentRunLimits.RetryDelayMs * (1L shl retry++))
                            continue
                        }
                        throw failure
                    }
                    if (finish == ModelFinish.OutputLimit) throw AgentBudgetException()
                    if (finish == ModelFinish.Filtered) throw ModelException(ModelError.InvalidRequest)
                    if (finish != ModelFinish.Complete || text.isBlank()) throw ModelException(ModelError.Interrupted)
                    // Sharing the submission mutex makes the final boundary atomic with supplements.
                    val hasSupplement = mutex.withLock { transaction {
                        database.agent().updateMessage(checkNotNull(sequence), text, AgentMessageStatus.Complete)
                        val pending = database.agent().messages().any { it.runId == run.id && it.role == AgentMessageRole.User && it.status == AgentMessageStatus.Pending }
                        if (!pending) {
                            run = run.copy(status = AgentRunStatus.Complete, updatedAtEpochMs = now())
                            database.agent().saveRun(run)
                        }
                        pending
                    } }
                    if (!hasSupplement) break
                    retry = 0
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                val interrupted = interruptionReason != null || cancelled is TimeoutCancellationException
                run = run.copy(status = if (interrupted) AgentRunStatus.Interrupted else AgentRunStatus.Cancelled,
                    errorCode = interruptionReason ?: if (interrupted) "run_timeout" else null)
                persistStopped(run, sequence, text, if (interrupted) AgentMessageStatus.Interrupted else AgentMessageStatus.Cancelled)
                if (interrupted) mutableState.value = AgentTimelineState(true, true, notice = "后台执行已中断，内容已保存；回到前台后可继续。")
            }
        } catch (error: Exception) {
            val budget = error is AgentBudgetException || (error as? ModelException)?.error == ModelError.ContextLimit
            run = run.copy(status = if (budget) AgentRunStatus.PausedBudget else AgentRunStatus.Failed,
                errorCode = if (budget) "budget_limit" else (error as? ModelException)?.error?.name ?: "storage_or_service")
            persistStopped(run, sequence, text, if (budget) AgentMessageStatus.Interrupted else AgentMessageStatus.Failed)
            mutableState.value = AgentTimelineState(true, true, notice = if (budget) "达到运行或容量上限，已保留内容；请结束任务后缩小请求。" else safeModelError(error))
        }
    }

    private suspend fun persistStopped(run: AgentRunEntity, sequence: Long?, text: String, status: AgentMessageStatus) {
        transaction {
            sequence?.let { database.agent().updateMessage(it, text, status) }
            database.agent().saveRun(run.copy(updatedAtEpochMs = now(), grantJson = if (run.status == AgentRunStatus.Cancelled) null else run.grantJson))
            pauseQueue()
        }
    }

    private suspend fun pauseQueue() {
        database.agent().pendingQueue().forEach { database.agent().saveQueueItem(it.copy(status = AgentQueueStatus.Paused)) }
    }

    private suspend fun boundProfile(id: String, version: Long): ModelProfile = profiles.list().singleOrNull { it.id == id && it.version == version && it.enabled }
        ?: throw ModelException(ModelError.InvalidConfig)

    private suspend fun persistDraft(text: String) {
        database.agent().saveDraft(AgentDraftEntity(text = text))
        mutableDraft.value = text
    }

    private suspend fun insertMessage(run: AgentRunEntity, role: AgentMessageRole, text: String, status: AgentMessageStatus = AgentMessageStatus.Complete): Long =
        database.agent().insertMessage(AgentMessageEntity(id = id(), segmentId = run.segmentId, runId = run.id, role = role,
            text = text, status = status, createdAtEpochMs = now()))

    private suspend fun removeMessage(id: String) {
        database.agent().deleteSnapshotRefs(id)
        database.agent().deleteMessageAttachments(id)
        database.agent().deleteMessage(id)
        database.agent().deleteUnusedSnapshots()
        database.agent().deleteUnusedSnapshotAttachments()
    }

    private suspend fun <T> transaction(block: suspend () -> T): T = database.useWriterConnection { it.immediateTransaction { block() } }
    private fun addUsage(old: Long?, value: Long?): Long? = if (value == null) old else (old ?: 0) + value
    private fun id(): String = UUID.randomUUID().toString()
    private fun now(): Long = System.currentTimeMillis()
}
