package com.xnote.app.data.agent

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.xnote.app.data.db.*
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.*
import com.xnote.app.domain.text.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import java.util.UUID

// -- Type Definitions

sealed interface AgentReviewResult {
    data class Applied(val note: NoteEntity, val reviewId: String) : AgentReviewResult
    data class Unchanged(val note: NoteEntity) : AgentReviewResult
    data class Conflict(val location: String) : AgentReviewResult
    data object PermissionRequired : AgentReviewResult
    data object Unrecoverable : AgentReviewResult
}

data class AgentReviewDetail(val review: AgentReviewEntity, val current: NoteEntity?, val rollback: AgentContentMerge?,
    val changes: List<AgentChangeEntity>, val undoReview: AgentReviewEntity?)

private sealed interface AgentRollbackPlan {
    data class Ready(val note: NoteEntity) : AgentRollbackPlan
    data class Conflict(val location: String) : AgentRollbackPlan
}

/** Body, search index, provenance and review state always share the caller's Room writer transaction. */
class AgentReviewStore(private val database: XNoteDatabase, private val now: () -> Long = System::currentTimeMillis) {
    // -- Derived Values

    val reviews = database.agent().observeReviews()

    // -- Functions

    suspend fun applyEdit(runId: String, callId: String, base: AgentEditBase, proposed: AgentEditableContent,
        selection: AgentSelection? = null): AgentReviewResult = transaction {
        currentCoroutineContext().ensureActive()
        val run = requireNotNull(database.agent().run(runId))
        require(run.status == AgentRunStatus.Running)
        val current = database.notes().get(base.noteId) ?: return@transaction AgentReviewResult.Unrecoverable
        val access = AgentNoteStore(database).access(run)
        if (!access.canEdit(AgentNoteAccess(current.id, current.notebookId, current.deletedAtEpochMs != null))) return@transaction AgentReviewResult.PermissionRequired
        database.agent().committedChange(runId, callId)?.let { prior ->
            require(prior.noteId == base.noteId) { "调用 ID 不能用于另一篇笔记。" }
            return@transaction AgentReviewResult.Applied(Json.decodeFromString(prior.afterNoteJson), requireNotNull(prior.reviewId))
        }
        if (selection != null && selection.version != current.agentVersion()) return@transaction AgentReviewResult.Conflict("选区版本已变化，请重新选择。")
        require(selection == null || proposed.title == base.content.title) { "选区润色不能修改标题。" }
        require(validateAgentEdit(base.content.document, proposed.document, base.version, base.version, selection) == AgentEditValidation.Valid) {
            "改动包含非法结构、受保护媒体或无效选区。"
        }
        val merged = mergeAgentContent(base.content, current.content(), proposed)
        if (merged is AgentContentMerge.Conflict) return@transaction AgentReviewResult.Conflict(merged.location)
        val content = (merged as AgentContentMerge.Merged).content
        if (content == current.content()) return@transaction AgentReviewResult.Unchanged(current)
        require(validateAgentEdit(current.content().document, content.document, current.agentVersion(), current.agentVersion()) == AgentEditValidation.Valid)
        val saved = saveContent(current, content)
        recordChange(runId, callId, current, saved, AgentChangeKind.Edit)
    }

    suspend fun applyCreate(runId: String, callId: String, target: AgentCreateTarget, content: AgentEditableContent): AgentReviewResult = transaction {
        currentCoroutineContext().ensureActive()
        val run = requireNotNull(database.agent().run(runId))
        require(run.status == AgentRunStatus.Running)
        val notes = AgentNoteStore(database)
        if (notes.creationDecision(run, target) != AgentCreateDecision.Allowed(target)) return@transaction AgentReviewResult.PermissionRequired
        database.agent().committedChange(runId, callId)?.let { return@transaction AgentReviewResult.Applied(Json.decodeFromString(it.afterNoteJson), requireNotNull(it.reviewId)) }
        require(validateAgentEdit(NoteDocument(), content.document, "new", "new") == AgentEditValidation.Valid) { "新建内容包含非法结构或媒体。" }
        val timestamp = now()
        val saved = saveContent(NoteEntity(id(), target.notebookId, content.title, content.document.encodeToJson(), null,
            timestamp, 0, 0, "", timestamp, timestamp, null, null), content)
        recordChange(runId, callId, null, saved, AgentChangeKind.Create)
    }

