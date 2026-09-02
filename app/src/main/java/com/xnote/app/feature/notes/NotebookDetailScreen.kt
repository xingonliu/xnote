package com.xnote.app.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.xnote.app.domain.model.NotebookStats
import kotlinx.coroutines.launch

// -- Functions

@Composable
fun NotebookDetailScreen(
    library: NoteLibrary,
    notebook: Notebook?,
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    listState: LazyListState,
    sort: NoteListSort,
    selectedIds: Set<String>,
    onOpenNote: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onOpenSort: () -> Unit,
    sortMenuAnchor: XNotePopupAnchor,
    modifier: Modifier = Modifier,
) {
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(library, notebook?.id, sort, dragging) {
        val id = notebook?.id ?: return@LaunchedEffect
        if (dragging) return@LaunchedEffect
        library.observeNotesInNotebook(id, sort).collect { notes = it }
    }

    val untitled = stringResource(R.string.notes_untitled)
    val selectionMode = selectedIds.isNotEmpty()
    val stats = NotebookStats(
        noteCount = notes.size,
        characterCount = notes.sumOf { it.visibleCharacterCount },
    )
    val allowDrag = sort == NoteListSort.Manual && !selectionMode

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = XNoteSpacingSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.notes_notebook_stats,
                        stats.noteCount,
                        stats.characterCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

        if (notes.isEmpty()) {
            item {
                XNoteEmptyState(
                    title = stringResource(R.string.notes_empty_notebook_title),
                    description = stringResource(R.string.notes_empty_notebook_description),
                    iconRes = R.drawable.ic_keyline_stroke_square_pen,
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = XNoteMaximumContentWidth),
                )
            }
        } else {
            item {
                XNoteGroupCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = XNoteMaximumContentWidth),
                ) {
                    notes.forEachIndexed { index, note ->
                        NoteListRow(
                            note = note,
                            notebookName = null,
                            untitledLabel = untitled,
                            selected = note.id in selectedIds,
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) onToggleSelect(note.id) else onOpenNote(note.id)
                            },
                            onLongClick = { onEnterSelection(note.id) },
                            dragHandle = if (allowDrag) {
                                {
                                    NoteReorderHandle(
                                        modifier = Modifier.pointerInput(note.id, notes.map { it.id }) {
                                            detectVerticalDragGestures(
                                                onDragStart = { dragging = true },
                                                onVerticalDrag = { _, dy ->
                                                    dragOffset += dy
                                                    val step = 72f
                                                    if (dragOffset > step && index < notes.lastIndex) {
                                                        notes = notes.toMutableList().also {
                                                            val item = it.removeAt(index)
                                                            it.add(index + 1, item)
                                                        }
                                                        dragOffset = 0f
                                                    } else if (dragOffset < -step && index > 0) {
                                                        notes = notes.toMutableList().also {
                                                            val item = it.removeAt(index)
                                                            it.add(index - 1, item)
                                                        }
                                                        dragOffset = 0f
                                                    }
                                                },
                                                onDragEnd = {
                                                    dragging = false
                                                    dragOffset = 0f
                                                    scope.launch {
                                                        library.reorderNotes(notes.map { it.id })
                                                    }
                                                },
                                                onDragCancel = {
                                                    dragging = false
                                                    dragOffset = 0f
                                                },
                                            )
                                        },
                                    )
                                }
                            } else {
                                null
                            },
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
