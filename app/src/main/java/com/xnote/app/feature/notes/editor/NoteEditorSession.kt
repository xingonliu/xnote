package com.xnote.app.feature.notes.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xnote.app.domain.agent.AgentEditableContent
import com.xnote.app.domain.agent.mergeUserContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteParagraphStyle
import com.xnote.app.design.XNoteRichTextAction
import com.xnote.app.design.XNoteRichTextToolbarState
import com.xnote.app.domain.document.EditorChange
import com.xnote.app.domain.document.EditorHistory
import com.xnote.app.domain.document.ImageBlock
import com.xnote.app.domain.document.transformed
import com.xnote.app.domain.document.ImageAction
import com.xnote.app.domain.document.insertImage
import com.xnote.app.domain.document.editImage
import com.xnote.app.domain.document.replaceBlock
import com.xnote.app.domain.document.attachmentIds
import com.xnote.app.domain.document.EditorSelection
import com.xnote.app.domain.document.EditorSnapshot
import com.xnote.app.domain.document.InlineMark
import com.xnote.app.domain.document.InlineMarks
import com.xnote.app.domain.document.ListMarker
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
import com.xnote.app.domain.markdown.applyMarkdownShortcut
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.newNoteId
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

class NoteEditorSession(
    private val library: NoteLibrary,
    val noteId: String,
    private val scope: CoroutineScope,
) {
    // -- State and Variables

    var note by mutableStateOf<Note?>(null)
        private set
    var title by mutableStateOf("")
        private set
    var document by mutableStateOf(emptyNoteDocument())
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
    var toolbarHeightDp by mutableFloatStateOf(64f)
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    var markdownShortcutsEnabled by mutableStateOf(true)
    var replaceImageId by mutableStateOf<String?>(null)
    var imagePlacement by mutableStateOf<ImagePlacement?>(null)
    val attachmentOwner = newNoteId()

    private val history = EditorHistory()

    private var pendingBackground by mutableStateOf<BackgroundKey.Image?>(null)
    private var saveJob: Job? = null
    private var lastSavedTitle = ""
    private var lastSavedDocument = emptyNoteDocument()
    private var editVersion = 0L
    private var savedVersion = 0L
    private var imageGesture = 0
    private var closing = false
    private var composing = false
    private val saveMutex = Mutex()

    // -- Derived Values

    val backgroundKey: BackgroundKey?
        get() = pendingBackground ?: note?.backgroundKey

    val toolbarState: XNoteRichTextToolbarState
        get() = toolbarStateFor(document, selection, typingMarks)

    // -- Functions

    suspend fun load() = saveMutex.withLock {
        // The same session can move between phone and tablet panes during a resize.
        if (note != null || missing) return@withLock
        val loaded = library.getNote(noteId)
        if (loaded == null || loaded.isTrashed) {
            missing = true
            return@withLock
        }
        note = loaded
        title = loaded.title
        document = loaded.document
        library.retainSessionAttachments(attachmentOwner, document.attachmentIds())
        lastSavedTitle = title
        lastSavedDocument = document
        val first = document.blocks.firstOrNull()
        selection = EditorSelection(blockId = first?.id.orEmpty())
        focusBlockId = (first as? TextBlock)?.id
        editVersion = 0L
        savedVersion = 0L
    }

    suspend fun refreshFromStorage() = saveMutex.withLock {
        if (note == null || composing || closing) return@withLock
        val latest = library.getNote(noteId) ?: return@withLock
        if (latest.isTrashed || composing || closing) return@withLock
        val base = AgentEditableContent(lastSavedTitle, lastSavedDocument)
        val remote = AgentEditableContent(latest.title, latest.document)
        rebaseHistory(base, remote)
        applyContent(mergeUserContent(base, remote, AgentEditableContent(title, document)))
        lastSavedTitle = latest.title
        lastSavedDocument = latest.document
        note = latest
    }

    suspend fun moveToNotebook(notebookId: String?) {
        flushSave()
        library.moveNotes(listOf(noteId), notebookId)
        note = library.getNote(noteId)
    }

    suspend fun setBackground(backgroundKey: BackgroundKey?) {
        flushSave()
        note = library.setNoteBackground(noteId, backgroundKey)
        pendingBackground = null
    }

    fun setBackgroundMaskOpacity(opacity: Int) {
        val background = backgroundKey as? BackgroundKey.Image ?: return
        if (background.maskOpacity == opacity) return
        pendingBackground = background.copy(maskOpacity = opacity)
        scheduleSave()
    }

    fun updateTitle(value: String) {
        if (title == value) return
        captureHistory(snapshot(), key = "title")
        title = value
        scheduleSave()
    }

    fun onPlainTextChange(
        target: EditorSelection,
        oldText: String,
        newText: String,
        composing: Boolean,
    ) {
        val compositionEnded = this.composing && !composing
        this.composing = composing
        if (oldText == newText) {
            if (compositionEnded && editVersion != savedVersion) scheduleSave()
            val selectionChanged = selection != target
            selection = target
            if (!composing && selectionChanged) {
                typingMarks = currentInlines(target)?.marksAt(target.end) ?: InlineMarks()
            }
            if (compositionEnded) scope.launch { refreshFromStorage() }
            return
        }
        captureHistory(snapshot(), key = "type:${target.blockId}:${target.tableRow}:${target.tableColumn}")
        val (start, end, inserted) = findTextReplacement(oldText, newText)
        val change = document.replaceSelectedText(
            target.copy(start = start, end = end),
            inserted,
            typingMarks,
        )
        val structureChanged = change.document.blocks.map { it.id } != document.blocks.map { it.id }
        document = change.document
        selection = change.selection
        val shortcut = applyMarkdownShortcut(
            document = document,
            selection = selection,
            inserted = inserted,
            enabled = markdownShortcutsEnabled,
            composing = composing,
        )
        if (shortcut != null) {
            captureHistory(snapshot(), key = "markdown-shortcut")
            document = shortcut.document
            selection = shortcut.selection
            fieldsEpoch += 1
            focusBlockId = shortcut.selection.blockId
            typingMarks = currentInlines(shortcut.selection)?.marksAt(shortcut.selection.end) ?: InlineMarks()
        } else if (structureChanged) {
            fieldsEpoch += 1
            focusBlockId = change.selection.blockId
        }
        scheduleSave()
        if (compositionEnded) scope.launch { refreshFromStorage() }
    }

    fun deleteBackward() {
        captureHistory(snapshot())
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
                mutate { it.insertTable(selection, newNoteId(), newNoteId()) }
                focusBlockId = selection.blockId
                fieldsEpoch += 1
                true
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
        captureHistory(snapshot())
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

    fun continueAfterBlock(blockId: String) {
        val index = document.blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val next = document.blocks.getOrNull(index + 1) as? TextBlock
        if (next != null) {
            select(EditorSelection(next.id))
        } else {
            val paragraph = TextBlock(newNoteId())
            mutate { current ->
                val blocks = current.blocks.toMutableList()
                blocks.add(index + 1, paragraph)
                EditorChange(current.copy(blocks = blocks), EditorSelection(paragraph.id))
            }
        }
        focusBlockId = selection.blockId
        fieldsEpoch += 1
    }

    fun select(target: EditorSelection) {
        selection = target
        typingMarks = currentInlines(target)?.marksAt(target.end) ?: InlineMarks()
    }

    suspend fun imageFile(id: String): java.io.File? = library.getAttachment(id)?.let(library::attachmentFile)

    fun attachImage(attachmentId: String, target: EditorSelection, replaceId: String?) {
        library.retainSessionAttachments(attachmentOwner, setOf(attachmentId))
        mutate { current ->
            val existing = replaceId?.let { current.block(it) as? ImageBlock }
            if (replaceId != null) {
                if (existing == null) EditorChange(current, selection)
                else EditorChange(
                    current.replaceBlock(existing.copy(attachmentId = attachmentId)), EditorSelection(existing.id),
                )
            } else current.insertImage(target, ImageBlock(newNoteId(), attachmentId), newNoteId())
        }
        focusBlockId = null
        fieldsEpoch += 1
    }

    fun editImage(id: String, action: ImageAction) {
        mutate { it.editImage(id, action, newNoteId()) }
        focusBlockId = null
        fieldsEpoch += 1
    }

    fun transformImage(id: String, scale: Float, rotation: Float, x: Float, y: Float) {
        val image = document.block(id) as? ImageBlock ?: return
        captureHistory(snapshot(), key = "image-gesture:$id:$imageGesture")
        document = document.replaceBlock(image.transformed(scale, rotation, x, y))
        scheduleSave()
    }

    fun finishImageGesture() { imageGesture += 1 }

    fun releaseAttachments() {
        closing = true
        if (editVersion == savedVersion) library.releaseSessionAttachments(attachmentOwner)
    }

    fun undo() {
        val previous = history.undo(snapshot()) ?: return
        refreshHistoryState()
        restore(previous)
    }

    fun redo() {
        val next = history.redo(snapshot()) ?: return
        refreshHistoryState()
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
        captureHistory(snapshot())
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
        captureHistory(snapshot())
        val change = block(document)
        document = change.document
        selection = change.selection
        scheduleSave()
        return true
    }

    private fun refreshHistoryState() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    private fun captureHistory(snapshot: EditorSnapshot, key: String? = null) {
        history.capture(snapshot, key)
        refreshHistoryState()
    }

    private fun restore(snapshot: EditorSnapshot) {
        title = snapshot.title
        document = snapshot.document
        selection = snapshot.selection
        fieldsEpoch += 1
        focusBlockId = snapshot.selection.blockId.takeIf { document.block(it) !is ImageBlock }
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

    private fun rebaseHistory(base: AgentEditableContent, remote: AgentEditableContent) {
        if (base == remote) return
        history.rebase { snapshot ->
            val merged = mergeUserContent(base, remote, AgentEditableContent(snapshot.title, snapshot.document))
            snapshot.copy(title = merged.title, document = merged.document,
                selection = rebaseEditorSelection(snapshot.document, merged.document, snapshot.selection))
        }
        refreshHistoryState()
    }

    private fun applyContent(content: AgentEditableContent) {
        title = content.title
        if (document != content.document) {
            val pendingMarks = typingMarks.takeIf { selection.isCollapsed && it != currentInlines(selection)?.marksAt(selection.end) }
            selection = rebaseEditorSelection(document, content.document, selection)
            document = content.document
            library.retainSessionAttachments(attachmentOwner, document.attachmentIds())
            if (focusBlockId != null && document.block(requireNotNull(focusBlockId)) == null) focusBlockId = selection.blockId
            typingMarks = pendingMarks ?: currentInlines(selection)?.marksAt(selection.end) ?: InlineMarks()
            composing = false
            fieldsEpoch += 1
        }
    }

    private suspend fun persist(clearSavedStatusAfterDelay: Boolean = true) {
        // Debounced saves wait for IME composition; explicit flush still commits every visible character.
        if (composing && clearSavedStatusAfterDelay) return
        var committedVersion: Long? = null
        saveMutex.withLock {
            val current = note ?: return@withLock
            val versionToSave = editVersion
            val proposed = AgentEditableContent(title, document)
            val base = AgentEditableContent(lastSavedTitle, lastSavedDocument)
            val backgroundToSave = pendingBackground
            if (versionToSave == savedVersion && proposed == base && backgroundToSave == null) return@withLock
            try {
                // Once a commit begins, update its baseline even if another keystroke cancels the debounce job.
                withContext(NonCancellable) {
                    if (backgroundToSave != null) library.setNoteBackground(current.id, backgroundToSave)
                    val result = library.saveNoteContent(current.id, base, proposed)
                    val saved = result.saved
                    rebaseHistory(base, AgentEditableContent(result.before.title, result.before.document))
                    applyContent(mergeUserContent(proposed, AgentEditableContent(saved.title, saved.document), AgentEditableContent(title, document)))
                    if (pendingBackground == backgroundToSave) pendingBackground = null
                    note = saved
                    lastSavedTitle = saved.title
                    lastSavedDocument = saved.document
                    savedVersion = versionToSave
                    if (closing && editVersion == savedVersion) library.releaseSessionAttachments(attachmentOwner)
                    saveStatus = if (editVersion == versionToSave) EditorSaveStatus.Saved else EditorSaveStatus.Saving
                    committedVersion = versionToSave
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                saveStatus = if (editVersion == versionToSave) EditorSaveStatus.Error else EditorSaveStatus.Saving
            }
        }
        if (clearSavedStatusAfterDelay && committedVersion != null) {
            delay(1_200)
            if (saveStatus == EditorSaveStatus.Saved && editVersion == committedVersion) saveStatus = EditorSaveStatus.Idle
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
