package com.xnote.app.feature.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.design.XNoteMinimumTouchTarget
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.domain.model.Note

// -- Functions

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteListRow(
    note: Note,
    notebookName: String?,
    untitledLabel: String,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    val title = note.displayTitle(untitledLabel)
    val timestamp = formatNoteTimestamp(note.updatedAtEpochMs)
    val summaryText = if (note.summary.isNotBlank()) note.summary else stringResource(R.string.notes_untitled)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .testTag("xnote-note-row")
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = XNoteSpacingMedium, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (!notebookName.isNullOrBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_notebook_pen),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = notebookName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        dragHandle?.invoke()
    }
}

@Composable
fun NoteReorderHandle(
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(R.drawable.ic_lucide_grip_vertical),
        contentDescription = stringResource(R.string.notes_reorder_handle),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(XNoteMinimumTouchTarget),
    )
}
