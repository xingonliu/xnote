package com.xnote.app.feature.notes.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
import com.xnote.app.design.xNoteScrollEdgePadding
import com.xnote.app.design.XNoteErrorState
import com.xnote.app.design.XNoteIconSizeMedium
import com.xnote.app.design.XNoteMaximumContentWidth
import com.xnote.app.design.XNoteMinimumTouchTarget
import com.xnote.app.design.XNoteRadiusSmall
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.domain.document.*
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import com.xnote.app.data.files.decodeNoteImage
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import com.xnote.app.design.XNoteButton
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
import com.xnote.app.feature.notes.formatNoteEditorDate
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

    val layoutDirection = LocalLayoutDirection.current
    Column(
        // Keep toolbar clearance inside the scrollable content so its viewport reaches the edge fade.
        modifier = modifier.fillMaxSize().imePadding()
            .verticalScroll(scrollState).navigationBarsPadding().padding(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = maxOf(contentPadding.calculateBottomPadding(), xNoteScrollEdgePadding(session.toolbarHeightDp.dp)),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NotePaper(session)
    }
}

@Composable
private fun NotePaper(session: NoteEditorSession) {
    val imageOrigins = remember(session.noteId) { mutableStateMapOf<String, Float>() }
    val imageBottoms = remember(session.noteId) { mutableStateMapOf<String, Float>() }
    val density = LocalDensity.current.density
    val blocks = session.document.visibleBlocks()
    val labels = session.document.numberedLabels()
    val firstTextId = blocks.filterIsInstance<TextBlock>().firstOrNull()?.id
    val images = blocks.filterIsInstance<PlacedMediaBlock>()
    val paperBottom = images.maxOfOrNull { imageBottoms[it.id] ?: 0f } ?: 0f
    Box(Modifier.widthIn(max = XNoteMaximumContentWidth).fillMaxWidth()
        .heightIn(min = maxOf(600f, paperBottom).dp)) {
        Box(Modifier.matchParentSize().clickable {
            val tail = session.document.blocks.lastOrNull()
            if (tail is TextBlock) {
                val end = tail.inlines.plainText().length
                session.select(EditorSelection(tail.id, end, end))
                session.focusBlockId = tail.id
            } else if (tail != null) session.continueAfterBlock(tail.id)
        })
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall)) {
            TitleField(
                value = session.title,
                onValueChange = session::updateTitle,
                onFocused = { session.focusBlockId = null; session.select(EditorSelection("")) },
            )
            session.note?.let { note ->
                Text(
                    text = formatNoteEditorDate(note.updatedAtEpochMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().testTag("xnote-editor-date"),
                )
            }
            blocks.flowGroups().forEach { group ->
                if (group.wraps) {
                    EditorFlowGroup(group, session, labels, firstTextId)
                } else group.blocks.forEach { block ->
                    key(block.id) {
                        if (block is PlacedMediaBlock) {
                            NoteImageBlock(
                                block = block,
                                session = session,
                                originY = (imageOrigins[block.id] ?: 0f) / density,
                                modifier = Modifier.fillMaxWidth().onPlaced {
                                    imageOrigins[block.id] = it.positionInParent().y
                                },
                                onBottomChanged = { imageBottoms[block.id] = it },
                            )
                        } else {
                            EditorBlock(block, session, labels[block.id], block.id == firstTextId)
                        }
                        val nextBlock = session.document.blocks.getOrNull(session.document.blocks.indexOf(block) + 1)
                        if (block !is TextBlock && nextBlock !is TextBlock) {
                            Box(Modifier.fillMaxWidth().height(XNoteMinimumTouchTarget)
                                .testTag(when (block) {
                                    is TableBlock -> "xnote-editor-continue-after-table"
                                    is ImageBlock -> "xnote-editor-continue-after-image"
                                    else -> "xnote-editor-continue-after-${block.id}"
                                }).clickable { session.continueAfterBlock(block.id) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TitleField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocused: () -> Unit,
) {
    val style = MaterialTheme.typography.headlineLarge
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onBackground),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("xnote-editor-title")
            .onFocusChanged { if (it.isFocused) onFocused() },
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
        is PlacedMediaBlock -> Unit
        is DrawingBlock -> DrawingBlockPreview(block, session)
    }
}

@Composable
private fun TextBlockEditor(
    block: TextBlock,
    session: NoteEditorSession,
    numberedLabel: Int?,
    isFirstTextBlock: Boolean,
    nativeFlow: Boolean = false,
    exclusionWidth: Float = 0f,
    exclusionHeight: Float = 0f,
) {
    val alignment = block.alignment.toTextAlign()
    val style = block.paragraphStyle.toTextStyle()
    val heading = block.paragraphStyle == ParagraphStyle.Heading ||
        block.paragraphStyle == ParagraphStyle.Subheading
    val firstLineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    val markerHeight = firstLineHeight.coerceAtLeast(XNoteMinimumTouchTarget)
    val fieldTopPadding = if (heading || block.listMarker == ListMarker.Checklist) {
        (markerHeight - firstLineHeight) / 2
    } else 0.dp
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
            Box(
                modifier = Modifier
                    .size(width = XNoteMinimumTouchTarget, height = markerHeight)
                    .clickable {
                        session.select(EditorSelection(block.id))
                        session.applyAction(com.xnote.app.design.XNoteRichTextAction.ToggleHeadingCollapse)
                    }
                    .testTag("xnote-editor-heading-collapse"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (block.collapsed) {
                            R.drawable.ic_keyline_stroke_chevron_right
                        } else {
                            R.drawable.ic_keyline_stroke_chevron_down
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
                    modifier = Modifier.size(XNoteIconSizeMedium),
                )
            }
        }
        when (block.listMarker) {
            ListMarker.None -> Unit
            ListMarker.Bullet -> MarkerText("•", style)
            ListMarker.Dash -> MarkerText("–", style)
            ListMarker.Numbered -> MarkerText("${numberedLabel ?: 1}.", style)
            ListMarker.Checklist -> {
                Box(
                    modifier = Modifier
                        .size(width = XNoteMinimumTouchTarget, height = markerHeight)
                        .clickable {
                            session.select(EditorSelection(block.id))
                            session.toggleChecked()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (block.checked) {
                                R.drawable.ic_keyline_stroke_square_check
                            } else {
                                R.drawable.ic_keyline_stroke_square
                            },
                        ),
                        contentDescription = stringResource(R.string.editor_checklist),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(XNoteIconSizeMedium),
                    )
                }
            }
        }
        RichTextField(
            inlines = block.inlines,
            nativeFlow = nativeFlow,
            exclusionWidth = (exclusionWidth - with(LocalDensity.current) { (block.indent * 20 + if (block.quoted) 16 else 0).dp.toPx() }).coerceAtLeast(0f),
            exclusionHeight = exclusionHeight,
            fieldsEpoch = session.fieldsEpoch,
            textStyle = style,
            textAlign = alignment,
            focused = session.focusBlockId == block.id,
            onFocused = {
                if (session.selection.blockId != block.id || session.selection.isTable) {
                    session.select(EditorSelection(blockId = block.id))
                }
                session.focusBlockId = block.id
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
            selection = if (session.selection.blockId == block.id && !session.selection.isTable) {
                TextRange(session.selection.start, session.selection.end)
            } else {
                null
            },
            fieldTestTag = if (isFirstTextBlock) "xnote-editor-body" else "xnote-editor-text-${block.id}",
            modifier = Modifier.weight(1f).alignByBaseline().padding(top = fieldTopPadding),
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
                            selection = if (cellSelected) {
                                TextRange(session.selection.start, session.selection.end)
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.MarkerText(label: String, style: TextStyle) {
    Text(
        text = label,
        style = style.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .widthIn(min = 28.dp)
            .alignByBaseline(),
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

@Composable
private fun DrawingBlockPreview(block: DrawingBlock, session: NoteEditorSession) {
    var image by remember(block.attachmentId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(block.attachmentId) {
        val file = session.imageFile(block.attachmentId)
        if (file != null) try { image = decodeNoteImage(file).asImageBitmap() }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { image = null }
    }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(block.width / block.height).clickable { session.drawingBlockId = block.id }
            .testTag("xnote-drawing-${block.id}")) {
            image?.let { Image(it, "画笔内容，点击编辑", Modifier.fillMaxSize()) } ?: Text("画笔内容无法读取，点击重新编辑")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XNoteButton({ session.drawingBlockId = block.id }) { Text("编辑画板") }
            XNoteButton({ session.removeMedia(block.id) }) { Text("删除画笔块") }
        }
    }
}

@Composable
private fun EditorFlowGroup(group: NoteFlowGroup, session: NoteEditorSession, labels: Map<String, Int>, firstTextId: String?) {
    val density = LocalDensity.current.density
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val gap = 8.dp.toPx()
        var y = 0f
        var bottom = 0f
        val exclusions = mutableListOf<Pair<Float, Float>>()
        val positions = mutableListOf<Triple<androidx.compose.ui.layout.Placeable, Int, Float>>()
        for (block in group.blocks) {
            if (block is PlacedMediaBlock) {
                val geometry = block.geometry(width / density, session.mediaRatios[block.attachmentId] ?: 0.65f)
                val placeable = subcompose(block.id) {
                    NoteImageBlock(block, session, 0f, onBottomChanged = {})
                }.single().measure(Constraints(maxWidth = width))
                positions += Triple(placeable, y.roundToInt(), 1f + block.zIndex)
                bottom = maxOf(bottom, y + geometry.bottom * density)
                if (block.layout == MediaLayout.Wrap) exclusions += (geometry.right * density + gap) to (y + geometry.bottom * density + gap)
            } else if (block is TextBlock) {
                val active = exclusions.filter { it.second > y }
                val right = active.maxOfOrNull { it.first } ?: 0f
                if (width - right < 80.dp.toPx()) y = maxOf(y, active.maxOfOrNull { it.second } ?: y)
                val remaining = exclusions.filter { it.second > y }
                val exclusionWidth = remaining.maxOfOrNull { it.first } ?: 0f
                val exclusionHeight = (remaining.maxOfOrNull { it.second } ?: y) - y
                val placeable = subcompose(block.id) {
                    TextBlockEditor(block, session, labels[block.id], block.id == firstTextId,
                        nativeFlow = true, exclusionWidth = exclusionWidth, exclusionHeight = exclusionHeight)
                }.single().measure(Constraints(maxWidth = width))
                positions += Triple(placeable, y.roundToInt(), 0f)
                y += placeable.height + gap
            }
        }
        layout(width, kotlin.math.ceil(maxOf(y, bottom + gap)).toInt()) {
            positions.forEach { (placeable, top, z) -> placeable.place(0, top, zIndex = z) }
        }
    }
}
