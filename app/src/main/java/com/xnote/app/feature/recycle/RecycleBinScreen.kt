package com.xnote.app.feature.recycle

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.XNoteEmptyState
import com.xnote.app.design.XNoteMinimumTouchTarget
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.Notebook
import com.xnote.app.feature.notes.displayTitle
import com.xnote.app.feature.notes.formatNoteTimestamp

// -- Composables

@Composable
fun RecycleBinScreen(
    notes: List<Note>,
    notebooks: List<Notebook>,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onToggleSelection: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onRestore: (String) -> Unit,
    onPermanentlyDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val untitledLabel = stringResource(R.string.notes_untitled)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
    ) {
        if (notes.isEmpty()) {
            item {
                XNoteEmptyState(
                    title = stringResource(R.string.recycle_bin_empty_title),
                    description = stringResource(R.string.recycle_bin_empty_description),
                    iconRes = R.drawable.ic_lucide_trash_2,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(notes, key = Note::id) { note ->
                RecycleBinRow(
                    note = note,
                    originalNotebookName = note.originalNotebookName
                        ?: notebooks.firstOrNull { it.id == note.notebookId }?.name,
                    untitledLabel = untitledLabel,
                    selected = note.id in selectedIds,
                    selectionMode = selectionMode,
                    backdrop = backdrop,
                    onClick = {
                        if (selectionMode) {
                            onToggleSelection(note.id)
                        } else {
                            onEnterSelection(note.id)
                        }
                    },
                    onLongClick = { onEnterSelection(note.id) },
                    onRestore = { onRestore(note.id) },
                    onPermanentlyDelete = { onPermanentlyDelete(note.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecycleBinRow(
    note: Note,
    originalNotebookName: String?,
    untitledLabel: String,
    selected: Boolean,
    selectionMode: Boolean,
    backdrop: Backdrop,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    onPermanentlyDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deletedAt = note.deletedAtEpochMs ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .testTag("xnote-recycle-row")
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Icon(
                    painter = painterResource(
                        if (selected) R.drawable.ic_lucide_square_check else R.drawable.ic_lucide_square,
                    ),
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = note.displayTitle(untitledLabel),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(
                R.string.recycle_bin_deleted_metadata,
                formatNoteTimestamp(deletedAt),
                note.remainingRetentionDays(System.currentTimeMillis()) ?: 0,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!originalNotebookName.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.recycle_bin_original_notebook, originalNotebookName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!selectionMode) {
            Row(
                modifier = Modifier.padding(top = XNoteSpacingSmall),
                horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
            ) {
                LiquidButton(
                    onClick = onRestore,
                    backdrop = backdrop,
                    height = XNoteMinimumTouchTarget,
                    contentPadding = PaddingValues(horizontal = XNoteSpacingMedium),
                ) {
                    Text(
                        text = stringResource(R.string.recycle_bin_restore),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LiquidButton(
                    onClick = onPermanentlyDelete,
                    backdrop = backdrop,
                    height = XNoteMinimumTouchTarget,
                    contentPadding = PaddingValues(horizontal = XNoteSpacingMedium),
                ) {
                    Text(
                        text = stringResource(R.string.recycle_bin_delete_permanently),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
