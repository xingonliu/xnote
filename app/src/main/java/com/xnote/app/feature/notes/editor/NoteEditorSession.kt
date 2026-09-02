package com.xnote.app.feature.notes.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteParagraphStyle
import com.xnote.app.design.XNoteRichTextAction
import com.xnote.app.design.XNoteRichTextToolbarState
import com.xnote.app.domain.document.EditorHistory
import com.xnote.app.domain.document.EditorSelection
import com.xnote.app.domain.document.EditorSnapshot
import com.xnote.app.domain.document.InlineMark
import com.xnote.app.domain.document.InlineMarks
import com.xnote.app.domain.document.ListMarker
import com.xnote.app.domain.document.MarkdownEditorHistory
import com.xnote.app.domain.document.MaxTextIndent
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.ParagraphStyle
import com.xnote.app.domain.document.TableBlock
import com.xnote.app.domain.document.TextAlignment
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.document.applyInlineMark
import com.xnote.app.domain.document.block
import com.xnote.app.domain.document.changeIndent
import com.xnote.app.domain.document.deleteBackward
import com.xnote.app.domain.document.deleteBlock
import com.xnote.app.domain.document.deleteTableColumn
import com.xnote.app.domain.document.deleteTableRow
import com.xnote.app.domain.document.emptyNoteDocument
import com.xnote.app.domain.document.findTextReplacement
import com.xnote.app.domain.document.hiddenBlockIds
import com.xnote.app.domain.document.insertTable
import com.xnote.app.domain.document.insertTableColumn
import com.xnote.app.domain.document.insertTableRow
import com.xnote.app.domain.document.marksAt
import com.xnote.app.domain.document.plainText
import com.xnote.app.domain.document.rangeHasLink
import com.xnote.app.domain.document.rangeHasMark
import com.xnote.app.domain.document.replaceSelectedText
import com.xnote.app.domain.document.setAlignment
import com.xnote.app.domain.document.setLink
import com.xnote.app.domain.document.setListMarker
import com.xnote.app.domain.document.setParagraphStyle
import com.xnote.app.domain.document.toggleChecked
import com.xnote.app.domain.document.toggleCollapsed
import com.xnote.app.domain.document.toggleQuoted
import com.xnote.app.domain.markdown.markdownDocumentTitle
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteKind
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.newNoteId
import com.xnote.app.domain.rules.ConversionBlocker
import com.xnote.app.domain.rules.conversionBlockers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// -- Type Definitions

enum class EditorSaveStatus {
    Idle,
    Saving,
    Saved,
    Error,
}

enum class MarkdownEditorMode {
    Editing,
    Preview,
}

