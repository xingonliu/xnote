package com.xnote.app.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.R
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.document.attachmentIds
import com.xnote.app.domain.model.*
import com.xnote.app.feature.background.rememberBackgroundImage
import com.xnote.app.feature.background.XNoteNoteSurface
import com.xnote.app.navigation.NotesRoute

// -- Functions

@Composable
fun ReaderScreen(
    route: NotesRoute.Reader,
    activeNotes: List<Note>,
    sort: NoteListSort,
    library: NoteLibrary,
    defaultBackground: BackgroundKey,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val notes = remember(activeNotes, route, sort) { readingNotes(activeNotes, route, sort) }
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val readingLayout = LocalReadingLayout.current
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val untitled = stringResource(R.string.notes_untitled)
    var anchorNote by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorBlock by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorOffset by rememberSaveable { mutableIntStateOf(0) }
    var controlsHeight by remember { mutableIntStateOf(0) }
    var contentsVisible by rememberSaveable { mutableStateOf(false) }
    val attachmentIds = remember(notes) { notes.flatMap { it.document.attachmentIds() }.toSet() }
    var attachments by remember(attachmentIds) { mutableStateOf<Map<String, Attachment>?>(null) }
    var pages by remember { mutableStateOf<List<ReadingPage<ReadingContent>>>(emptyList()) }
    val pageIndex = readingPageIndex(pages, anchorNote, anchorBlock, anchorOffset)
    val currentPage = pages.getOrNull(pageIndex)
    val currentNote = notes.firstOrNull { it.id == currentPage?.noteId } ?: notes.firstOrNull()
    val background = resolveBackgroundKey(currentNote?.backgroundKey, defaultBackground)
    val edges = setOf(XNoteScrollEdge.Top, XNoteScrollEdge.Bottom)

    fun goTo(index: Int) {
        val first = pages.getOrNull(index)?.units?.firstOrNull() ?: return
        anchorNote = first.noteId
        anchorBlock = first.blockId
        anchorOffset = first.offset
    }

    LaunchedEffect(attachmentIds, library) {
        attachments = attachmentIds.mapNotNull { id -> library.getAttachment(id)?.let { id to it } }.toMap()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tablet = maxWidth >= 600.dp
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = xNoteScrollEdgePadding(insets.calculateTopPadding() + XNoteHeaderHeight)
        val bottom = xNoteScrollEdgePadding(
            if (controlsHeight > 0) with(density) { controlsHeight.toDp() }
            else insets.calculateBottomPadding() + 84.dp
        )
        val horizontal = if (tablet) 24.dp else XNoteSpacingMedium

        XNotePageScaffold(
            backdrop = backdrop,
            scrollEdges = edges,
            alwaysVisibleScrollEdges = edges,
            pageBackground = { XNoteNoteSurface(background, Modifier.fillMaxSize(), rememberBackgroundImage(background, library)) },
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = top, bottom = bottom, start = horizontal, end = horizontal),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    BoxWithConstraints(Modifier.widthIn(max = readingLayout.widthDp.dp).fillMaxSize()) {
                        val width = constraints.maxWidth.coerceAtLeast(1)
                        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                        val measuredPages = remember(notes, attachments, width, height, density, typography, colors, measurer, untitled, readingLayout) {
                            if (attachments == null) emptyList() else paginateReadingUnits(
                                measureReadingUnits(
                                    notes,
                                    attachments.orEmpty(),
                                    measurer,
                                    typography,
                                    colors,
                                    width,
                                    height,
                                    density.density,
                                    untitled,
                                    readingLayout.lineHeightScale,
                                ),
                                height,
                            )
                        }
                        SideEffect { pages = measuredPages }
                        val displayedPage = measuredPages.getOrNull(readingPageIndex(measuredPages, anchorNote, anchorBlock, anchorOffset))
                        when {
                            notes.isEmpty() -> XNoteErrorState(
                                title = stringResource(R.string.reader_empty),
                                description = stringResource(R.string.reader_empty_description),
                                backdrop = backdrop,
                                modifier = Modifier.align(Alignment.Center),
                            )
                            displayedPage != null -> ReadingPageContent(displayedPage, library, Modifier.fillMaxWidth())
                            else -> XNoteLoadingState(backdrop = backdrop, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            },
            overlay = {
                XNoteHeader(
                    title = currentNote?.title?.ifBlank { untitled } ?: stringResource(R.string.reader_title),
                    backdrop = backdrop,
                    onBack = onBack,
                    actions = listOf(
                        XNoteHeaderAction(
                            R.drawable.ic_keyline_stroke_more_horizontal,
                            stringResource(R.string.reader_contents),
                            { contentsVisible = true },
                            enabled = notes.isNotEmpty(),
                        ),
                        XNoteHeaderAction(
                            R.drawable.ic_keyline_stroke_square_pen,
                            stringResource(R.string.reader_edit),
                            { currentNote?.let { onEdit(it.id) } },
                            enabled = currentNote != null,
                        ),
                    ),
                    horizontalPadding = horizontal,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                // 底部浮动式微光阅读控制条 (Floating Reading Dock)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { controlsHeight = it.height }
                        .navigationBarsPadding()
                        .padding(horizontal = horizontal, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = colors.surface.copy(alpha = 0.88f),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LiquidButton(
                                onClick = { goTo(pageIndex - 1) },
                                backdrop = backdrop,
                                enabled = pageIndex > 0,
                                modifier = Modifier.testTag("xnote-reader-previous"),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_keyline_stroke_chevron_left),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.reader_previous),
                                        style = typography.labelLarge,
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.reader_progress,
                                        if (pages.isEmpty()) 0 else pageIndex + 1,
                                        pages.size,
                                        if (pages.isEmpty()) 0 else (pageIndex + 1) * 100 / pages.size,
                                    ),
                                    style = typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.onSurface,
                                    modifier = Modifier.testTag("xnote-reader-progress"),
                                )
                            }

                            LiquidButton(
                                onClick = { goTo(pageIndex + 1) },
                                backdrop = backdrop,
                                enabled = pageIndex < pages.lastIndex,
                                modifier = Modifier.testTag("xnote-reader-next"),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.reader_next),
                                        style = typography.labelLarge,
                                    )
                                    Icon(
                                        painter = painterResource(R.drawable.ic_keyline_stroke_chevron_right),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // 目录抽屉
                XNoteDrawer(
                    visible = contentsVisible,
                    onDismissRequest = { contentsVisible = false },
                    title = stringResource(R.string.reader_contents),
                    backdrop = backdrop,
                    placement = if (tablet) XNoteDrawerPlacement.End else XNoteDrawerPlacement.Bottom,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        notes.forEach { note ->
                            val firstPage = pages.indexOfFirst { it.noteId == note.id }
                            val isCurrent = note.id == currentNote?.id

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = firstPage >= 0) {
                                        goTo(firstPage)
                                        contentsVisible = false
                                    }
                                    .testTag("xnote-reader-toc-${note.id}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) colors.primary.copy(alpha = 0.12f) else colors.surface.copy(alpha = 0.4f),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(colors.primary)
                                        )
                                    }
                                    Text(
                                        text = note.title.ifBlank { untitled },
                                        style = typography.bodyLarge,
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isCurrent) colors.primary else colors.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.surfaceVariant.copy(alpha = 0.6f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = if (firstPage < 0) "…" else "P.${firstPage + 1}",
                                            style = typography.labelSmall,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}