    suspend fun applyTrash(runId: String, callId: String, noteId: String, version: String): AgentReviewResult = transaction {
        currentCoroutineContext().ensureActive()
        val run = requireNotNull(database.agent().run(runId))
        require(run.status == AgentRunStatus.Running)
        val current = database.notes().get(noteId) ?: return@transaction AgentReviewResult.Unrecoverable
        if (!AgentNoteStore(database).access(run).canEdit(AgentNoteAccess(current.id, current.notebookId, current.deletedAtEpochMs != null)))
            return@transaction AgentReviewResult.PermissionRequired
        database.agent().committedChange(runId, callId)?.let { return@transaction AgentReviewResult.Applied(Json.decodeFromString(it.afterNoteJson), requireNotNull(it.reviewId)) }
        if (current.agentVersion() != version) return@transaction AgentReviewResult.Conflict("笔记已变化，重新读取后才能删除")
        val saved = saveContent(current.copy(deletedAtEpochMs = now()), current.content())
        recordChange(runId, callId, current, saved, AgentChangeKind.Trash)
    }

    suspend fun detail(noteId: String): AgentReviewDetail? = transaction {
        val reviews = database.agent().reviews(noteId)
        val review = reviews.lastOrNull { it.status in setOf(AgentReviewStatus.Pending, AgentReviewStatus.Conflict) }
            ?: reviews.lastOrNull() ?: return@transaction null
        val current = database.notes().get(noteId)
        val changes = database.agent().reviewChanges(review.id)
        val actionable = review.status in setOf(AgentReviewStatus.Pending, AgentReviewStatus.Conflict, AgentReviewStatus.Accepted)
        val undoReview = reviews.lastOrNull { it.status in setOf(AgentReviewStatus.Accepted, AgentReviewStatus.Undone) }
            ?.takeIf { it.status == AgentReviewStatus.Accepted }
        val rollback = current?.takeIf { actionable }?.let { when (val plan = planRollback(it, changes)) {
            is AgentRollbackPlan.Ready -> AgentContentMerge.Merged(plan.note.content())
            is AgentRollbackPlan.Conflict -> AgentContentMerge.Conflict(plan.location)
        } }
        AgentReviewDetail(review, current, rollback, changes, undoReview)
    }

    suspend fun accept(noteId: String) = transaction {
        val review = pending(noteId)
        check(database.notes().get(noteId) != null) { "笔记已永久删除，无法接受改动。" }
        database.agent().saveReview(review.copy(status = AgentReviewStatus.Accepted, reviewedAtEpochMs = now()))
    }

    suspend fun reject(noteId: String): AgentReviewResult = transaction { rollback(pending(noteId), accepted = false) }

    suspend fun undoAccepted(noteId: String): AgentReviewResult = transaction {
        val reviewed = database.agent().reviews(noteId).lastOrNull { it.status in setOf(AgentReviewStatus.Accepted, AgentReviewStatus.Undone) }
        check(reviewed?.status == AgentReviewStatus.Accepted && canUndoAcceptedAgentReview(reviewed.reviewedAtEpochMs, now())) {
            "最近一次接受的改动不可撤回，或已超过 30 天。"
        }
        rollback(reviewed, accepted = true)
    }

    /** Only actual user changes are recorded. This method runs inside NoteLibrary's content transaction. */
    suspend fun recordUserChange(before: NoteEntity, after: NoteEntity) {
        if (before.content() == after.content() || database.agent().reviews(before.id).none {
            it.status in setOf(AgentReviewStatus.Pending, AgentReviewStatus.Conflict) ||
                (it.status == AgentReviewStatus.Accepted && canUndoAcceptedAgentReview(it.reviewedAtEpochMs, now()))
        }) return
        database.agent().insertChange(AgentChangeEntity(id(), before.id, null, null, null, AgentChangeOrigin.User, AgentChangeKind.Edit,
            before.agentVersion(), after.agentVersion(), Json.encodeToString(before), Json.encodeToString(after), now()))
    }

    private suspend fun rollback(review: AgentReviewEntity, accepted: Boolean): AgentReviewResult {
        val current = database.notes().get(review.noteId)
        if (current == null) {
            database.agent().saveReview(review.copy(status = AgentReviewStatus.Unrecoverable))
            return AgentReviewResult.Unrecoverable
        }
        val changes = database.agent().reviewChanges(review.id)
        val result = planRollback(current, changes)
        if (result is AgentRollbackPlan.Conflict) {
            // An accepted batch keeps its acceptance timestamp and its remaining undo window.
            if (!accepted) database.agent().saveReview(review.copy(status = AgentReviewStatus.Conflict))
            return AgentReviewResult.Conflict(result.location)
        }
        val planned = (result as AgentRollbackPlan.Ready).note
        val saved = if (planned == current) current else saveContent(planned, planned.content())
        database.agent().saveReview(review.copy(status = if (accepted) AgentReviewStatus.Undone else AgentReviewStatus.Rejected, reviewedAtEpochMs = now()))
        database.agent().pruneReviewedAttachments(now() - AgentAcceptedUndoRetentionMs)
        return AgentReviewResult.Applied(saved, review.id)
    }