class NoteEditorSession(
    private val library: NoteLibrary,
    val noteId: String,
    private val scope: CoroutineScope,
) {
    var note by mutableStateOf<Note?>(null)
        private set
    var title by mutableStateOf("")
        private set
    var document by mutableStateOf(emptyNoteDocument())
        private set
    var markdownText by mutableStateOf("")
        private set
    var markdownMode by mutableStateOf(MarkdownEditorMode.Preview)
        private set
    var selection by mutableStateOf(EditorSelection(blockId = ""))
        private set
    var typingMarks by mutableStateOf(InlineMarks())
        private set
    var saveStatus by mutableStateOf(EditorSaveStatus.Idle)
        private set
    var missing by mutableStateOf(false)
        private set
    var fieldsEpoch by mutableIntStateOf(0)
        private set
    var focusBlockId by mutableStateOf<String?>(null)
    var conversionInProgress by mutableStateOf(false)
        private set

    private val history = EditorHistory()
    private val markdownHistory = MarkdownEditorHistory()
    private var saveJob: Job? = null
    private var lastSavedTitle = ""
    private var lastSavedDocument = emptyNoteDocument()
    private var lastSavedMarkdown = ""
    private var editVersion = 0L
    private var savedVersion = 0L

    val canUndo: Boolean
        get() = if (isMarkdown) markdownHistory.canUndo else history.canUndo

    val canRedo: Boolean
        get() = if (isMarkdown) markdownHistory.canRedo else history.canRedo

    val isMarkdown: Boolean
        get() = note?.kind == NoteKind.Markdown

    val toolbarState: XNoteRichTextToolbarState
        get() = toolbarStateFor(document, selection, typingMarks)

    val markdownConversionBlockers: Set<ConversionBlocker>
        get() = if (isMarkdown) {
            setOf(ConversionBlocker.AlreadyMarkdown)
        } else {
            conversionBlockers(document)
        }

    suspend fun load() {
        val loaded = library.getNote(noteId)
        if (loaded == null || loaded.isTrashed) {
            missing = true
            return
        }
        note = loaded
        title = loaded.title
        document = loaded.document ?: emptyNoteDocument()
        markdownText = loaded.markdownText.orEmpty()
        markdownMode = MarkdownEditorMode.Preview
        lastSavedTitle = title
        lastSavedDocument = document
        lastSavedMarkdown = markdownText
        val first = document.blocks.firstOrNull()
        selection = EditorSelection(blockId = first?.id.orEmpty())
        focusBlockId = (first as? TextBlock)?.id
        editVersion = 0L
        savedVersion = 0L
    }

    suspend fun moveToNotebook(notebookId: String?) {
        flushSave()
        library.moveNotes(listOf(noteId), notebookId)
        note = library.getNote(noteId)
    }

    suspend fun setBackground(backgroundKey: BackgroundKey?) {
        flushSave()
        note = library.setNoteBackground(noteId, backgroundKey)
    }

    fun updateTitle(value: String) {
        if (isMarkdown) return
        if (title == value) return
        history.capture(snapshot(), key = "title")
        title = value
        scheduleSave()
    }

    fun updateMarkdownText(value: String) {
        if (!isMarkdown || markdownText == value) return
        markdownHistory.capture(markdownText, key = "markdown")
        markdownText = value
        title = markdownDocumentTitle(value)
        scheduleSave()
    }

    fun startMarkdownEditing() {
        if (isMarkdown) markdownMode = MarkdownEditorMode.Editing
    }

    suspend fun saveMarkdownAndPreview(): Boolean {
        if (!isMarkdown) return false
        flushSave()
        if (saveStatus == EditorSaveStatus.Error) return false
        markdownMode = MarkdownEditorMode.Preview
        return true
    }

    suspend fun convertToMarkdown(): Boolean {
        if (isMarkdown || markdownConversionBlockers.isNotEmpty()) return false
        conversionInProgress = true
        return try {
            flushSave()
            if (saveStatus == EditorSaveStatus.Error) return false
            val converted = library.convertToMarkdown(noteId)
            note = converted
            title = converted.title
            document = emptyNoteDocument()
            markdownText = converted.markdownText.orEmpty()
            markdownMode = MarkdownEditorMode.Editing
            lastSavedTitle = title
            lastSavedDocument = document
            lastSavedMarkdown = markdownText
            editVersion = 0L
            savedVersion = 0L
            saveStatus = EditorSaveStatus.Idle
            true
        } finally {
            conversionInProgress = false
        }
    }

    fun onPlainTextChange(
        target: EditorSelection,
        oldText: String,
        newText: String,
        composing: Boolean,
    ) {
        if (isMarkdown) return
        if (oldText == newText) {
            selection = target
            if (!composing) {
                typingMarks = currentInlines(target)?.marksAt(target.end) ?: InlineMarks()
            }
            return
        }
        history.capture(snapshot(), key = "type:${target.blockId}:${target.tableRow}:${target.tableColumn}")
        val (start, end, inserted) = findTextReplacement(oldText, newText)
        val change = document.replaceSelectedText(
            target.copy(start = start, end = end),
            inserted,
            typingMarks,
        )
        val structureChanged = change.document.blocks.map { it.id } != document.blocks.map { it.id }
        document = change.document
        selection = change.selection
        if (structureChanged) {
            fieldsEpoch += 1
            focusBlockId = change.selection.blockId
        }
        scheduleSave()
    }

    fun deleteBackward() {
        if (isMarkdown) return
        history.capture(snapshot())
        val change = document.deleteBackward(selection)
        val structureChanged = change.document.blocks.map { it.id } != document.blocks.map { it.id }
        document = change.document
        selection = change.selection
        if (structureChanged) {
            fieldsEpoch += 1
            focusBlockId = change.selection.blockId
        }
        scheduleSave()
    }

    fun applyAction(action: XNoteRichTextAction): Boolean {
        if (isMarkdown) return false
        return when (action) {
            XNoteRichTextAction.ParagraphStyle -> false
            XNoteRichTextAction.Bold -> applyMark(InlineMark.Bold)
            XNoteRichTextAction.Italic -> applyMark(InlineMark.Italic)
            XNoteRichTextAction.Underline -> applyMark(InlineMark.Underline)
            XNoteRichTextAction.Strikethrough -> applyMark(InlineMark.Strikethrough)
            XNoteRichTextAction.Highlight -> applyMark(InlineMark.Highlight)
            XNoteRichTextAction.Link -> false
            XNoteRichTextAction.BulletedList -> applyList(ListMarker.Bullet)
            XNoteRichTextAction.DashedList -> applyList(ListMarker.Dash)
            XNoteRichTextAction.NumberedList -> applyList(ListMarker.Numbered)
            XNoteRichTextAction.Checklist -> applyList(ListMarker.Checklist)
            XNoteRichTextAction.Quote -> mutate { it.toggleQuoted(selection) }
            XNoteRichTextAction.DecreaseIndent -> mutate { it.changeIndent(selection, -1) }
            XNoteRichTextAction.IncreaseIndent -> mutate { it.changeIndent(selection, 1) }
            XNoteRichTextAction.AlignStart -> mutate { it.setAlignment(selection, TextAlignment.Left) }
            XNoteRichTextAction.AlignCenter -> mutate { it.setAlignment(selection, TextAlignment.Center) }
            XNoteRichTextAction.AlignEnd -> mutate { it.setAlignment(selection, TextAlignment.Right) }
            XNoteRichTextAction.Table -> {
                if (selection.isTable) {
                    false
                } else {
                    mutate { it.insertTable(selection, newNoteId()) }
                    focusBlockId = selection.blockId
                    fieldsEpoch += 1
                    true
                }
            }
            XNoteRichTextAction.ToggleHeadingCollapse -> {
                mutate { it.toggleCollapsed(selection) }
                true
            }
        }
    }

    fun setParagraphStyle(style: ParagraphStyle) {
        mutate { it.setParagraphStyle(selection, style) }
    }

    fun applyLink(url: String?) {
        history.capture(snapshot())
        val (change, marks) = document.setLink(selection, url, typingMarks)
        document = change.document
        selection = change.selection
        typingMarks = marks
        if (selection.isCollapsed && !url.isNullOrBlank()) {
            fieldsEpoch += 1
        }
        scheduleSave()
    }

    fun toggleChecked() {
        mutate { it.toggleChecked(selection) }
    }

    fun insertTableRow(after: Boolean) {
        val row = selection.tableRow ?: return
        mutate { it.insertTableRow(selection, if (after) row else row - 1) }
        focusBlockId = selection.blockId
        fieldsEpoch += 1
    }

    fun insertTableColumn(after: Boolean) {
        val column = selection.tableColumn ?: return
        mutate { it.insertTableColumn(selection, if (after) column else column - 1) }
        focusBlockId = selection.blockId
        fieldsEpoch += 1
    }

    fun deleteTableRow() {
        val row = selection.tableRow ?: return
        mutate { it.deleteTableRow(selection, row, newNoteId()) }
        focusBlockId = selection.blockId
        fieldsEpoch += 1
    }

    fun deleteTableColumn() {
        val column = selection.tableColumn ?: return
        mutate { it.deleteTableColumn(selection, column, newNoteId()) }
        focusBlockId = selection.blockId
        fieldsEpoch += 1
    }

    fun deleteTable() {
        if (document.block(selection.blockId) !is TableBlock) return
        mutate { it.deleteBlock(selection.blockId, newNoteId()) }
        focusBlockId = selection.blockId
        fieldsEpoch += 1
    }

    fun select(target: EditorSelection) {
        selection = target
        typingMarks = currentInlines(target)?.marksAt(target.end) ?: InlineMarks()
    }

    fun undo() {
        if (isMarkdown) {
            markdownHistory.undo(markdownText)?.let(::restoreMarkdown)
            return
        }
        val previous = history.undo(snapshot()) ?: return
        restore(previous)
    }

    fun redo() {
        if (isMarkdown) {
            markdownHistory.redo(markdownText)?.let(::restoreMarkdown)
            return
        }
        val next = history.redo(snapshot()) ?: return
        restore(next)
    }

    suspend fun flushSave() {
        val pendingSave = saveJob
        saveJob = null
        pendingSave?.cancelAndJoin()
        withContext(NonCancellable) {
            persist(clearSavedStatusAfterDelay = false)
        }
        if (saveStatus == EditorSaveStatus.Saved) {
            saveStatus = EditorSaveStatus.Idle
        }
    }

    private fun applyMark(mark: InlineMark): Boolean {
        history.capture(snapshot())
        val (change, marks) = document.applyInlineMark(selection, mark, typingMarks)
        document = change.document
        typingMarks = marks
        scheduleSave()
        return true
    }

    private fun applyList(marker: ListMarker): Boolean {
        mutate { it.setListMarker(selection, marker) }
        return true
    }

    private fun mutate(block: (NoteDocument) -> com.xnote.app.domain.document.EditorChange): Boolean {
        history.capture(snapshot())
        val change = block(document)
        document = change.document
        selection = change.selection
        scheduleSave()
        return true
    }

    private fun restore(snapshot: EditorSnapshot) {
        title = snapshot.title
        document = snapshot.document
        selection = snapshot.selection
        fieldsEpoch += 1
        focusBlockId = snapshot.selection.blockId
        scheduleSave()
    }

    private fun restoreMarkdown(value: String) {
        markdownText = value
        title = markdownDocumentTitle(value)
        scheduleSave()
    }

    private fun snapshot(): EditorSnapshot = EditorSnapshot(
        title = title,
        document = document,
        selection = selection,
    )

    private fun currentInlines(target: EditorSelection) = if (target.isTable) {
        val table = document.block(target.blockId) as? TableBlock
        val row = target.tableRow
        val column = target.tableColumn
        if (table != null && row != null && column != null) {
            table.rows.getOrNull(row)?.cells?.getOrNull(column)?.inlines
        } else {
            null
        }
    } else {
        (document.block(target.blockId) as? TextBlock)?.inlines
    }

    private fun scheduleSave() {
        editVersion += 1L
        saveStatus = EditorSaveStatus.Saving
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(450)
            persist()
        }
    }

    private suspend fun persist(clearSavedStatusAfterDelay: Boolean = true) {
        val current = note ?: return
        val versionToSave = editVersion
        val titleToSave = if (current.kind == NoteKind.Markdown) {
            markdownDocumentTitle(markdownText)
        } else {
            title
        }
        val documentToSave = document
        val markdownToSave = markdownText
        if (versionToSave == savedVersion &&
            titleToSave == lastSavedTitle &&
            documentToSave == lastSavedDocument &&
            markdownToSave == lastSavedMarkdown
        ) {
            if (saveStatus == EditorSaveStatus.Saving) {
                saveStatus = EditorSaveStatus.Idle
            }
            return
        }
        try {
            val saved = library.saveNote(
                current.copy(
                    title = titleToSave,
                    document = if (current.kind == NoteKind.Rich) documentToSave else null,
                    markdownText = if (current.kind == NoteKind.Markdown) markdownToSave else null,
                ),
            )
            note = saved
            title = saved.title
            lastSavedTitle = saved.title
            lastSavedDocument = documentToSave
            lastSavedMarkdown = markdownToSave
            savedVersion = versionToSave
            if (editVersion == versionToSave) {
                saveStatus = EditorSaveStatus.Saved
                if (clearSavedStatusAfterDelay) {
                    delay(1_200)
                    if (saveStatus == EditorSaveStatus.Saved && editVersion == versionToSave) {
                        saveStatus = EditorSaveStatus.Idle
                    }
                }
            } else {
                saveStatus = EditorSaveStatus.Saving
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            saveStatus = if (editVersion == versionToSave) {
                EditorSaveStatus.Error
            } else {
                EditorSaveStatus.Saving
            }
        }
    }
}

