package com.xnote.app.domain.agent

import com.xnote.app.domain.document.*
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
        if (selection.version != currentVersion || !current.hasValidSelection(selection))
            return AgentEditValidation.InvalidSelection
        val range = selection.editorSelection()
        var restored = proposed
        for (part in current.selectionParts(range)) {
            val original = current.selectedInlines(part) ?: return AgentEditValidation.InvalidSelection
            val changed = proposed.selectedInlines(part) ?: return AgentEditValidation.InvalidSelection
            val suffixLength = original.plainText().length - part.max
            val newLength = changed.plainText().length
            if (newLength < part.min + suffixLength ||
                original.splitAt(part.min).first != changed.splitAt(part.min).first ||
                original.splitAt(part.max).second != changed.splitAt(newLength - suffixLength).second)
                return AgentEditValidation.InvalidSelection
            restored = restored.replaceSelectedInlines(part, original)
        }
        // All structure, media, formatting and unselected cells must be identical after restoring selected text.
        if (restored != current) return AgentEditValidation.InvalidSelection
    }
    return AgentEditValidation.Valid
}

fun AgentSelection.editorSelection() = EditorSelection(blockId, start, end, tableRow, tableColumn,
    endBlockId, endTableRow, endTableColumn)

fun NoteDocument.hasValidSelection(selection: AgentSelection): Boolean {
    val range = selection.editorSelection()
    if (range.isCollapsed) return false
    val addresses = textAddresses()
    return listOf(range.anchor(), range.focus()).all { position ->
        if (position.address !in addresses) false else {
            val text = inlinesAt(position.address).plainText()
            position.offset in 0..text.length && !(position.offset in 1 until text.length &&
                text[position.offset].isLowSurrogate() && text[position.offset - 1].isHighSurrogate())
        }
    }
}

fun selectedAgentText(document: NoteDocument, selection: AgentSelection): String? =
    if (document.hasValidSelection(selection)) document.selectedText(selection.editorSelection()) else null

fun canUndoAcceptedAgentReview(acceptedAtEpochMs: Long?, nowEpochMs: Long): Boolean =
    acceptedAtEpochMs != null && nowEpochMs >= acceptedAtEpochMs && nowEpochMs - acceptedAtEpochMs < AgentAcceptedUndoRetentionMs

fun agentRestoreDecision(noteExists: Boolean, currentlyDeleted: Boolean, originalNotebookExists: Boolean): AgentRestoreDecision = when {
    !noteExists -> AgentRestoreDecision.Unrecoverable
    !currentlyDeleted -> AgentRestoreDecision.KeepUserRestoredVersion
    originalNotebookExists -> AgentRestoreDecision.RestoreOriginalNotebook
    else -> AgentRestoreDecision.RestoreUnfiled
}
