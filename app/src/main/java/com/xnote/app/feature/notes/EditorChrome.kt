package com.xnote.app.feature.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.*
import com.xnote.app.feature.notes.editor.EditorSaveStatus
import com.xnote.app.feature.notes.editor.NoteEditorSession
import com.xnote.app.feature.notes.editor.NoteImageUiState
import com.xnote.app.feature.notes.editor.toDomain

// -- Functions

@Composable
fun EditorHeader(
    session: NoteEditorSession?,
    notebookName: String,
    backdrop: Backdrop,
    onBack: (() -> Unit)?,
    onChooseNotebook: () -> Unit,
    onMore: () -> Unit,
    moreAnchor: XNotePopupAnchor,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .height(XNoteHeaderHeight).padding(start = 12.dp, end = 12.dp, top = 11.dp)) {
        val leadingSpace = if (onBack == null) 0.dp else 48.dp
        val pillWidth = (maxWidth - 280.dp).coerceIn(88.dp, 220.dp)
            .coerceAtMost(maxWidth - leadingSpace - 140.dp)
        val pillLeft = ((maxWidth - pillWidth) / 2).coerceIn(leadingSpace, maxWidth - 140.dp - pillWidth)
        if (onBack != null) {
            XNoteLiquidGlassPanel(backdrop, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
                EditorSymbolButton("", stringResource(R.string.action_back), Modifier.fillMaxWidth().testTag("xnote-editor-back"),
                    iconRes = R.drawable.ic_keyline_stroke_chevron_left, onClick = onBack)
            }
        }
        XNoteLiquidGlassPanel(backdrop, modifier = Modifier.offset(x = pillLeft).width(pillWidth)) {
            EditorSymbolButton(
                if (session?.saveStatus == EditorSaveStatus.Error) stringResource(R.string.editor_save_failed) else "$notebookName ▾",
                stringResource(R.string.notes_choose_notebook), Modifier.fillMaxWidth().testTag("xnote-editor-notebook"),
                enabled = session?.note != null, onClick = onChooseNotebook)
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EditorSymbolButton("", stringResource(R.string.action_undo), Modifier.width(44.dp),
                enabled = session?.canUndo == true, iconRes = R.drawable.ic_keyline_stroke_arrow_u_turn_left, onClick = { session?.undo() })
            EditorSymbolButton("", stringResource(R.string.action_redo), Modifier.width(44.dp),
                enabled = session?.canRedo == true, iconRes = R.drawable.ic_keyline_stroke_arrow_u_turn_right, onClick = { session?.redo() })
            XNoteLiquidGlassPanel(backdrop, modifier = Modifier.width(44.dp)) {
                EditorSymbolButton("", stringResource(R.string.action_more), Modifier.fillMaxWidth().xNotePopupAnchor(moreAnchor),
                    iconRes = R.drawable.ic_keyline_stroke_more_horizontal, onClick = onMore)
            }
        }
    }
}

@Composable
fun EditorToolbarBar(
    session: NoteEditorSession,
    ui: NotesUiState,
    backdrop: Backdrop,
    insertMenuAnchor: XNotePopupAnchor,
    imageUi: NoteImageUiState,
    onOpenModal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var formatVisible by rememberSaveable(session.noteId) { mutableStateOf(false) }
    val reduceMotion = LocalXNoteInteractionSettings.current.reduceMotion
    val density = LocalDensity.current.density
    val state = session.toolbarState
    val inTable = session.selection.isTable && session.focusBlockId == session.selection.blockId
    val action: (XNoteRichTextAction) -> Unit = { selected ->
        if (selected == XNoteRichTextAction.Link) {
            onOpenModal()
            session.focusBlockId = null
            ui.linkDraft = session.typingMarks.linkUrl.orEmpty()
            ui.linkDialogVisible = true
        } else session.applyAction(selected)
    }
    Column(modifier.widthIn(max = 560.dp).fillMaxWidth().onSizeChanged { session.toolbarHeightDp = it.height / density }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(formatVisible && !inTable,
            enter = if (reduceMotion) EnterTransition.None else expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = if (reduceMotion) ExitTransition.None else shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()) {
            XNoteRichTextToolbar(state, action, backdrop,
                onParagraphStyle = { session.setParagraphStyle(it.toDomain()) })
        }
        XNoteLiquidGlassPanel(backdrop, shape = XNoteSmoothCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().testTag(if (inTable) "xnote-table-toolbar" else "xnote-editor-toolbar")) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (inTable) {
                    listOf(
                        Triple("+↑", R.string.editor_table_insert_row_above, { session.insertTableRow(false) }),
                        Triple("+↓", R.string.editor_table_insert_row_below, { session.insertTableRow(true) }),
                        Triple("+←", R.string.editor_table_insert_column_left, { session.insertTableColumn(false) }),
                        Triple("+→", R.string.editor_table_insert_column_right, { session.insertTableColumn(true) }),
                        Triple("−行", R.string.editor_table_delete_row, { session.deleteTableRow() }),
                        Triple("−列", R.string.editor_table_delete_column, { session.deleteTableColumn() }),
                        Triple("▦×", R.string.editor_table_delete, { session.deleteTable() }),
                    ).forEachIndexed { index, (symbol, label, callback) ->
                        EditorSymbolButton(symbol, stringResource(label), Modifier.weight(1f),
                            destructive = index >= 4, onClick = callback)
                    }
                } else {
                    EditorSymbolButton(if (imageUi.busy) "…" else "+", stringResource(if (imageUi.busy) R.string.image_importing else R.string.editor_insert),
                        Modifier.weight(1f).xNotePopupAnchor(insertMenuAnchor), enabled = session.note != null && !imageUi.busy,
                        iconRes = if (imageUi.busy) null else R.drawable.ic_keyline_stroke_plus, onClick = { imageUi.addRequested = true; formatVisible = false })
                    EditorSymbolButton("Aa", stringResource(R.string.editor_format), Modifier.weight(1f),
                        selected = formatVisible, onClick = { formatVisible = !formatVisible })
                    EditorSymbolButton("☑", stringResource(R.string.rich_text_action_checklist), Modifier.weight(1f),
                        selected = XNoteRichTextAction.Checklist in state.selectedActions,
                        enabled = XNoteRichTextAction.Checklist !in state.disabledActions,
                        iconRes = R.drawable.ic_keyline_stroke_square_check, onClick = { action(XNoteRichTextAction.Checklist) })
                    EditorSymbolButton("❝", stringResource(R.string.rich_text_action_quote), Modifier.weight(1f),
                        selected = XNoteRichTextAction.Quote in state.selectedActions,
                        enabled = XNoteRichTextAction.Quote !in state.disabledActions,
                        onClick = { action(XNoteRichTextAction.Quote) })
                    EditorSymbolButton("⌵", stringResource(R.string.editor_keyboard_done), Modifier.weight(1f),
                        iconRes = R.drawable.ic_keyline_stroke_chevron_down, onClick = { session.focusBlockId = null; onOpenModal(); formatVisible = false })
                }
            }
        }
    }
}
