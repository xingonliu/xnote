package com.xnote.app.feature.notes

import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteLiquidGlassPanel
import com.xnote.app.design.XNoteDialog
import com.xnote.app.design.XNoteDialogAction
import com.xnote.app.design.XNoteDrawer
import com.xnote.app.design.XNoteDrawerPlacement
import com.xnote.app.design.XNoteDropdownMenu
import com.xnote.app.design.XNoteDropdownMenuItem
import com.xnote.app.design.XNoteHeader
import com.xnote.app.design.XNoteHeaderAction
import com.xnote.app.design.XNoteBottomNavigationHeight
import com.xnote.app.design.XNoteButtonSize
import com.xnote.app.design.XNoteIconSizeMedium
import com.xnote.app.design.XNoteParagraphStyle
import com.xnote.app.design.XNotePopupAnchor
import com.xnote.app.design.XNotePopupPlacement
import com.xnote.app.design.XNoteRichTextAction
import com.xnote.app.design.XNoteRichTextToolbar
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.XNoteTextField
import com.xnote.app.design.rememberXNotePopupAnchor
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.NoteListSort
import com.xnote.app.domain.model.Notebook
import com.xnote.app.domain.model.NotebookStats
import com.xnote.app.feature.notes.editor.EditorSaveStatus
import com.xnote.app.feature.notes.editor.NoteEditorSession
import com.xnote.app.feature.notes.editor.NoteImageUiState
import com.xnote.app.feature.notes.editor.NoteImageChrome
import com.xnote.app.feature.notes.editor.toDomain
import com.xnote.app.feature.background.XNoteBackgroundPicker
import com.xnote.app.navigation.NoteCollection
import com.xnote.app.navigation.NotesRoute
import com.xnote.app.domain.model.BackgroundKey
import kotlinx.coroutines.launch

// -- Constants

val XNoteEditorToolbarHeight = 64.dp

// -- Composables

