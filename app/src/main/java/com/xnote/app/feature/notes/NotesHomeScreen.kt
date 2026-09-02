package com.xnote.app.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteEmptyState
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteIconSizeSmall
import com.xnote.app.design.XNoteInsetDivider
import com.xnote.app.design.XNoteMaximumContentWidth
import com.xnote.app.design.XNotePopupAnchor
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.xNotePopupAnchor
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteListSort
import com.xnote.app.domain.model.Notebook

// -- Functions

@Composable
fun NotesHomeScreen(
    library: NoteLibrary,
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    listState: LazyListState,
    scope: NotesScope,
    sort: NoteListSort,
    notebooks: List<Notebook>,
    selectedIds: Set<String>,
    onOpenNote: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onOpenSort: () -> Unit,
    sortMenuAnchor: XNotePopupAnchor,
    onCreateNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    LaunchedEffect(library, scope, sort) {
        when (scope) {
            NotesScope.All -> library.observeAllActiveNotes(sort)
            NotesScope.Unfiled -> library.observeUnfiledNotes(sort)
            is NotesScope.Notebook -> library.observeNotesInNotebook(scope.id, sort)
        }.collect { notes = it }
    }

    val untitled = stringResource(R.string.notes_untitled)
    val selectionMode = selectedIds.isNotEmpty()
    val recent = remember(notes) { notes.sortedByDescending { it.updatedAtEpochMs }.take(3) }
    val showRecent = notes.isNotEmpty() && sort == NoteListSort.UpdatedAt && notes.size > 3
    val scopeName = scopeTitle(scope, notebooks)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = XNoteSpacingSmall),
                verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
            ) {
                Text(
                    text = scopeName,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiquidButton(
                        onClick = onOpenPicker,
                        backdrop = backdrop,
                        modifier = Modifier.testTag("xnote-notebook-picker"),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_keyline_stroke_square_pen),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(XNoteIconSizeSmall),
                            )
                            Text(
                                text = stringResource(R.string.notes_choose_notebook),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_keyline_stroke_chevron_down),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(XNoteIconSizeSmall),
                            )
                        }
                    }
                    LiquidButton(
                        onClick = onOpenSort,
                        backdrop = backdrop,
                        modifier = Modifier.xNotePopupAnchor(sortMenuAnchor),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_keyline_stroke_grip_vertical),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(XNoteIconSizeSmall),
                            )
                            Text(
                                text = stringResource(R.string.notes_sort),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        if (notes.isEmpty()) {
            item {
                XNoteEmptyState(
                    title = stringResource(
                        when (scope) {
                            NotesScope.Unfiled -> R.string.notes_empty_unfiled_title
                            is NotesScope.Notebook -> R.string.notes_empty_notebook_title
                            NotesScope.All -> R.string.notes_empty_title
                        },
                    ),
                    description = stringResource(
                        when (scope) {
                            NotesScope.Unfiled -> R.string.notes_empty_unfiled_description
                            is NotesScope.Notebook -> R.string.notes_empty_notebook_description
                            NotesScope.All -> R.string.notes_empty_description
                        },
                    ),
                    iconRes = when (scope) {
                        NotesScope.Unfiled -> R.drawable.ic_keyline_stroke_inbox
                        else -> R.drawable.ic_keyline_stroke_square_pen
                    },
                    actionLabel = stringResource(R.string.action_create_note),
                    actionIconRes = R.drawable.ic_keyline_stroke_plus,
                    onAction = onCreateNote,
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = XNoteMaximumContentWidth),
                )
            }
        } else {
            if (showRecent) {
                item {
                    Text(
                        text = stringResource(R.string.notes_recent_section),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                item {
                    XNoteGroupCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = XNoteMaximumContentWidth),
                    ) {
                        recent.forEachIndexed { index, note ->
                            NoteListRow(
                                note = note,
                                notebookName = notebookName(notebooks, note.notebookId),
                                untitledLabel = untitled,
                                selected = note.id in selectedIds,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) onToggleSelect(note.id) else onOpenNote(note.id)
                                },
                                onLongClick = { onEnterSelection(note.id) },
                            )
                            if (index < recent.lastIndex) {
                                XNoteInsetDivider(startIndent = 16.dp)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.notes_list_section),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            item {
                XNoteGroupCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = XNoteMaximumContentWidth),
                ) {
                    notes.forEachIndexed { index, note ->
                        NoteListRow(
                            note = note,
                            notebookName = notebookName(notebooks, note.notebookId),
                            untitledLabel = untitled,
                            selected = note.id in selectedIds,
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) onToggleSelect(note.id) else onOpenNote(note.id)
                            },
                            onLongClick = { onEnterSelection(note.id) },
                        )
                        if (index < notes.lastIndex) {
                            XNoteInsetDivider(startIndent = 16.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun scopeTitle(scope: NotesScope, notebooks: List<Notebook>): String = when (scope) {
    NotesScope.All -> stringResource(R.string.notes_scope_all)
    NotesScope.Unfiled -> stringResource(R.string.notes_scope_unfiled)
    is NotesScope.Notebook -> notebooks.firstOrNull { it.id == scope.id }?.name
        ?: stringResource(R.string.notes_scope_all)
}
