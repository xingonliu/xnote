package com.xnote.app.domain.agent

import com.xnote.app.domain.document.CurrentSchemaVersion
import com.xnote.app.domain.document.MaxTextIndent
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.NoteDocumentJson
import com.xnote.app.domain.document.TableBlock
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.document.plainText
import kotlinx.serialization.json.Json

// -- Type Definitions

enum class AgentEditValidation { Valid, StaleVersion, InvalidDocument, ProtectedContent, InvalidSelection }

enum class AgentRestoreDecision { RestoreOriginalNotebook, RestoreUnfiled, KeepUserRestoredVersion, Unrecoverable }

// -- Constants

private val StrictAgentDocumentJson = Json(NoteDocumentJson) { ignoreUnknownKeys = false }

// -- Functions

fun decodeAgentDocument(json: String): NoteDocument =
    StrictAgentDocumentJson.decodeFromString(NoteDocument.serializer(), json)

fun validateAgentEdit(
    current: NoteDocument,
    proposed: NoteDocument,
    currentVersion: String,
    expectedVersion: String,
    selection: AgentSelection? = null,
): AgentEditValidation {
    if (currentVersion != expectedVersion) return AgentEditValidation.StaleVersion
    if (proposed.schemaVersion != CurrentSchemaVersion || proposed.blocks.isEmpty() ||
        proposed.blocks.any { it.id.isBlank() } || proposed.blocks.map { it.id }.distinct().size != proposed.blocks.size ||
        proposed.blocks.any { block -> when (block) {
            is TextBlock -> block.indent !in 0..MaxTextIndent
            is TableBlock -> block.rows.isEmpty() || block.rows.first().cells.isEmpty() ||
                block.rows.any { it.cells.size != block.rows.first().cells.size }
            else -> false
        } }
    ) return AgentEditValidation.InvalidDocument
    // Media properties and relative order are immutable; text/table structure may change around them.
    if (current.blocks.filterNot { it is TextBlock || it is TableBlock } !=
        proposed.blocks.filterNot { it is TextBlock || it is TableBlock }
    ) return AgentEditValidation.ProtectedContent
    if (selection != null) {
        val block = current.blocks.find { it.id == selection.blockId } as? TextBlock
        if (selection.version != currentVersion || block == null || selection.start < 0 ||
            selection.end < selection.start || selection.end > block.inlines.plainText().length
        ) return AgentEditValidation.InvalidSelection
    }
    return AgentEditValidation.Valid
}

fun canUndoAcceptedAgentReview(acceptedAtEpochMs: Long?, nowEpochMs: Long): Boolean =
    acceptedAtEpochMs != null && nowEpochMs >= acceptedAtEpochMs && nowEpochMs - acceptedAtEpochMs < AgentAcceptedUndoRetentionMs

fun agentRestoreDecision(noteExists: Boolean, currentlyDeleted: Boolean, originalNotebookExists: Boolean): AgentRestoreDecision = when {
    !noteExists -> AgentRestoreDecision.Unrecoverable
    !currentlyDeleted -> AgentRestoreDecision.KeepUserRestoredVersion
    originalNotebookExists -> AgentRestoreDecision.RestoreOriginalNotebook
    else -> AgentRestoreDecision.RestoreUnfiled
}
