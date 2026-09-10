package com.xnote.app.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.XNoteButtonContentSpacing
import com.xnote.app.design.XNoteCardRadius
import com.xnote.app.design.XNoteIconSizeMedium
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.Notebook
import com.xnote.app.navigation.NoteCollection

// -- Functions

@Composable
fun NotesHomeScreen(
    notes: List<Note>,
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    listState: LazyListState,
    notebooks: List<Notebook>,
    onOpenNotebook: (String) -> Unit,
    onOpenCollection: (NoteCollection) -> Unit,
    onCreateNotebook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts = remember(notes) { notes.groupingBy { it.notebookId }.eachCount() }
    val layoutDirection = LocalLayoutDirection.current
    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(modifier.fillMaxSize()) {
        val availableWidth = maxWidth - contentPadding.calculateLeftPadding(layoutDirection) -
            contentPadding.calculateRightPadding(layoutDirection)
        val columns = ((availableWidth + XNoteSpacingMedium) / (156.dp * fontScale + XNoteSpacingMedium))
            .toInt().coerceIn(1, 4)
        val rows = remember(notebooks, columns) { notebooks.chunked(columns) }
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
            modifier = Modifier.fillMaxSize().testTag("xnote-notebook-grid"),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.notes_notebooks_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item(key = "collections") {
                Column(verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall)) {
                    CollectionShortcut(
                        title = stringResource(R.string.notes_scope_all),
                        count = notes.size,
                        iconRes = R.drawable.ic_keyline_fill_file_text,
                        onClick = { onOpenCollection(NoteCollection.All) },
                        modifier = Modifier.testTag("xnote-collection-all"),
                    )
                    CollectionShortcut(
                        title = stringResource(R.string.notes_scope_unfiled),
                        count = counts[null] ?: 0,
                        iconRes = R.drawable.ic_keyline_stroke_inbox,
                        onClick = { onOpenCollection(NoteCollection.Unfiled) },
                        modifier = Modifier.testTag("xnote-collection-unfiled"),
                    )
                }
            }
            item(key = "notebooks-heading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = XNoteSpacingMedium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.notes_my_notebooks),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    LiquidButton(
                        onClick = onCreateNotebook,
                        backdrop = backdrop,
                        modifier = Modifier.testTag("xnote-create-notebook"),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_keyline_stroke_plus),
                            contentDescription = stringResource(R.string.notes_create_notebook),
                            modifier = Modifier.size(XNoteIconSizeMedium),
                        )
                    }
                }
            }
            if (notebooks.isEmpty()) {
                item(key = "empty-notebooks") {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, XNoteSmoothCornerShape(XNoteCardRadius))
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(XNoteSpacingMedium),
                    ) {
                        Text(
                            text = stringResource(R.string.notes_notebooks_empty_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(rows, key = { row -> row.first().id }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(XNoteSpacingMedium)) {
                    row.forEach { notebook ->
                        NotebookTile(
                            title = notebook.name,
                            count = counts[notebook.id] ?: 0,
                            onClick = { onOpenNotebook(notebook.id) },
                            modifier = Modifier.weight(1f).testTag("xnote-notebook-${notebook.id}"),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item(key = "bottom-space") { Spacer(Modifier.size(56.dp)) }
        }
    }
}

@Composable
private fun CollectionShortcut(title: String, count: Int, iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = XNoteSmoothCornerShape(XNoteCardRadius)
    Row(
        modifier = modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(XNoteSpacingMedium),
        horizontalArrangement = Arrangement.spacedBy(XNoteButtonContentSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), null, Modifier.size(XNoteIconSizeMedium), tint = MaterialTheme.colorScheme.onSurface)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(painterResource(R.drawable.ic_keyline_stroke_chevron_right), null, Modifier.size(XNoteIconSizeMedium), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun NotebookTile(title: String, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = XNoteSmoothCornerShape(XNoteCardRadius)
    Column(
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick).heightIn(min = 156.dp).padding(XNoteSpacingMedium),
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
    ) {
        Icon(
            painterResource(R.drawable.ic_keyline_stroke_square_pen), null,
            Modifier.padding(bottom = XNoteSpacingMedium).size(32.dp), tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.notes_count, count), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
