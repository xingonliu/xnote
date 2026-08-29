package com.xnote.app.feature.recycle

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.XNoteEmptyState
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteInsetDivider
import com.xnote.app.design.XNoteMinimumTouchTarget
import com.xnote.app.design.XNoteRadiusSmall
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.Notebook
import com.xnote.app.feature.notes.displayTitle
import com.xnote.app.feature.notes.formatNoteTimestamp

// -- Functions

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
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
    ) {
        if (notes.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(XNoteSmoothCornerShape(XNoteRadiusSmall))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = XNoteSpacingMedium, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.recycle_bin_empty_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

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
            item {
                XNoteGroupCard(modifier = Modifier.fillMaxWidth()) {
                    notes.forEachIndexed { index, note ->
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
                        if (index < notes.lastIndex) {
                            XNoteInsetDivider(startIndent = 16.dp)
                        }
                    }
                }
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
    val remainingDays = note.remainingRetentionDays(System.currentTimeMillis()) ?: 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .testTag("xnote-recycle-row")
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = XNoteSpacingMedium, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = note.displayTitle(untitledLabel),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(XNoteSmoothCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "剩余 $remainingDays 天",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "删除于 ${formatNoteTimestamp(deletedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!originalNotebookName.isNullOrBlank()) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = originalNotebookName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!selectionMode) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
            ) {
                LiquidButton(
                    onClick = onRestore,
                    backdrop = backdrop,
                    height = 32.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.recycle_bin_restore),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LiquidButton(
                    onClick = onPermanentlyDelete,
                    backdrop = backdrop,
                    height = 32.dp,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.recycle_bin_delete_permanently),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
