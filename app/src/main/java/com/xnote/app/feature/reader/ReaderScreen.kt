package com.xnote.app.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.R
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.document.attachmentIds
import com.xnote.app.domain.model.*
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
        val top = insets.calculateTopPadding() + XNoteHeaderHeight + XNoteSpacingMedium
        val bottom = if (controlsHeight > 0) with(density) { controlsHeight.toDp() } + XNoteSpacingMedium
            else insets.calculateBottomPadding() + 96.dp
        val horizontal = if (tablet) 24.dp else XNoteSpacingMedium
        XNotePageScaffold(
            backdrop = backdrop,
            scrollEdges = edges,
            alwaysVisibleScrollEdges = edges,
            bottomOverlayHeight = 96.dp,
            pageBackground = { XNoteNoteSurface(background, Modifier.fillMaxSize()) },
            content = {
                Box(Modifier.fillMaxSize().padding(top = top, bottom = bottom, start = horizontal, end = horizontal),
                    contentAlignment = Alignment.TopCenter) {
                    BoxWithConstraints(Modifier.widthIn(max = XNoteMaximumContentWidth).fillMaxSize()) {
                        val width = constraints.maxWidth.coerceAtLeast(1)
                        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                        val measuredPages = remember(notes, attachments, width, height, density, typography, colors, measurer, untitled) {
                            if (attachments == null) emptyList() else paginateReadingUnits(
                                measureReadingUnits(notes, attachments.orEmpty(), measurer, typography, colors,
                                    width, height, density.density, untitled), height)
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
                        XNoteHeaderAction(R.drawable.ic_keyline_stroke_more_horizontal,
                            stringResource(R.string.reader_contents), { contentsVisible = true }, enabled = notes.isNotEmpty()),
                        XNoteHeaderAction(R.drawable.ic_keyline_stroke_square_pen,
                            stringResource(R.string.reader_edit), { currentNote?.let { onEdit(it.id) } }, enabled = currentNote != null),
                    ),
                    horizontalPadding = horizontal,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                Column(Modifier.align(Alignment.BottomCenter).onSizeChanged { controlsHeight = it.height }.navigationBarsPadding().padding(horizontal = horizontal, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.reader_progress, if (pages.isEmpty()) 0 else pageIndex + 1, pages.size,
                        if (pages.isEmpty()) 0 else (pageIndex + 1) * 100 / pages.size),
                        style = typography.labelMedium, color = colors.onSurface, modifier = Modifier.testTag("xnote-reader-progress"))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LiquidButton(onClick = { goTo(pageIndex - 1) }, backdrop = backdrop, enabled = pageIndex > 0,
                            modifier = Modifier.testTag("xnote-reader-previous")) { Text(stringResource(R.string.reader_previous), color = colors.onSurface) }
                        LiquidButton(onClick = { goTo(pageIndex + 1) }, backdrop = backdrop, enabled = pageIndex < pages.lastIndex,
                            modifier = Modifier.testTag("xnote-reader-next")) { Text(stringResource(R.string.reader_next), color = colors.onSurface) }
                    }
                }
                XNoteDrawer(
                    visible = contentsVisible,
                    onDismissRequest = { contentsVisible = false },
                    title = stringResource(R.string.reader_contents),
                    backdrop = backdrop,
                    placement = if (tablet) XNoteDrawerPlacement.End else XNoteDrawerPlacement.Bottom,
                ) {
                    notes.forEach { note ->
                        val firstPage = pages.indexOfFirst { it.noteId == note.id }
                        Row(Modifier.fillMaxWidth().clickable(enabled = firstPage >= 0) {
                            goTo(firstPage)
                            contentsVisible = false
                        }.padding(vertical = 12.dp).testTag("xnote-reader-toc-${note.id}"),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(note.title.ifBlank { untitled }, color = colors.onSurface, modifier = Modifier.weight(1f))
                            Text(if (firstPage < 0) "…" else "${firstPage + 1}", color = colors.onSurface)
                        }
                    }
                }
            },
        )
    }
}