// -- Functions

fun toolbarStateFor(
    document: NoteDocument,
    selection: EditorSelection,
    typingMarks: InlineMarks,
): XNoteRichTextToolbarState {
    val block = document.block(selection.blockId)
    if (block is TableBlock) {
        val inlines = currentCellInlines(block, selection)
        val marks = if (selection.isCollapsed || inlines == null) {
            typingMarks
        } else {
            InlineMarks(
                bold = inlines.rangeHasMark(selection.min, selection.max, InlineMark.Bold),
                italic = inlines.rangeHasMark(selection.min, selection.max, InlineMark.Italic),
                underline = inlines.rangeHasMark(selection.min, selection.max, InlineMark.Underline),
                strikethrough = inlines.rangeHasMark(selection.min, selection.max, InlineMark.Strikethrough),
                highlight = inlines.rangeHasMark(selection.min, selection.max, InlineMark.Highlight),
                linkUrl = if (inlines.rangeHasLink(selection.min, selection.max)) "selected" else null,
            )
        }
        return XNoteRichTextToolbarState(
            paragraphStyle = XNoteParagraphStyle.Body,
            selectedActions = buildSet {
                addInlineSelections(this, marks)
                add(XNoteRichTextAction.Table)
            },
            disabledActions = setOf(
                XNoteRichTextAction.ParagraphStyle,
                XNoteRichTextAction.BulletedList,
                XNoteRichTextAction.DashedList,
                XNoteRichTextAction.NumberedList,
                XNoteRichTextAction.Checklist,
                XNoteRichTextAction.Quote,
                XNoteRichTextAction.DecreaseIndent,
                XNoteRichTextAction.IncreaseIndent,
                XNoteRichTextAction.AlignStart,
                XNoteRichTextAction.AlignCenter,
                XNoteRichTextAction.AlignEnd,
                XNoteRichTextAction.ToggleHeadingCollapse,
            ),
        )
    }
    val text = block as? TextBlock ?: return XNoteRichTextToolbarState(
        disabledActions = XNoteRichTextAction.entries.toSet(),
    )
    val inlines = text.inlines
    val marks = if (selection.isCollapsed) {
        typingMarks
    } else {
        InlineMarks(
            bold = inlines.rangeHasMark(selection.min, selection.max, InlineMark.Bold),
            italic = inlines.rangeHasMark(selection.min, selection.max, InlineMark.Italic),
            underline = inlines.rangeHasMark(selection.min, selection.max, InlineMark.Underline),
            strikethrough = inlines.rangeHasMark(selection.min, selection.max, InlineMark.Strikethrough),
            highlight = inlines.rangeHasMark(selection.min, selection.max, InlineMark.Highlight),
            linkUrl = if (inlines.rangeHasLink(selection.min, selection.max)) "selected" else null,
        )
    }
    val heading = text.paragraphStyle == ParagraphStyle.Heading ||
        text.paragraphStyle == ParagraphStyle.Subheading
    return XNoteRichTextToolbarState(
        paragraphStyle = text.paragraphStyle.toToolbar(),
        selectedActions = buildSet {
            addInlineSelections(this, marks)
            when (text.listMarker) {
                ListMarker.Bullet -> add(XNoteRichTextAction.BulletedList)
                ListMarker.Dash -> add(XNoteRichTextAction.DashedList)
                ListMarker.Numbered -> add(XNoteRichTextAction.NumberedList)
                ListMarker.Checklist -> add(XNoteRichTextAction.Checklist)
                ListMarker.None -> Unit
            }
            if (text.quoted) add(XNoteRichTextAction.Quote)
            when (text.alignment) {
                TextAlignment.Left -> add(XNoteRichTextAction.AlignStart)
                TextAlignment.Center -> add(XNoteRichTextAction.AlignCenter)
                TextAlignment.Right -> add(XNoteRichTextAction.AlignEnd)
            }
            if (text.collapsed && heading) add(XNoteRichTextAction.ToggleHeadingCollapse)
        },
        disabledActions = buildSet {
            if (text.indent <= 0) add(XNoteRichTextAction.DecreaseIndent)
            if (text.indent >= MaxTextIndent) add(XNoteRichTextAction.IncreaseIndent)
            if (!heading) add(XNoteRichTextAction.ToggleHeadingCollapse)
        },
    )
}

