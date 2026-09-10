package com.xnote.app.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.R
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.*
import com.xnote.app.feature.background.XNoteNoteSurface
import com.xnote.app.feature.notes.editor.NoteEditorScreen
import com.xnote.app.feature.notes.editor.NoteEditorSession
import com.xnote.app.feature.search.SearchScreen
import com.xnote.app.navigation.*

// -- Functions

@Composable
fun TabletNotesWorkspace(
    navigation: XNoteNavigationState,
    library: NoteLibrary,
    notebooks: List<Notebook>,
    notes: List<Note>,
    ui: NotesUiState,
    editorSession: NoteEditorSession?,
    editorScroll: ScrollState,
    listState: LazyListState,
    searchListState: LazyListState,
    background: BackgroundKey,
    query: String,
    searchNotebookId: String?,
    results: List<NoteSearchResult>,
    recentQueries: List<String>,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSearchNotebook: (String?) -> Unit,
    onClearHistory: () -> Unit,
    onSearchVisible: (Boolean) -> Unit,
    onOpenNotebook: (String) -> Unit,
    onOpenCollection: (NoteCollection) -> Unit,
    onOpenNote: (String) -> Unit,
    onCreateNote: (String?) -> Unit,
    onBack: () -> Unit,
    onReadNotebook: (String) -> Unit,
    onReadNote: () -> Unit,
    onExport: () -> Unit,
    navigationRail: @Composable BoxScope.(Backdrop) -> Unit,
) {
    val outerBackdrop = rememberLayerBackdrop()
    val listBackdrop = rememberLayerBackdrop()
    val editorBackdrop = rememberLayerBackdrop()
    val toast = rememberXNoteToastHostState()
    val listAnchor = rememberXNotePopupAnchor()
    val editorAnchor = rememberXNotePopupAnchor()
    val editorUi = remember { NotesUiState() }
    val editor = navigation.notesRoute as? NotesRoute.Editor
    val parentRoute = navigation.notesStack.lastOrNull { it is NotesRoute.Notebook || it is NotesRoute.Collection }
        ?: NotesRoute.Collection(NoteCollection.All)
    val notebookId = (parentRoute as? NotesRoute.Notebook)?.notebookId
    val scope = if (notebookId != null) NotesScope.Notebook(notebookId)
        else if ((parentRoute as? NotesRoute.Collection)?.collection == NoteCollection.Unfiled) NotesScope.Unfiled else NotesScope.All
    var selectionHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val top = insets.calculateTopPadding() + XNoteHeaderHeight + 16.dp
    val edges = setOf(XNoteScrollEdge.Top, XNoteScrollEdge.Bottom)
    BackHandler(ui.selectedIds.isNotEmpty()) { ui.selectedIds = emptySet() }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("xnote-tablet-workspace")) {
        val full = maxWidth.value >= 1200 * maxOf(1f, density.fontScale)
        val showNotebooks = full || editor == null
        XNotePageScaffold(outerBackdrop, content = {
            Row(Modifier.fillMaxSize().padding(start = 104.dp)) {
                if (showNotebooks) {
                    LazyColumn(Modifier.width(220.dp).fillMaxHeight().testTag("xnote-tablet-notebooks"),
                        state = rememberLazyListState(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = 24.dp)) {
                        item { Text("笔记本", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp)) }
                        item { NotebookRow("全部笔记", notes.size, scope == NotesScope.All) { onOpenCollection(NoteCollection.All) } }
                        item { NotebookRow("未归档", notes.count { it.notebookId == null }, scope == NotesScope.Unfiled) { onOpenCollection(NoteCollection.Unfiled) } }
                        items(notebooks, key = { it.id }) { book ->
                            NotebookRow(book.name, notes.count { it.notebookId == book.id }, notebookId == book.id,
                                Modifier.testTag("xnote-notebook-${book.id}")) { onOpenNotebook(book.id) }
                        }
                        item {
                            LiquidButton({ ui.createNotebookVisible = true }, outerBackdrop, modifier = Modifier.padding(top = 16.dp)) { Text("新建笔记本") }
                        }
                    }
                    VerticalDivider()
                }
                val listModifier = if (full || editor != null) Modifier.width(320.dp) else Modifier.weight(1f)
                XNotePageScaffold(listBackdrop, modifier = listModifier.testTag("xnote-tablet-note-list"),
                    scrollEdges = edges, alwaysVisibleScrollEdges = edges,
                    scrollEdgeState = rememberXNoteScrollEdgeState(if (navigation.isSearchOpen) searchListState else listState),
                    toastHostState = toast,
                    content = {
                        val padding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 48.dp,
                            bottom = insets.calculateBottomPadding() + if (ui.selectedIds.isNotEmpty()) with(density) { selectionHeight.toDp() } + 16.dp else 88.dp)
                        if (navigation.isSearchOpen) {
                            SearchScreen(query, searchNotebookId, results, recentQueries, notebooks, listBackdrop, padding,
                                searchListState, onQueryChange, onSearch, onSearchNotebook, onOpenNote)
                        } else {
                            NoteCollectionScreen(library, scope, notebooks, listBackdrop, padding, listState,
                                if (notebookId != null) ui.notebookSort else ui.collectionSort, ui.selectedIds, onOpenNote,
                                { id -> ui.selectedIds = if (id in ui.selectedIds) ui.selectedIds - id else ui.selectedIds + id },
                                { ui.selectedIds = setOf(it) }, { ui.sortMenuVisible = true }, listAnchor, openedNoteId = editor?.noteId)
                        }
                    }, overlay = {
                        if (!navigation.isSearchOpen) {
                            NotesChrome(parentRoute, library, ui, notebooks, notebookStatsFrom(notes), listBackdrop, true, editorSession,
                                background, listAnchor, toast, onOpenNotebook, onCreateNote,
                                { onOpenCollection(NoteCollection.All) }, { notebookId?.let(onReadNotebook) }, {}, { selectionHeight = it },
                                showBack = parentRoute != NotesRoute.Collection(NoteCollection.All) || editor != null)
                        } else {
                            XNoteHeader("搜索", listBackdrop, onBack = { onSearchVisible(false) }, actions = listOf(
                                XNoteHeaderAction(R.drawable.ic_keyline_stroke_bin, "清空最近搜索", onClearHistory, enabled = recentQueries.isNotEmpty())))
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = top), horizontalArrangement = Arrangement.End) {
                            if (!navigation.isSearchOpen) LiquidButton({ onSearchVisible(true) }, listBackdrop) { Text("搜索") }
                        }
                    })
                if (full || editor != null) {
                    VerticalDivider()
                    XNotePageScaffold(editorBackdrop, modifier = Modifier.weight(1f).testTag("xnote-tablet-content"),
                        scrollEdges = edges, alwaysVisibleScrollEdges = edges,
                        scrollEdgeState = rememberXNoteScrollEdgeState(editorScroll),
                        pageBackground = { XNoteNoteSurface(background, Modifier.fillMaxSize()) }, content = {
                            if (editor != null && editorSession != null) {
                                NoteEditorScreen(editorSession, editorBackdrop,
                                    PaddingValues(start = 24.dp, end = 24.dp, top = top, bottom = XNoteEditorToolbarHeight + 16.dp), editorScroll)
                            } else {
                                Text("选择一篇笔记开始编辑", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                            }
                        }, overlay = {
                            if (editor != null) NotesChrome(editor, library, editorUi, notebooks, notebookStatsFrom(notes), editorBackdrop, true,
                                editorSession, background, editorAnchor, toast, onOpenNotebook, onCreateNote, onBack, onReadNote, onExport, {})
                        })
                }
            }
        }, overlay = { navigationRail(outerBackdrop) })
    }
}

@Composable
private fun NotebookRow(title: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        XNoteSmoothCornerShape(12.dp)).selectable(selected, onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
        Text("$count 篇笔记", style = MaterialTheme.typography.bodySmall)
    }
}