    private suspend fun planRollback(current: NoteEntity, changes: List<AgentChangeEntity>): AgentRollbackPlan {
        var planned = current
        for (change in changes.asReversed()) {
            val after = Json.decodeFromString<NoteEntity>(change.afterNoteJson)
            when (change.kind) {
                AgentChangeKind.Create -> {
                    if (planned.content() != after.content() || planned.backgroundKey != after.backgroundKey || planned.notebookId != after.notebookId)
                        return AgentRollbackPlan.Conflict("新建笔记包含用户内容或设置，不能整篇移除")
                    planned = planned.copy(deletedAtEpochMs = planned.deletedAtEpochMs ?: current.deletedAtEpochMs ?: now())
                }
                AgentChangeKind.Trash -> {
                    if (planned.deletedAtEpochMs != null) {
                        if (planned.deletedAtEpochMs != after.deletedAtEpochMs) return AgentRollbackPlan.Conflict("删除状态已被再次改变")
                        val before = Json.decodeFromString<NoteEntity>(requireNotNull(change.beforeNoteJson))
                        val notebook = before.notebookId?.takeIf { database.notebooks().get(it) != null }
                        planned = planned.copy(notebookId = notebook, deletedAtEpochMs = null, originalNotebookName = null)
                    }
                }
                AgentChangeKind.Edit -> {
                    val before = Json.decodeFromString<NoteEntity>(requireNotNull(change.beforeNoteJson))
                    when (val inverse = mergeAgentContent(after.content(), planned.content(), before.content())) {
                        is AgentContentMerge.Conflict -> return AgentRollbackPlan.Conflict(inverse.location)
                        is AgentContentMerge.Merged -> planned = planned.copy(title = inverse.content.title, documentJson = inverse.content.document.encodeToJson())
                    }
                }
            }
        }
        return AgentRollbackPlan.Ready(planned)
    }

    private suspend fun recordChange(runId: String, callId: String, before: NoteEntity?, after: NoteEntity, kind: AgentChangeKind): AgentReviewResult.Applied {
        val review = database.agent().reviews(after.id).lastOrNull { it.status in setOf(AgentReviewStatus.Pending, AgentReviewStatus.Conflict) }
            ?: AgentReviewEntity(id(), after.id, AgentReviewStatus.Pending, now())
        database.agent().saveReview(review.copy(status = AgentReviewStatus.Pending))
        val change = AgentChangeEntity(id(), after.id, runId, callId, review.id, AgentChangeOrigin.Agent, kind,
            before?.agentVersion(), after.agentVersion(), before?.let { Json.encodeToString(it) }, Json.encodeToString(after), now())
        database.agent().insertChange(change)
        retainChangeMedia(change, before, after)
        return AgentReviewResult.Applied(after, review.id)
    }

    private suspend fun saveContent(current: NoteEntity, content: AgentEditableContent): NoteEntity {
        val domain = current.toDomain().copy(title = content.title, document = content.document, updatedAtEpochMs = now())
        val stats = visibleTextStats(domain)
        val saved = domain.copy(visibleCharacterCount = stats.characterCount, latinWordCount = stats.latinWordCount,
            summary = summarizePlainText(extractPlainText(domain))).toEntity()
        database.notes().upsert(saved)
        database.noteFts().deleteByNoteId(saved.id)
        if (saved.deletedAtEpochMs == null) database.noteFts().insert(NoteFtsEntity(0, saved.id, FtsIndexText.prepare(saved.title), FtsIndexText.prepare(extractPlainText(domain))))
        return saved
    }

    private suspend fun retainChangeMedia(change: AgentChangeEntity, before: NoteEntity?, after: NoteEntity) {
        (before?.toDomain()?.referencedAttachmentIds().orEmpty() + after.toDomain().referencedAttachmentIds()).forEach {
            database.agent().insertAttachmentRef(AgentAttachmentRefEntity("change", change.id, it))
        }
    }

    private suspend fun pending(noteId: String) = requireNotNull(database.agent().reviews(noteId).lastOrNull {
        it.status in setOf(AgentReviewStatus.Pending, AgentReviewStatus.Conflict)
    }) { "该笔记没有待审阅改动。" }

    private fun NoteEntity.content() = AgentEditableContent(title, decodeNoteDocument(documentJson))
    private suspend fun <T> transaction(block: suspend () -> T): T = database.useWriterConnection { it.immediateTransaction { block() } }
    private fun id() = UUID.randomUUID().toString()
}
