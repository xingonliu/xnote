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
        val review = database.agent().reviews(current.id).lastOrNull { it.status in setOf(AgentReviewStatus.Pending, AgentReviewStatus.Conflict) }
            ?: AgentReviewEntity(id(), current.id, AgentReviewStatus.Pending, now())
        database.agent().saveReview(review.copy(status = AgentReviewStatus.Pending))
        val change = AgentChangeEntity(id(), current.id, runId, callId, review.id, AgentChangeOrigin.Agent, AgentChangeKind.Edit,
            current.agentVersion(), saved.agentVersion(), Json.encodeToString(current), Json.encodeToString(saved), now())
        database.agent().insertChange(change)
        retainChangeMedia(change, current, saved)
        AgentReviewResult.Applied(saved, review.id)
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
        AgentReviewDetail(review, current, current?.takeIf { actionable }?.let { rollbackContent(it, changes) }, changes, undoReview)
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
        val result = rollbackContent(current, changes)
        if (result is AgentContentMerge.Conflict) {
            // An accepted batch keeps its acceptance timestamp and its remaining undo window.
            if (!accepted) database.agent().saveReview(review.copy(status = AgentReviewStatus.Conflict))
            return AgentReviewResult.Conflict(result.location)
        }
        val saved = saveContent(current, (result as AgentContentMerge.Merged).content)
        database.agent().saveReview(review.copy(status = if (accepted) AgentReviewStatus.Undone else AgentReviewStatus.Rejected, reviewedAtEpochMs = now()))
        database.agent().pruneReviewedAttachments(now() - AgentAcceptedUndoRetentionMs)
        return AgentReviewResult.Applied(saved, review.id)
    }

    private fun rollbackContent(current: NoteEntity, changes: List<AgentChangeEntity>): AgentContentMerge {
        var content = current.content()
        for (change in changes.asReversed()) {
            check(change.kind == AgentChangeKind.Edit) { "此类改动尚未开放审阅。" }
            val before = Json.decodeFromString<NoteEntity>(requireNotNull(change.beforeNoteJson)).content()
            val after = Json.decodeFromString<NoteEntity>(change.afterNoteJson).content()
            when (val inverse = mergeAgentContent(after, content, before)) {
                is AgentContentMerge.Conflict -> return inverse
                is AgentContentMerge.Merged -> content = inverse.content
            }
        }
        return AgentContentMerge.Merged(content)
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

    private suspend fun retainChangeMedia(change: AgentChangeEntity, before: NoteEntity, after: NoteEntity) {
        (before.toDomain().referencedAttachmentIds() + after.toDomain().referencedAttachmentIds()).forEach {
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
