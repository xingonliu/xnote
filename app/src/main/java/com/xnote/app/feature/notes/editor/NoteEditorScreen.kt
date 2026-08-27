package com.xnote.app.feature.notes.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import com.xnote.app.R
import com.xnote.app.design.XNoteErrorState
import com.xnote.app.design.XNoteMaximumContentWidth
import com.xnote.app.design.XNoteMinimumTouchTarget
import com.xnote.app.design.XNoteRadiusSmall
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.domain.document.DrawingBlock
import com.xnote.app.domain.document.EditorSelection
import com.xnote.app.domain.document.ImageBlock
import com.xnote.app.domain.document.ListMarker
import com.xnote.app.domain.document.NoteBlock
import com.xnote.app.domain.document.ParagraphStyle
import com.xnote.app.domain.document.StickerBlock
import com.xnote.app.domain.document.TableBlock
import com.xnote.app.domain.document.TextAlignment
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.document.numberedLabels
import com.xnote.app.domain.document.plainText
import com.kyant.backdrop.Backdrop

// -- Composables

@Composable
fun NoteEditorScreen(
    session: NoteEditorSession,
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(session.noteId) {
        session.load()
    }

    if (session.missing) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            XNoteErrorState(
                title = stringResource(R.string.editor_missing_title),
                description = stringResource(R.string.editor_missing_description),
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = XNoteMaximumContentWidth),
            )
        }
        return
    }

    val labels = session.document.numberedLabels()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = XNoteMaximumContentWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
        ) {
            TitleField(
                value = session.title,
                onValueChange = session::updateTitle,
                readOnly = false,
            )
            if (session.isMarkdown) {
                Text(
                    text = stringResource(R.string.editor_markdown_readonly),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = session.note?.markdownText.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                session.document.visibleBlocks().forEachIndexed { index, block ->
                    EditorBlock(
                        block = block,
                        session = session,
                        numberedLabel = labels[block.id],
                        isFirstTextBlock = index == 0 || session.document.visibleBlocks()
                            .take(index)
                            .none { it is TextBlock },
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TitleField(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
) {
    val style = MaterialTheme.typography.headlineLarge
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onBackground),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("xnote-editor-title"),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.editor_title_placeholder),
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun EditorBlock(
    block: NoteBlock,
    session: NoteEditorSession,
    numberedLabel: Int?,
    isFirstTextBlock: Boolean,
) {
    when (block) {
        is TextBlock -> TextBlockEditor(
            block = block,
            session = session,
            numberedLabel = numberedLabel,
            isFirstTextBlock = isFirstTextBlock,
        )
        is TableBlock -> TableBlockEditor(block = block, session = session)
        is ImageBlock, is StickerBlock, is DrawingBlock -> {
            Text(
                text = stringResource(R.string.editor_unsupported_block),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = XNoteSpacingSmall),
            )
        }
    }
}

@Composable
private fun TextBlockEditor(
    block: TextBlock,
    session: NoteEditorSession,
    numberedLabel: Int?,
    isFirstTextBlock: Boolean,
) {
    val alignment = block.alignment.toTextAlign()
    val style = block.paragraphStyle.toTextStyle()
    val heading = block.paragraphStyle == ParagraphStyle.Heading ||
        block.paragraphStyle == ParagraphStyle.Subheading
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (block.indent * 20).dp)
            .then(
                if (block.quoted) {
                    Modifier
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            shape = XNoteSmoothCornerShape(XNoteRadiusSmall),
                        )
                        .padding(start = XNoteSpacingMedium, top = 4.dp, bottom = 4.dp)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (heading) {
            Icon(
                painter = painterResource(
                    if (block.collapsed) {
                        R.drawable.ic_lucide_chevron_right
                    } else {
                        R.drawable.ic_lucide_chevron_down
                    },
                ),
                contentDescription = stringResource(
                    if (block.collapsed) {
                        R.string.editor_expand_heading
                    } else {
                        R.string.editor_collapse_heading
                    },
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(XNoteMinimumTouchTarget)
                    .clickable {
                        session.select(EditorSelection(block.id))
                        session.applyAction(com.xnote.app.design.XNoteRichTextAction.ToggleHeadingCollapse)
                    },
            )
        }
        when (block.listMarker) {
            ListMarker.None -> Unit
            ListMarker.Bullet -> MarkerText("•")
            ListMarker.Dash -> MarkerText("–")
            ListMarker.Numbered -> MarkerText("${numberedLabel ?: 1}.")
            ListMarker.Checklist -> {
                Icon(
                    painter = painterResource(
                        if (block.checked) {
                            R.drawable.ic_lucide_square_check
                        } else {
                            R.drawable.ic_lucide_square
                        },
                    ),
                    contentDescription = stringResource(R.string.editor_checklist),
                    tint = if (block.checked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(XNoteMinimumTouchTarget)
                        .clickable {
                            session.select(EditorSelection(block.id))
                            session.toggleChecked()
                        },
                )
            }
        }
        RichTextField(
            inlines = block.inlines,
            fieldsEpoch = session.fieldsEpoch,
            textStyle = style,
            textAlign = alignment,
            placeholder = stringResource(R.string.editor_body_placeholder),
            focused = session.focusBlockId == block.id,
            onFocused = {
                if (session.selection.blockId != block.id || session.selection.isTable) {
                    session.select(EditorSelection(blockId = block.id))
                }
            },
            onTextChange = { oldText, newText, range, composing ->
                session.onPlainTextChange(
                    EditorSelection(block.id, range.start, range.end),
                    oldText,
                    newText,
                    composing,
                )
            },
            onDeleteBackwardAtStart = {
                session.select(EditorSelection(block.id, 0, 0))
                session.deleteBackward()
            },
            fieldTestTag = if (isFirstTextBlock) "xnote-editor-body" else null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TableBlockEditor(
    block: TableBlock,
    session: NoteEditorSession,
) {
    val selected = session.selection.blockId == block.id
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(XNoteSmoothCornerShape(XNoteRadiusSmall))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                },
                shape = XNoteSmoothCornerShape(XNoteRadiusSmall),
            )
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        block.rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.cells.forEachIndexed { columnIndex, cell ->
                    val cellSelected = selected &&
                        session.selection.tableRow == rowIndex &&
                        session.selection.tableColumn == columnIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(XNoteSmoothCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                            .border(
                                width = if (cellSelected) 1.5.dp else 1.dp,
                                color = if (cellSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                },
                                shape = XNoteSmoothCornerShape(8.dp),
                            )
                            .padding(8.dp),
                    ) {
                        RichTextField(
                            inlines = cell.inlines,
                            fieldsEpoch = session.fieldsEpoch,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Start,
                            placeholder = stringResource(R.string.editor_cell_placeholder),
                            focused = cellSelected && session.focusBlockId == block.id,
                            onFocused = {
                                session.select(
                                    EditorSelection(
                                        blockId = block.id,
                                        tableRow = rowIndex,
                                        tableColumn = columnIndex,
                                    ),
                                )
                                session.focusBlockId = block.id
                            },
                            onTextChange = { oldText, newText, range, composing ->
                                session.onPlainTextChange(
                                    EditorSelection(
                                        blockId = block.id,
                                        start = range.start,
                                        end = range.end,
                                        tableRow = rowIndex,
                                        tableColumn = columnIndex,
                                    ),
                                    oldText,
                                    newText,
                                    composing,
                                )
                            },
                            onDeleteBackwardAtStart = {
                                session.select(
                                    EditorSelection(
                                        blockId = block.id,
                                        tableRow = rowIndex,
                                        tableColumn = columnIndex,
                                    ),
                                )
                                session.deleteBackward()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkerText(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .width(28.dp)
            .padding(top = 2.dp),
    )
}

@Composable
private fun ParagraphStyle.toTextStyle() = when (this) {
    ParagraphStyle.Body -> MaterialTheme.typography.bodyLarge
    ParagraphStyle.Heading -> MaterialTheme.typography.headlineSmall
    ParagraphStyle.Subheading -> MaterialTheme.typography.titleMedium
    ParagraphStyle.Monospace -> MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
}

private fun TextAlignment.toTextAlign(): TextAlign = when (this) {
    TextAlignment.Left -> TextAlign.Start
    TextAlignment.Center -> TextAlign.Center
    TextAlignment.Right -> TextAlign.End
}