fun NoteDocument.visibleBlocks(): List<com.xnote.app.domain.document.NoteBlock> {
    val hidden = hiddenBlockIds()
    return blocks.filterNot { it.id in hidden }
}

fun ParagraphStyle.toToolbar(): XNoteParagraphStyle = when (this) {
    ParagraphStyle.Body -> XNoteParagraphStyle.Body
    ParagraphStyle.Heading -> XNoteParagraphStyle.Heading
    ParagraphStyle.Subheading -> XNoteParagraphStyle.Subheading
    ParagraphStyle.Monospace -> XNoteParagraphStyle.Monospace
}

fun XNoteParagraphStyle.toDomain(): ParagraphStyle = when (this) {
    XNoteParagraphStyle.Body -> ParagraphStyle.Body
    XNoteParagraphStyle.Heading -> ParagraphStyle.Heading
    XNoteParagraphStyle.Subheading -> ParagraphStyle.Subheading
    XNoteParagraphStyle.Monospace -> ParagraphStyle.Monospace
}

private fun addInlineSelections(sink: MutableSet<XNoteRichTextAction>, marks: InlineMarks) {
    if (marks.bold) sink += XNoteRichTextAction.Bold
    if (marks.italic) sink += XNoteRichTextAction.Italic
    if (marks.underline) sink += XNoteRichTextAction.Underline
    if (marks.strikethrough) sink += XNoteRichTextAction.Strikethrough
    if (marks.highlight) sink += XNoteRichTextAction.Highlight
    if (!marks.linkUrl.isNullOrBlank()) sink += XNoteRichTextAction.Link
}

private fun currentCellInlines(table: TableBlock, selection: EditorSelection) =
    table.rows.getOrNull(selection.tableRow ?: -1)
        ?.cells
        ?.getOrNull(selection.tableColumn ?: -1)
        ?.inlines