@Composable
fun BoxScope.NotesChrome(
    route: NotesRoute,
    library: NoteLibrary,
    ui: NotesUiState,
    notebooks: List<Notebook>,
    notebookStats: Map<String, NotebookStats>,
    backdrop: Backdrop,
    isTablet: Boolean,
    editorSession: NoteEditorSession?,
    editorBackground: BackgroundKey,
    sortMenuAnchor: XNotePopupAnchor,
    toastHostState: SnackbarHostState,
    onOpenNotebook: (String) -> Unit,
    onCreateNote: (notebookId: String?) -> Unit,
    onPop: () -> Unit,
    onOpenReader: () -> Unit,
    onExport: () -> Unit,
    onSelectionBarHeightChanged: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val drawerPlacement = if (isTablet) XNoteDrawerPlacement.End else XNoteDrawerPlacement.Bottom
    val imageUi = remember(editorSession?.noteId) { NoteImageUiState() }
    val moreMenuAnchor = rememberXNotePopupAnchor()
    val paragraphMenuAnchor = rememberXNotePopupAnchor()
    val tableMenuAnchor = rememberXNotePopupAnchor()
    val currentNotebook = (route as? NotesRoute.Notebook)?.let { opened ->
        notebooks.firstOrNull { it.id == opened.notebookId }
    }
    val dismissEditorInput: () -> Unit = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    when (route) {
        NotesRoute.Home, is NotesRoute.Reader, is NotesRoute.Export -> Unit
        is NotesRoute.Collection -> {
            XNoteHeader(
                title = stringResource(if (route.collection == NoteCollection.All) R.string.notes_scope_all else R.string.notes_scope_unfiled),
                backdrop = backdrop,
                onBack = onPop,
                horizontalPadding = if (isTablet) 24.dp else XNoteSpacingMedium,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        is NotesRoute.Notebook -> {
            XNoteHeader(
                title = currentNotebook?.name.orEmpty(),
                backdrop = backdrop,
                onBack = onPop,
                actions = listOf(
                    XNoteHeaderAction(
                        iconRes = R.drawable.ic_keyline_stroke_more_horizontal,
                        contentDescription = stringResource(R.string.action_more),
                        onClick = { ui.moreVisible = true },
                        popupAnchor = moreMenuAnchor,
                    ),
                ),
                horizontalPadding = if (isTablet) 24.dp else XNoteSpacingMedium,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        is NotesRoute.Editor -> {
            XNoteHeader(
                title = if (editorSession?.saveStatus == EditorSaveStatus.Error) {
                    stringResource(R.string.editor_save_failed)
                } else {
                    ""
                },
                backdrop = backdrop,
                onBack = onPop,
                actions = listOf(
                    XNoteHeaderAction(
                        iconRes = R.drawable.ic_keyline_stroke_square_pen,
                        contentDescription = stringResource(R.string.notes_choose_notebook),
                        onClick = {
                            dismissEditorInput()
                            ui.moveVisible = true
                        },
                    ),
                    XNoteHeaderAction(
                        iconRes = R.drawable.ic_keyline_stroke_more_horizontal,
                        contentDescription = stringResource(R.string.action_more),
                        onClick = { ui.moreVisible = true },
                        popupAnchor = moreMenuAnchor,
                    ),
                ),
                horizontalPadding = if (isTablet) 24.dp else XNoteSpacingMedium,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    if (route is NotesRoute.Home || route is NotesRoute.Notebook || route is NotesRoute.Collection) {
        if (ui.selectedIds.isEmpty()) {
            LiquidButton(
                onClick = {
                    val notebookId = when (route) {
                        is NotesRoute.Notebook -> route.notebookId
                        NotesRoute.Home, is NotesRoute.Collection -> null
                        is NotesRoute.Editor, is NotesRoute.Reader, is NotesRoute.Export -> null
                    }
                    onCreateNote(notebookId)
                },
                backdrop = backdrop,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(
                        end = if (isTablet) 24.dp else XNoteSpacingMedium,
                        bottom = if (route is NotesRoute.Home && !isTablet) {
                            XNoteBottomNavigationHeight + XNoteSpacingSmall
                        } else {
                            XNoteSpacingMedium
                        },
                    )
                    .testTag("xnote-create-note")
                    .size(XNoteButtonSize),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyline_stroke_plus),
                    contentDescription = stringResource(R.string.action_create_note),
                    tint = Color.White,
                    modifier = Modifier.size(XNoteIconSizeMedium),
                )
            }
        } else {
            SelectionBar(
                count = ui.selectedIds.size,
                backdrop = backdrop,
                onTrash = { ui.trashConfirmVisible = true },
                onMove = { ui.moveVisible = true },
                onCancel = { ui.selectedIds = emptySet() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = if (isTablet && route is NotesRoute.Home) 112.dp else if (isTablet) 24.dp else XNoteSpacingMedium,
                        end = if (isTablet) 24.dp else XNoteSpacingMedium,
                        bottom = if (route is NotesRoute.Home && !isTablet) {
                            XNoteBottomNavigationHeight + XNoteSpacingSmall
                        } else {
                            XNoteSpacingMedium
                        },
                    )
                    .onSizeChanged { onSelectionBarHeightChanged(it.height) }
                    .testTag("xnote-note-selection-bar"),
            )
        }
    }

    if (route is NotesRoute.Editor && editorSession != null) {
        EditorToolbarBar(
            session = editorSession,
            ui = ui,
            backdrop = backdrop,
            paragraphMenuAnchor = paragraphMenuAnchor,
            tableMenuAnchor = tableMenuAnchor,
            onOpenModal = dismissEditorInput,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = if (isTablet) 24.dp else XNoteSpacingMedium)
                .padding(bottom = XNoteSpacingSmall),
        )
        NoteImageChrome(
            session = editorSession, library = library, backdrop = backdrop, isTablet = isTablet,
            toast = toastHostState,
            ui = imageUi,
            sourceAnchor = moreMenuAnchor,
        )
    }

    XNoteDrawer(
        visible = ui.createNotebookVisible,
        onDismissRequest = { ui.createNotebookVisible = false },
        title = stringResource(R.string.notes_create_notebook),
        backdrop = backdrop,
        placement = drawerPlacement,
    ) {
        XNoteTextField(
            value = ui.createNotebookName,
            onValueChange = { ui.createNotebookName = it },
            placeholder = stringResource(R.string.notes_notebook_name_placeholder),
            imeAction = ImeAction.Done,
        )
        LiquidButton(
            onClick = {
                val name = ui.createNotebookName.trim()
                if (name.isEmpty()) return@LiquidButton
                scope.launch {
                    val created = library.createNotebook(name)
                    ui.createNotebookName = ""
                    ui.createNotebookVisible = false
                    dismissEditorInput()
                    onOpenNotebook(created.id)
                }
            },
            backdrop = backdrop,
            enabled = ui.createNotebookName.trim().isNotEmpty(),
            tint = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = stringResource(R.string.action_create_notebook),
                style = MaterialTheme.typography.titleMedium,
                color = LocalContentColor.current,
            )
        }
    }

    val sortItems = buildList {
        add(NoteListSort.UpdatedAt to R.string.notes_sort_updated)
        add(NoteListSort.CreatedAt to R.string.notes_sort_created)
        add(NoteListSort.Title to R.string.notes_sort_title)
        if (route is NotesRoute.Notebook) {
            add(NoteListSort.Manual to R.string.notes_sort_manual)
        }
    }.map { (value, labelRes) ->
        val selected = if (route is NotesRoute.Notebook) ui.notebookSort == value else ui.collectionSort == value
        XNoteDropdownMenuItem(
            label = stringResource(labelRes),
            selected = selected,
            onClick = {
                if (route is NotesRoute.Notebook) {
                    ui.notebookSort = value
                } else {
                    ui.collectionSort = value
                }
            },
        )
    }
    XNoteDropdownMenu(
        expanded = ui.sortMenuVisible,
        onDismissRequest = { ui.sortMenuVisible = false },
        items = sortItems,
        backdrop = backdrop,
        anchor = sortMenuAnchor,
        placement = XNotePopupPlacement.BelowEnd,
    )

    val moreItems = when (route) {
        is NotesRoute.Notebook -> listOf(
            XNoteDropdownMenuItem(
                label = stringResource(R.string.reader_open),
                enabled = (notebookStats[route.notebookId]?.noteCount ?: 0) > 0,
                onClick = onOpenReader,
            ),
            XNoteDropdownMenuItem(
                label = stringResource(R.string.action_rename),
                onClick = {
                    ui.renameDraft = currentNotebook?.name.orEmpty()
                    ui.renameVisible = true
                },
            ),
            XNoteDropdownMenuItem(
                label = stringResource(R.string.notes_delete_notebook),
                destructive = true,
                onClick = { ui.deleteNotebookVisible = true },
            ),
        )
        is NotesRoute.Editor -> buildList {
            add(XNoteDropdownMenuItem(
                label = stringResource(R.string.export_open),
                enabled = editorSession?.note != null && !imageUi.busy,
                onClick = { dismissEditorInput(); onExport() },
            ))
            add(XNoteDropdownMenuItem(
                label = stringResource(if (imageUi.busy) R.string.image_importing else R.string.image_add),
                enabled = editorSession?.note != null && !imageUi.busy,
                onClick = { imageUi.addRequested = true },
            ))
            add(XNoteDropdownMenuItem(
                label = stringResource(R.string.reader_open),
                onClick = {
                    dismissEditorInput()
                    onOpenReader()
                },
            ))
            add(
                XNoteDropdownMenuItem(
                    label = stringResource(R.string.editor_note_background),
                    onClick = {
                        dismissEditorInput()
                        ui.backgroundPickerVisible = true
                    },
                ),
            )
            add(
                XNoteDropdownMenuItem(
                    label = stringResource(R.string.notes_move_to_notebook),
                    onClick = {
                        dismissEditorInput()
                        ui.moveVisible = true
                    },
                ),
            )
            add(
                XNoteDropdownMenuItem(
                    label = stringResource(R.string.notes_delete_notes),
                    destructive = true,
                    onClick = {
                        dismissEditorInput()
                        ui.trashConfirmVisible = true
                    },
                ),
            )
        }
        NotesRoute.Home, is NotesRoute.Reader, is NotesRoute.Export, is NotesRoute.Collection -> emptyList()
    }
    XNoteDropdownMenu(
        expanded = ui.moreVisible,
        onDismissRequest = { ui.moreVisible = false },
        items = moreItems,
        backdrop = backdrop,
        anchor = moreMenuAnchor,
        placement = XNotePopupPlacement.BelowEnd,
    )

    XNoteDrawer(
        visible = ui.backgroundPickerVisible && route is NotesRoute.Editor,
        onDismissRequest = { ui.backgroundPickerVisible = false },
        title = stringResource(R.string.background_picker_title),
        backdrop = backdrop,
        placement = drawerPlacement,
    ) {
        XNoteBackgroundPicker(
            selectedKey = editorSession?.note?.backgroundKey,
            previewBackground = editorBackground,
            scopeDescription = stringResource(R.string.background_scope_current_note),
            onSelect = { key ->
                editorSession?.let { session ->
                    scope.launch { session.setBackground(key) }
                }
            },
            allowDefaultInheritance = true,
        )
    }

    XNoteDropdownMenu(
        expanded = ui.paragraphMenuVisible,
        onDismissRequest = { ui.paragraphMenuVisible = false },
        items = XNoteParagraphStyle.entries.map { style ->
            XNoteDropdownMenuItem(
                label = stringResource(style.labelRes),
                selected = editorSession?.toolbarState?.paragraphStyle == style,
                onClick = { editorSession?.setParagraphStyle(style.toDomain()) },
            )
        },
        backdrop = backdrop,
        anchor = paragraphMenuAnchor,
        placement = XNotePopupPlacement.AboveStart,
    )

    XNoteDropdownMenu(
        expanded = ui.tableMenuVisible,
        onDismissRequest = { ui.tableMenuVisible = false },
        items = listOf(
            XNoteDropdownMenuItem(stringResource(R.string.editor_table_insert_row_above), onClick = { editorSession?.insertTableRow(false) }),
            XNoteDropdownMenuItem(stringResource(R.string.editor_table_insert_row_below), onClick = { editorSession?.insertTableRow(true) }),
            XNoteDropdownMenuItem(stringResource(R.string.editor_table_insert_column_left), onClick = { editorSession?.insertTableColumn(false) }),
            XNoteDropdownMenuItem(stringResource(R.string.editor_table_insert_column_right), onClick = { editorSession?.insertTableColumn(true) }),
            XNoteDropdownMenuItem(stringResource(R.string.editor_table_delete_row), onClick = { editorSession?.deleteTableRow() }, destructive = true),
            XNoteDropdownMenuItem(stringResource(R.string.editor_table_delete_column), onClick = { editorSession?.deleteTableColumn() }, destructive = true),
            XNoteDropdownMenuItem(stringResource(R.string.editor_table_delete), onClick = { editorSession?.deleteTable() }, destructive = true),
        ),
        backdrop = backdrop,
        anchor = tableMenuAnchor,
        placement = XNotePopupPlacement.AboveEnd,
    )

    XNoteDrawer(
        visible = ui.moveVisible,
        onDismissRequest = { ui.moveVisible = false },
        title = stringResource(R.string.notes_move_to_notebook),
        backdrop = backdrop,
        placement = drawerPlacement,
    ) {
        PickerRow(
            title = stringResource(R.string.notes_move_to_unfiled),
            subtitle = null,
            selected = route is NotesRoute.Editor && editorSession?.note?.notebookId == null,
            onClick = {
                scope.launch {
                    moveCurrentSelection(route, ui, editorSession, library, null)
                    ui.selectedIds = emptySet()
                    ui.moveVisible = false
                }
            },
        )
        notebooks.forEach { notebook ->
            PickerRow(
                title = notebook.name,
                subtitle = null,
                selected = route is NotesRoute.Editor && editorSession?.note?.notebookId == notebook.id,
                onClick = {
                    scope.launch {
                        moveCurrentSelection(route, ui, editorSession, library, notebook.id)
                        ui.selectedIds = emptySet()
                        ui.moveVisible = false
                    }
                },
            )
        }
    }

    XNoteDialog(
        visible = ui.trashConfirmVisible,
        onDismissRequest = { ui.trashConfirmVisible = false },
        title = stringResource(R.string.notes_delete_notes),
        backdrop = backdrop,
        confirmAction = XNoteDialogAction(
            label = stringResource(R.string.action_delete),
            destructive = true,
            onClick = {
                scope.launch {
                    val ids = moveIds(route, ui, editorSession)
                    library.trashNotes(ids)
                    ui.selectedIds = emptySet()
                    ui.trashConfirmVisible = false
                    if (route is NotesRoute.Editor) onPop()
                }
            },
        ),
        dismissAction = XNoteDialogAction(
            label = stringResource(R.string.action_cancel),
            onClick = { ui.trashConfirmVisible = false },
        ),
    ) {
        Text(
            text = stringResource(
                if (route is NotesRoute.Editor) {
                    R.string.notes_delete_current_note_message
                } else {
                    R.string.notes_delete_notes_message
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    val notebookNoteCount = currentNotebook?.let { notebookStats[it.id]?.noteCount ?: 0 } ?: 0
    XNoteDialog(
        visible = ui.deleteNotebookVisible,
        onDismissRequest = { ui.deleteNotebookVisible = false },
        title = stringResource(R.string.notes_delete_notebook),
        backdrop = backdrop,
        confirmAction = XNoteDialogAction(
            label = stringResource(R.string.action_delete),
            destructive = true,
            onClick = {
                val id = currentNotebook?.id
                if (id != null) {
                    scope.launch {
                        library.deleteNotebook(id)
                        ui.deleteNotebookVisible = false
                        onPop()
                    }
                }
            },
        ),
        dismissAction = XNoteDialogAction(
            label = stringResource(R.string.action_cancel),
            onClick = { ui.deleteNotebookVisible = false },
        ),
    ) {
        Text(
            text = stringResource(
                if (notebookNoteCount == 0) {
                    R.string.notes_delete_empty_notebook_message
                } else {
                    R.string.notes_delete_notebook_message
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    XNoteDialog(
        visible = ui.renameVisible,
        onDismissRequest = { ui.renameVisible = false },
        title = stringResource(R.string.notes_rename_notebook),
        backdrop = backdrop,
        confirmAction = XNoteDialogAction(
            label = stringResource(R.string.action_rename),
            enabled = ui.renameDraft.trim().isNotEmpty(),
            onClick = {
                val id = currentNotebook?.id
                val name = ui.renameDraft.trim()
                if (id != null && name.isNotEmpty()) {
                    scope.launch {
                        library.renameNotebook(id, name)
                        ui.renameVisible = false
                    }
                }
            },
        ),
        dismissAction = XNoteDialogAction(
            label = stringResource(R.string.action_cancel),
            onClick = { ui.renameVisible = false },
        ),
    ) {
        XNoteTextField(
            value = ui.renameDraft,
            onValueChange = { ui.renameDraft = it },
            placeholder = stringResource(R.string.notes_notebook_name_placeholder),
        )
    }

    XNoteDialog(
        visible = ui.linkDialogVisible,
        onDismissRequest = { ui.linkDialogVisible = false },
        title = stringResource(R.string.editor_link_title),
        backdrop = backdrop,
        confirmAction = XNoteDialogAction(
            label = stringResource(R.string.action_confirm),
            onClick = {
                editorSession?.applyLink(ui.linkDraft)
                ui.linkDialogVisible = false
            },
        ),
        dismissAction = XNoteDialogAction(
            label = if (ui.linkDraft.isBlank()) {
                stringResource(R.string.action_cancel)
            } else {
                stringResource(R.string.editor_link_remove)
            },
            onClick = {
                if (ui.linkDraft.isNotBlank()) {
                    editorSession?.applyLink(null)
                }
                ui.linkDialogVisible = false
            },
        ),
    ) {
        XNoteTextField(
            value = ui.linkDraft,
            onValueChange = { ui.linkDraft = it },
            placeholder = stringResource(R.string.editor_link_placeholder),
        )
    }
}

@Composable
private fun EditorToolbarBar(
    session: NoteEditorSession,
    ui: NotesUiState,
    backdrop: Backdrop,
    paragraphMenuAnchor: XNotePopupAnchor,
    tableMenuAnchor: XNotePopupAnchor,
    onOpenModal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidButton(
            onClick = session::undo,
            backdrop = backdrop,
            enabled = session.canUndo,
            modifier = Modifier.size(XNoteButtonSize),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_keyline_stroke_arrow_u_turn_left),
                contentDescription = stringResource(R.string.action_undo),
                tint = LocalContentColor.current,
                modifier = Modifier.size(XNoteIconSizeMedium),
            )
        }
        LiquidButton(
            onClick = session::redo,
            backdrop = backdrop,
            enabled = session.canRedo,
            modifier = Modifier.size(XNoteButtonSize),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_keyline_stroke_arrow_u_turn_right),
                contentDescription = stringResource(R.string.action_redo),
                tint = LocalContentColor.current,
                modifier = Modifier.size(XNoteIconSizeMedium),
            )
        }
        XNoteRichTextToolbar(
            state = session.toolbarState,
            onAction = { action ->
                when (action) {
                    XNoteRichTextAction.ParagraphStyle -> ui.paragraphMenuVisible = true
                    XNoteRichTextAction.Link -> {
                        val current = session.typingMarks.linkUrl.orEmpty()
                        if (session.selection.isCollapsed.not() &&
                            session.toolbarState.selectedActions.contains(XNoteRichTextAction.Link) &&
                            current.isNotEmpty()
                        ) {
                            session.applyLink(null)
                        } else {
                            onOpenModal()
                            ui.linkDraft = current
                            ui.linkDialogVisible = true
                        }
                    }
                    XNoteRichTextAction.Table -> {
                        if (!session.applyAction(action)) {
                            ui.tableMenuVisible = true
                        }
                    }
                    else -> session.applyAction(action)
                }
            },
            backdrop = backdrop,
            popupAnchors = mapOf(
                XNoteRichTextAction.ParagraphStyle to paragraphMenuAnchor,
                XNoteRichTextAction.Table to tableMenuAnchor,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    backdrop: Backdrop,
    onTrash: () -> Unit,
    onMove: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    XNoteLiquidGlassPanel(backdrop = backdrop, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(XNoteSpacingSmall),
            verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.notes_selection_count, count),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = XNoteSpacingSmall),
                )
                LiquidButton(onClick = onCancel, backdrop = backdrop) {
                    Text(stringResource(R.string.notes_cancel_selection),
                        color = LocalContentColor.current, maxLines = 1)
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
                verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
            ) {
                LiquidButton(onClick = onMove, backdrop = backdrop) {
                    Text(stringResource(R.string.notes_move_to_notebook),
                        color = LocalContentColor.current, maxLines = 1)
                }
                LiquidButton(onClick = onTrash, backdrop = backdrop) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyline_stroke_bin),
                        contentDescription = null,
                        tint = LocalContentColor.current,
                        modifier = Modifier.size(XNoteIconSizeMedium),
                    )
                    Text(stringResource(R.string.notes_delete_notes),
                        color = MaterialTheme.colorScheme.error, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int = R.drawable.ic_keyline_stroke_square_pen,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(if (selected) R.drawable.ic_keyline_stroke_check else iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(XNoteIconSizeMedium),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// -- Functions

private fun moveIds(
    route: NotesRoute,
    ui: NotesUiState,
    editorSession: NoteEditorSession?,
): Collection<String> {
    if (route is NotesRoute.Editor) {
        return listOfNotNull(editorSession?.noteId)
    }
    return ui.selectedIds
}

private suspend fun moveCurrentSelection(
    route: NotesRoute,
    ui: NotesUiState,
    editorSession: NoteEditorSession?,
    library: NoteLibrary,
    notebookId: String?,
) {
    if (route is NotesRoute.Editor && editorSession != null) {
        editorSession.moveToNotebook(notebookId)
    } else {
        library.moveNotes(ui.selectedIds, notebookId)
    }
}
