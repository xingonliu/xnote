package com.xnote.app.feature.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.EditorSymbolButton
import com.xnote.app.design.EditorGlassIconButton
import com.xnote.app.design.LocalXNoteInteractionSettings
import com.xnote.app.design.XNoteButtonSize
import com.xnote.app.design.XNoteHeaderTopPadding
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteHeaderHeight
import com.xnote.app.design.XNotePopupAnchor
import com.xnote.app.design.XNoteRichTextAction
import com.xnote.app.design.XNoteRichTextToolbar
import com.xnote.app.design.XNoteShortAnimationDurationMillis
import com.xnote.app.design.XNoteSpacingExtraSmall
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.design.xNotePopupAnchor
import com.xnote.app.feature.notes.editor.NoteEditorSession
import com.xnote.app.feature.notes.editor.NoteImageUiState
import com.xnote.app.feature.notes.editor.toDomain

// -- Functions

@Composable
fun EditorHeader(
    session: NoteEditorSession?,
    backdrop: Backdrop,
    onBack: (() -> Unit)?,
    onMore: () -> Unit,
    moreAnchor: XNotePopupAnchor,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(XNoteHeaderHeight)
            .padding(start = XNoteSpacingMedium, end = XNoteSpacingMedium, top = XNoteHeaderTopPadding),
        horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            EditorGlassIconButton(
                iconRes = R.drawable.ic_keyline_stroke_chevron_left,
                description = stringResource(R.string.action_back),
                backdrop = backdrop,
                onClick = onBack,
                modifier = Modifier
                    .size(XNoteButtonSize)
                    .testTag("xnote-editor-back"),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorGlassIconButton(
                iconRes = R.drawable.ic_keyline_stroke_arrow_u_turn_left,
                description = stringResource(R.string.action_undo),
                backdrop = backdrop,
                enabled = session?.canUndo == true,
                onClick = { session?.undo() },
                modifier = Modifier.size(XNoteButtonSize).testTag("xnote-editor-history"),
            )
            EditorGlassIconButton(
                iconRes = R.drawable.ic_keyline_stroke_more_horizontal,
                description = stringResource(R.string.action_more),
                backdrop = backdrop,
                onClick = onMore,
                modifier = Modifier
                    .size(XNoteButtonSize)
                    .xNotePopupAnchor(moreAnchor),
            )
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
    var toolsExpanded by rememberSaveable(session.noteId) { mutableStateOf(true) }
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
    Column(
        modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .onSizeChanged { session.toolbarHeightDp = it.height / density },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = formatVisible && !inTable && toolsExpanded,
            enter = if (reduceMotion) EnterTransition.None else expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = if (reduceMotion) ExitTransition.None else shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            XNoteRichTextToolbar(state, action, backdrop, onParagraphStyle = { session.setParagraphStyle(it.toDomain()) })
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(if (inTable) "xnote-table-toolbar" else "xnote-editor-toolbar"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorGlassIconButton(
                iconRes = R.drawable.ic_keyline_stroke_plus,
                description = stringResource(if (imageUi.busy) R.string.image_importing else R.string.editor_insert),
                backdrop = backdrop,
                enabled = session.note != null && !imageUi.busy,
                onClick = {
                    imageUi.addRequested = true
                    formatVisible = false
                },
                modifier = Modifier
                    .size(XNoteButtonSize)
                    .xNotePopupAnchor(insertMenuAnchor),
            )
            if (inTable) {
                TableToolbarCapsule(session = session, backdrop = backdrop)
            } else {
                EditorToolsCapsule(
                    expanded = toolsExpanded,
                    reduceMotion = reduceMotion,
                    backdrop = backdrop,
                    formatVisible = formatVisible,
                    checklistSelected = XNoteRichTextAction.Checklist in state.selectedActions,
                    checklistEnabled = XNoteRichTextAction.Checklist !in state.disabledActions,
                    quoteSelected = XNoteRichTextAction.Quote in state.selectedActions,
                    quoteEnabled = XNoteRichTextAction.Quote !in state.disabledActions,
                    onFormat = { formatVisible = !formatVisible },
                    onChecklist = { action(XNoteRichTextAction.Checklist) },
                    onQuote = { action(XNoteRichTextAction.Quote) },
                    onToggleExpanded = {
                        if (toolsExpanded) {
                            toolsExpanded = false
                            formatVisible = false
                            onOpenModal()
                        } else {
                            toolsExpanded = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EditorToolsCapsule(
    expanded: Boolean,
    reduceMotion: Boolean,
    backdrop: Backdrop,
    formatVisible: Boolean,
    checklistSelected: Boolean,
    checklistEnabled: Boolean,
    quoteSelected: Boolean,
    quoteEnabled: Boolean,
    onFormat: () -> Unit,
    onChecklist: () -> Unit,
    onQuote: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val widthSpec = tween<IntSize>(XNoteShortAnimationDurationMillis)
    val fadeSpec = tween<Float>(XNoteShortAnimationDurationMillis)
    LiquidButton(backdrop = backdrop) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = expanded,
                enter = if (reduceMotion) {
                    EnterTransition.None
                } else {
                    expandHorizontally(expandFrom = Alignment.End, animationSpec = widthSpec) + fadeIn(fadeSpec)
                },
                exit = if (reduceMotion) {
                    ExitTransition.None
                } else {
                    shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = widthSpec) + fadeOut(fadeSpec)
                },
            ) {
                Row {
                    EditorSymbolButton(
                        description = stringResource(R.string.editor_format),
                        iconRes = R.drawable.ic_keyline_stroke_pen_line,
                        selected = formatVisible,
                        modifier = Modifier.size(XNoteButtonSize),
                        onClick = onFormat,
                    )
                    EditorSymbolButton(
                        description = stringResource(R.string.rich_text_action_checklist),
                        iconRes = R.drawable.ic_keyline_stroke_square_check,
                        selected = checklistSelected,
                        enabled = checklistEnabled,
                        modifier = Modifier.size(XNoteButtonSize),
                        onClick = onChecklist,
                    )
                    EditorSymbolButton(
                        description = stringResource(R.string.rich_text_action_quote),
                        iconRes = R.drawable.ic_keyline_stroke_quote,
                        selected = quoteSelected,
                        enabled = quoteEnabled,
                        modifier = Modifier.size(XNoteButtonSize),
                        onClick = onQuote,
                    )
                }
            }
            EditorSymbolButton(
                description = stringResource(
                    if (expanded) R.string.editor_collapse_tools else R.string.editor_expand_tools,
                ),
                iconRes = if (expanded) {
                    R.drawable.ic_keyline_stroke_chevrons_right
                } else {
                    R.drawable.ic_keyline_stroke_chevrons_left
                },
                modifier = Modifier.size(XNoteButtonSize),
                onClick = onToggleExpanded,
            )
        }
    }
}

@Composable
private fun TableToolbarCapsule(session: NoteEditorSession, backdrop: Backdrop) {
    val actions = listOf(
        Triple(R.drawable.ic_keyline_stroke_square_arrow_up, R.string.editor_table_insert_row_above, { session.insertTableRow(false) }),
        Triple(R.drawable.ic_keyline_stroke_square_arrow_down, R.string.editor_table_insert_row_below, { session.insertTableRow(true) }),
        Triple(R.drawable.ic_keyline_stroke_square_arrow_left, R.string.editor_table_insert_column_left, { session.insertTableColumn(false) }),
        Triple(R.drawable.ic_keyline_stroke_square_arrow_right, R.string.editor_table_insert_column_right, { session.insertTableColumn(true) }),
        Triple(R.drawable.ic_keyline_stroke_list_minus, R.string.editor_table_delete_row, { session.deleteTableRow() }),
        Triple(R.drawable.ic_keyline_stroke_square_minus, R.string.editor_table_delete_column, { session.deleteTableColumn() }),
        Triple(R.drawable.ic_keyline_stroke_grid_squares_x, R.string.editor_table_delete, { session.deleteTable() }),
    )
    LiquidButton(backdrop = backdrop) {
        Row(
            modifier = Modifier.padding(horizontal = XNoteSpacingExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEachIndexed { index, (iconRes, label, callback) ->
                EditorSymbolButton(
                    description = stringResource(label),
                    iconRes = iconRes,
                    modifier = Modifier.size(XNoteButtonSize),
                    destructive = index >= 4,
                    onClick = callback,
                )
            }
        }
    }
}
