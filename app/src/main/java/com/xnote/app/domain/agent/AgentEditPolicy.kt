package com.xnote.app.domain.agent

import com.xnote.app.domain.document.CurrentSchemaVersion
import com.xnote.app.domain.document.MaxTextIndent
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.NoteDocumentJson
import com.xnote.app.domain.document.TableBlock
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.document.plainText
import com.xnote.app.domain.document.splitAt
import com.xnote.app.domain.document.InlineRun
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
        val block = current.blocks.find { it.id == selection.blockId }
        val inlines = selectionInlines(current, selection)
        val changedInlines = selectionInlines(proposed, selection)
        if (selection.version != currentVersion || block == null || inlines == null || changedInlines == null || selection.start < 0 ||
            selection.end <= selection.start || selection.end > inlines.plainText().length
        ) return AgentEditValidation.InvalidSelection
        val text = inlines.plainText()
        if (listOf(selection.start, selection.end).any { it in 1 until text.length && text[it].isLowSurrogate() && text[it - 1].isHighSurrogate() })
            return AgentEditValidation.InvalidSelection
        val changed = proposed.blocks.find { it.id == block.id }
        val restored = when (changed) {
            is TextBlock -> changed.copy(inlines = inlines)
            is TableBlock -> changed.copy(rows = changed.rows.mapIndexed { rowIndex, row ->
                row.copy(cells = row.cells.mapIndexed { columnIndex, cell ->
                    if (rowIndex == selection.tableRow && columnIndex == selection.tableColumn) cell.copy(inlines = inlines) else cell
                })
            })
            else -> return AgentEditValidation.InvalidSelection
        }
        if (current.blocks.map { it.id } != proposed.blocks.map { it.id } ||
            current.blocks.filter { it.id != block.id } != proposed.blocks.filter { it.id != block.id } ||
            block != restored) return AgentEditValidation.InvalidSelection
        val suffixLength = text.length - selection.end
        val newLength = changedInlines.plainText().length
        if (newLength < selection.start + suffixLength ||
            inlines.splitAt(selection.start).first != changedInlines.splitAt(selection.start).first ||
            inlines.splitAt(selection.end).second != changedInlines.splitAt(newLength - suffixLength).second)
            return AgentEditValidation.InvalidSelection
    }
    return AgentEditValidation.Valid
}

fun selectionInlines(document: NoteDocument, selection: AgentSelection): List<InlineRun>? =
    when (val block = document.blocks.find { it.id == selection.blockId }) {
        is TextBlock -> if (selection.tableRow == null && selection.tableColumn == null) block.inlines else null
        is TableBlock -> selection.tableRow?.let { row -> selection.tableColumn?.let { column ->
            block.rows.getOrNull(row)?.cells?.getOrNull(column)?.inlines
        } }
        else -> null
    }

fun canUndoAcceptedAgentReview(acceptedAtEpochMs: Long?, nowEpochMs: Long): Boolean =
    acceptedAtEpochMs != null && nowEpochMs >= acceptedAtEpochMs && nowEpochMs - acceptedAtEpochMs < AgentAcceptedUndoRetentionMs

fun agentRestoreDecision(noteExists: Boolean, currentlyDeleted: Boolean, originalNotebookExists: Boolean): AgentRestoreDecision = when {
    !noteExists -> AgentRestoreDecision.Unrecoverable
    !currentlyDeleted -> AgentRestoreDecision.KeepUserRestoredVersion
    originalNotebookExists -> AgentRestoreDecision.RestoreOriginalNotebook
    else -> AgentRestoreDecision.RestoreUnfiled
}
