package com.xnote.app.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.*
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
    trashCount: Int,
    onOpenRecycleBin: () -> Unit,
    onOpenNotebook: (String) -> Unit,
    onOpenCollection: (NoteCollection) -> Unit,
    onCreateNotebook: () -> Unit,
    modifier: Modifier = Modifier,
    ui: NotebookHomeUiState,
    selectedNotebookId: String? = null,
) {
    // -- State
    val haptics = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val fontScale = LocalDensity.current.fontScale

    // -- Derived Values
    val groupedNotes = remember(notes) { notes.filterNot { it.isTrashed }.groupBy { it.notebookId } }
    val latestNotes = remember(groupedNotes) { groupedNotes.mapValues { (_, entries) -> entries.maxByOrNull { it.updatedAtEpochMs } } }

    // -- Functions
    BoxWithConstraints(modifier.fillMaxSize()) {
        val availableWidth = maxWidth - contentPadding.calculateLeftPadding(layoutDirection) -
            contentPadding.calculateRightPadding(layoutDirection)
        val columns = ((availableWidth + 16.dp) / (156.dp * fontScale + 16.dp)).toInt().coerceIn(1, 4)
        val rows = remember(notebooks, columns) { notebooks.chunked(columns) }
        LazyColumn(
            state = listState, contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().testTag("xnote-notebook-grid"),
        ) {
            item(key = "title") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.notes_notebooks_title),
                        Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    LiquidButton(
                        onClick = { ui.managing = true },
                        backdrop = backdrop,
                        modifier = Modifier.testTag("xnote-manage-notebooks"),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_keyline_stroke_more_horizontal),
                            "管理笔记本",
                            Modifier.size(24.dp),
                        )
                    }
                }
            }
            item(key = "collections") {
                val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val unfiledColor = if (dark) Color(0xFF87B8FF) else Color(0xFF007AFF)
                val allNotesColor = MaterialTheme.colorScheme.primary
                val trashColor = if (dark) Color(0xFFC7C7CC) else Color(0xFF8E8E93)

                Column(
                    modifier = Modifier
                        .clip(XNoteSmoothCornerShape(XNoteCardRadius))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), XNoteSmoothCornerShape(XNoteCardRadius))
                        .testTag("xnote-system-collections"),
                ) {
                    CollectionShortcut(
                        title = "全部笔记",
                        count = groupedNotes.values.sumOf { it.size },
                        iconRes = R.drawable.ic_keyline_fill_file_text,
                        onClick = { onOpenCollection(NoteCollection.All) },
                        modifier = Modifier.testTag("xnote-collection-all"),
                        iconTint = allNotesColor,
                        badgeBackground = allNotesColor.copy(alpha = 0.12f),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 66.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                    CollectionShortcut(
                        title = "未分类",
                        count = groupedNotes[null]?.size ?: 0,
                        iconRes = R.drawable.ic_keyline_stroke_inbox,
                        onClick = { onOpenCollection(NoteCollection.Unfiled) },
                        modifier = Modifier.testTag("xnote-collection-unfiled"),
                        subtitle = "灵感收集箱",
                        iconTint = unfiledColor,
                        badgeBackground = unfiledColor.copy(alpha = 0.12f),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 66.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                    CollectionShortcut(
                        title = "最近删除",
                        count = trashCount,
                        iconRes = R.drawable.ic_keyline_stroke_bin,
                        onClick = onOpenRecycleBin,
                        modifier = Modifier.testTag("xnote-collection-trash"),
                        iconTint = trashColor,
                        badgeBackground = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            item(key = "notebooks-heading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.notes_my_notebooks),
                        Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    LiquidButton(
                        onClick = onCreateNotebook,
                        backdrop = backdrop,
                        modifier = Modifier.testTag("xnote-create-notebook"),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_keyline_stroke_plus),
                            stringResource(R.string.notes_create_notebook),
                            Modifier.size(24.dp),
                        )
                    }
                }
            }
            if (notebooks.isEmpty()) item(key = "empty-notebooks") {
                XNoteEmptyState(
                    title = "暂无笔记本",
                    description = stringResource(R.string.notes_notebooks_empty_description),
                    backdrop = backdrop,
                    iconRes = R.drawable.ic_keyline_stroke_square_pen,
                    actionLabel = stringResource(R.string.notes_create_notebook),
                    actionIconRes = R.drawable.ic_keyline_stroke_plus,
                    onAction = onCreateNotebook,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(XNoteSmoothCornerShape(XNoteCardRadius))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), XNoteSmoothCornerShape(XNoteCardRadius))
                        .testTag("xnote-empty-notebooks"),
                )
            }
            items(rows, key = { it.first().id }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    row.forEach { notebook ->
                        val anchor = rememberXNotePopupAnchor()
                        NotebookTile(
                            notebook = notebook,
                            isSelected = selectedNotebookId == notebook.id,
                            count = groupedNotes[notebook.id]?.size ?: 0,
                            latest = latestNotes[notebook.id],
                            onClick = { onOpenNotebook(notebook.id) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                ui.selectedId = notebook.id
                                ui.menuAnchor = anchor
                                ui.action = "menu"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .xNotePopupAnchor(anchor)
                                .testTag("xnote-notebook-${notebook.id}"),
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
private fun CollectionShortcut(
    title: String,
    count: Int,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    badgeBackground: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(badgeBackground, XNoteSmoothCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconTint,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            count.toString(),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f), XNoteSmoothCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            painterResource(R.drawable.ic_keyline_stroke_chevron_right),
            null,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun NotebookTile(
    notebook: Notebook,
    isSelected: Boolean,
    count: Int,
    latest: Note?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = notebookColor(notebook.color)
    val tileBackground = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val tileBorderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }
    val previewContainerColor = if (isSelected) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
    }

    Column(
        modifier = modifier
            .semantics { selected = isSelected }
            .clip(XNoteSmoothCornerShape(XNoteCardRadius))
            .background(tileBackground)
            .border(1.dp, tileBorderColor, XNoteSmoothCornerShape(XNoteCardRadius))
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "管理此笔记本",
                hapticFeedbackEnabled = false,
            )
            .heightIn(min = 180.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = 0.12f), XNoteSmoothCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(notebookIcon(notebook.icon)),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = accent,
                )
            }
        }
        Text(
            notebook.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.notes_count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(XNoteSmoothCornerShape(XNoteRadiusSmall))
                .background(previewContainerColor)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                if (latest == null) "还没有笔记，记录第一个想法" else "最新：" + latest.title.ifBlank { latest.summary }.ifBlank { "未命名笔记" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("xnote-notebook-preview-${notebook.id}"),
            )
        }
    }
}

@Composable
internal fun NotebookMenuAction(label: String, icon: Int, destructive: Boolean = false, onClick: () -> Unit) {
    val foreground = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 48.dp).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = foreground)
        Text(label, color = foreground, style = MaterialTheme.typography.bodyLarge)
    }
}
