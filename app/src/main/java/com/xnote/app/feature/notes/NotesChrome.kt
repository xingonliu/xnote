package com.xnote.app.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteDialog
import com.xnote.app.design.XNoteDialogAction
import com.xnote.app.design.XNoteDrawer
import com.xnote.app.design.XNoteDrawerPlacement
import com.xnote.app.design.XNoteDropdownMenu
import com.xnote.app.design.XNoteDropdownMenuItem
import com.xnote.app.design.XNoteHeader
import com.xnote.app.design.XNoteHeaderAction
import com.xnote.app.design.XNoteMinimumTouchTarget
import com.xnote.app.design.XNoteParagraphStyle
import com.xnote.app.design.XNoteRichTextAction
import com.xnote.app.design.XNoteRichTextToolbar
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.XNoteTextField
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.NoteListSort
import com.xnote.app.domain.model.Notebook
import com.xnote.app.domain.model.NotebookStats
import com.xnote.app.feature.notes.editor.EditorSaveStatus
import com.xnote.app.feature.notes.editor.NoteEditorSession
import com.xnote.app.feature.notes.editor.toDomain
import com.xnote.app.navigation.NotesRoute
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
    allNotesCount: Int,
    notebookStats: Map<String, NotebookStats>,
    unfiledStats: NotebookStats,
    backdrop: Backdrop,
    isTablet: Boolean,
    editorSession: NoteEditorSession?,
    onOpenNotebook: (String) -> Unit,
    onCreateNote: (notebookId: String?) -> Unit,
    onPop: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drawerPlacement = if (isTablet) XNoteDrawerPlacement.End else XNoteDrawerPlacement.Bottom
    val currentNotebook = (route as? NotesRoute.Notebook)?.let { opened ->
        notebooks.firstOrNull { it.id == opened.notebookId }
    }

    when (route) {
        NotesRoute.Home -> Unit
        is NotesRoute.Notebook -> {
            XNoteHeader(
                title = currentNotebook?.name.orEmpty(),
                backdrop = backdrop,
                onBack = onPop,
                actions = listOf(
                    XNoteHeaderAction(
                        iconRes = R.drawable.ic_lucide_ellipsis,
                        contentDescription = stringResource(R.string.action_more),
                        onClick = { ui.moreVisible = true },
                    ),
                ),
                horizontalPadding = if (isTablet) 24.dp else XNoteSpacingMedium,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        is NotesRoute.Editor -> {
            val status = when (editorSession?.saveStatus) {
                EditorSaveStatus.Saving -> stringResource(R.string.editor_saving)
                EditorSaveStatus.Saved -> stringResource(R.string.editor_saved)
                EditorSaveStatus.Error -> stringResource(R.string.editor_save_failed)
                else -> ""
            }
            XNoteHeader(
                title = status,
                backdrop = backdrop,
                onBack = onPop,
                actions = listOf(
                    XNoteHeaderAction(
                        iconRes = R.drawable.ic_lucide_notebook_pen,
                        contentDescription = stringResource(R.string.notes_choose_notebook),
                        onClick = { ui.moveVisible = true },
                    ),
                    XNoteHeaderAction(
                        iconRes = R.drawable.ic_lucide_ellipsis,
                        contentDescription = stringResource(R.string.action_more),
                        onClick = { ui.moreVisible = true },
                    ),
                ),
                horizontalPadding = if (isTablet) 24.dp else XNoteSpacingMedium,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    if (route is NotesRoute.Home || route is NotesRoute.Notebook) {
        if (ui.selectedIds.isEmpty()) {
            LiquidButton(
                onClick = {
                    val notebookId = when (route) {
                        is NotesRoute.Notebook -> route.notebookId
                        NotesRoute.Home -> when (val current = ui.scope) {
                            NotesScope.All, NotesScope.Unfiled -> null
                            is NotesScope.Notebook -> current.id
                        }
                        is NotesRoute.Editor -> null
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
                            80.dp
                        } else {
                            XNoteSpacingMedium
                        },
                    )
                    .testTag("xnote-create-note")
                    .size(XNoteMinimumTouchTarget),
                height = XNoteMinimumTouchTarget,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_plus),
                    contentDescription = stringResource(R.string.action_create_note),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
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
                        bottom = if (route is NotesRoute.Home && !isTablet) 80.dp else XNoteSpacingMedium,
                    ),
            )
        }
    }

    if (route is NotesRoute.Editor && editorSession != null && !editorSession.isMarkdown) {
        EditorToolbarBar(
            session = editorSession,
            ui = ui,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = if (isTablet) 24.dp else XNoteSpacingMedium)
                .padding(bottom = XNoteSpacingSmall),
        )
    }

    XNoteDrawer(
        visible = ui.pickerVisible,
        onDismissRequest = { ui.pickerVisible = false },
        title = stringResource(R.string.notes_choose_notebook),
        backdrop = backdrop,
        placement = drawerPlacement,
    ) {
        PickerRow(
            title = stringResource(R.string.notes_scope_all),
            subtitle = stringResource(R.string.notes_notebook_stats, allNotesCount, notebookStats.values.sumOf { it.characterCount } + unfiledStats.characterCount),
            selected = ui.scope is NotesScope.All,
            onClick = {
                ui.scope = NotesScope.All
                ui.pickerVisible = false
            },
        )
        PickerRow(
            title = stringResource(R.string.notes_scope_unfiled),
            subtitle = stringResource(R.string.notes_unfiled_stats, unfiledStats.noteCount),
            selected = ui.scope is NotesScope.Unfiled,
            onClick = {
                ui.scope = NotesScope.Unfiled
                ui.pickerVisible = false
            },
            iconRes = R.drawable.ic_lucide_inbox,
        )
        notebooks.forEach { notebook ->
            val stats = notebookStats[notebook.id] ?: NotebookStats(0, 0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PickerRow(
                    title = notebook.name,
                    subtitle = stringResource(R.string.notes_notebook_stats, stats.noteCount, stats.characterCount),
                    selected = (ui.scope as? NotesScope.Notebook)?.id == notebook.id,
                    onClick = {
                        ui.scope = NotesScope.Notebook(notebook.id)
                        ui.pickerVisible = false
                    },
                    modifier = Modifier.weight(1f),
                )
                LiquidButton(
                    onClick = {
                        ui.pickerVisible = false
                        onOpenNotebook(notebook.id)
                    },
                    backdrop = backdrop,
                    modifier = Modifier.size(XNoteMinimumTouchTarget),
                    height = XNoteMinimumTouchTarget,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_chevron_right),
                        contentDescription = stringResource(R.string.notes_open_notebook),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.notes_create_notebook),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
                    ui.scope = NotesScope.Notebook(created.id)
                    ui.pickerVisible = false
                }
            },
            backdrop = backdrop,
            enabled = ui.createNotebookName.trim().isNotEmpty(),
            tint = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = stringResource(R.string.action_create_notebook),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = XNoteSpacingMedium),
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
        val selected = if (route is NotesRoute.Notebook) ui.notebookSort == value else ui.homeSort == value
        XNoteDropdownMenuItem(
            label = stringResource(labelRes),
            selected = selected,
            onClick = {
                if (route is NotesRoute.Notebook) {
                    ui.notebookSort = value
                } else {
                    ui.homeSort = value
                }
            },
        )
    }
    XNoteDropdownMenu(
        expanded = ui.sortMenuVisible,
        onDismissRequest = { ui.sortMenuVisible = false },
        items = sortItems,
        backdrop = backdrop,
        alignment = Alignment.TopEnd,
        modifier = Modifier.padding(top = 72.dp, end = XNoteSpacingMedium),
    )

    val moreItems = when (route) {
        is NotesRoute.Notebook -> listOf(
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
        is NotesRoute.Editor -> listOf(
            XNoteDropdownMenuItem(
                label = stringResource(R.string.notes_move_to_notebook),
                onClick = { ui.moveVisible = true },
            ),
            XNoteDropdownMenuItem(
                label = stringResource(R.string.notes_delete_notes),
                destructive = true,
                onClick = { ui.trashConfirmVisible = true },
            ),
        )
        NotesRoute.Home -> emptyList()
    }
    XNoteDropdownMenu(
        expanded = ui.moreVisible,
        onDismissRequest = { ui.moreVisible = false },
        items = moreItems,
        backdrop = backdrop,
        alignment = Alignment.TopEnd,
        modifier = Modifier.padding(top = 72.dp, end = XNoteSpacingMedium),
    )

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
        alignment = Alignment.BottomStart,
        modifier = Modifier.padding(bottom = 88.dp, start = XNoteSpacingMedium),
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
        alignment = Alignment.BottomEnd,
        modifier = Modifier.padding(bottom = 88.dp, end = XNoteSpacingMedium),
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
            modifier = Modifier.size(XNoteMinimumTouchTarget),
            height = XNoteMinimumTouchTarget,
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_undo_2),
                contentDescription = stringResource(R.string.action_undo),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        LiquidButton(
            onClick = session::redo,
            backdrop = backdrop,
            enabled = session.canRedo,
            modifier = Modifier.size(XNoteMinimumTouchTarget),
            height = XNoteMinimumTouchTarget,
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_redo_2),
                contentDescription = stringResource(R.string.action_redo),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = XNoteSpacingMedium),
        horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.notes_selection_count, count),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        LiquidButton(
            onClick = onMove,
            backdrop = backdrop,
            height = XNoteMinimumTouchTarget,
            contentPadding = PaddingValues(horizontal = XNoteSpacingMedium),
        ) {
            Text(stringResource(R.string.notes_move_to_notebook), color = MaterialTheme.colorScheme.onSurface)
        }
        LiquidButton(
            onClick = onTrash,
            backdrop = backdrop,
            height = XNoteMinimumTouchTarget,
            contentPadding = PaddingValues(horizontal = XNoteSpacingMedium),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_trash_2),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Text(stringResource(R.string.notes_delete_notes), color = MaterialTheme.colorScheme.error)
            }
        }
        LiquidButton(
            onClick = onCancel,
            backdrop = backdrop,
            height = XNoteMinimumTouchTarget,
            contentPadding = PaddingValues(horizontal = XNoteSpacingMedium),
        ) {
            Text(stringResource(R.string.notes_cancel_selection), color = MaterialTheme.colorScheme.onSurface)
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
    iconRes: Int = R.drawable.ic_lucide_notebook_pen,
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
                painter = painterResource(if (selected) R.drawable.ic_lucide_check else iconRes),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
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
