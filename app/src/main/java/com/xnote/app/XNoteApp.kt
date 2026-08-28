package com.xnote.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.xnote.app.design.XNoteHeader
import com.xnote.app.design.XNoteHeaderAction
import com.xnote.app.design.XNoteBottomNavigationHeight
import com.xnote.app.design.XNoteHeaderHeight
import com.xnote.app.design.XNoteLiquidGlassPanel
import com.xnote.app.design.LocalXNoteInteractionSettings
import com.xnote.app.design.XNotePageScaffold
import com.xnote.app.design.XNoteScrollEdge
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.rememberXNoteScrollEdgeState
import com.xnote.app.design.rememberXNoteToastHostState
import com.xnote.app.design.liquidglass.LiquidBottomTab
import com.xnote.app.design.liquidglass.LiquidBottomTabs
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.data.background.NoteBackgroundResolution
import com.xnote.app.data.background.NoteBackgroundResolver
import com.xnote.app.data.background.ResolvedNoteBackground
import com.xnote.app.data.background.defaultResolvedBackground
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.data.search.EmptySearchHistoryRepository
import com.xnote.app.data.search.SearchHistoryRepository
import com.xnote.app.data.settings.AppSettingsRepository
import com.xnote.app.data.settings.InMemoryAppSettingsRepository
import com.xnote.app.domain.model.AppSettings
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.NoteListSort
import com.xnote.app.domain.model.NoteSearchResult
import com.xnote.app.domain.model.Notebook
import com.xnote.app.domain.model.defaultAppSettings
import com.xnote.app.feature.PlaceholderScreen
import com.xnote.app.feature.background.DefaultBackgroundScreen
import com.xnote.app.feature.notes.NotesChrome
import com.xnote.app.feature.notes.NotesHomeScreen
import com.xnote.app.feature.notes.NotesScope
import com.xnote.app.feature.notes.NotesUiState
import com.xnote.app.feature.notes.NotebookDetailScreen
import com.xnote.app.feature.notes.XNoteEditorToolbarHeight
import com.xnote.app.feature.notes.decodeNotesScope
import com.xnote.app.feature.notes.encodeNotesScope
import com.xnote.app.feature.notes.editor.MarkdownEditorMode
import com.xnote.app.feature.notes.editor.NoteEditorScreen
import com.xnote.app.feature.notes.editor.NoteEditorSession
import com.xnote.app.feature.notes.notebookStatsFrom
import com.xnote.app.feature.notes.unfiledStatsFrom
import com.xnote.app.feature.profile.ProfileScreen
import com.xnote.app.feature.recycle.RecycleBinChrome
import com.xnote.app.feature.recycle.RecycleBinScreen
import com.xnote.app.feature.recycle.RecycleBinUiState
import com.xnote.app.feature.recycle.XNoteRecycleSelectionHeight
import com.xnote.app.feature.search.SearchScreen
import com.xnote.app.navigation.AppDestination
import com.xnote.app.navigation.NotesRoute
import com.xnote.app.navigation.XNoteNavigationState
import com.xnote.app.navigation.decodeNotesStack
import com.xnote.app.navigation.encodeNotesStack
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// -- Constants

private val TabletBreakpoint = 600.dp

// -- Composables

@Composable
fun XNoteApp(
    noteLibrary: NoteLibrary,
    searchHistory: SearchHistoryRepository = EmptySearchHistoryRepository,
    settings: AppSettingsRepository? = null,
) {
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.Notes.name) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var isRecycleBinOpen by rememberSaveable { mutableStateOf(false) }
    var isAppearanceOpen by rememberSaveable { mutableStateOf(false) }
    var notesStackEncoded by rememberSaveable { mutableStateOf("") }
    var scopeEncoded by rememberSaveable { mutableStateOf("all") }
    var homeSortName by rememberSaveable { mutableStateOf(NoteListSort.UpdatedAt.name) }
    var notebookSortName by rememberSaveable { mutableStateOf(NoteListSort.Manual.name) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchNotebookId by rememberSaveable { mutableStateOf<String?>(null) }
    val uiState = remember { NotesUiState() }
    val recycleBinUiState = remember { RecycleBinUiState() }
    val navigationState = remember(
        destinationName,
        isSearchOpen,
        isRecycleBinOpen,
        isAppearanceOpen,
        notesStackEncoded,
    ) {
        XNoteNavigationState(
            destination = AppDestination.valueOf(destinationName),
            isSearchOpen = isSearchOpen,
            isRecycleBinOpen = isRecycleBinOpen,
            isAppearanceOpen = isAppearanceOpen,
            notesStack = decodeNotesStack(notesStackEncoded),
        )
    }
    val backdrop = rememberLayerBackdrop()
    val toastHostState = rememberXNoteToastHostState()
    val notesListState = rememberLazyListState()
    val notebookListState = rememberLazyListState()
    val editorScrollState = rememberScrollState()
    val agentListState = rememberLazyListState()
    val profileListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val recycleBinListState = rememberLazyListState()
    val appearanceScrollState = rememberScrollState()
    val appScope = rememberCoroutineScope()
    val interactionSettings = LocalXNoteInteractionSettings.current
    val editorNoteId = (navigationState.notesRoute as? NotesRoute.Editor)?.noteId
    val editorSession = remember(editorNoteId, noteLibrary) {
        editorNoteId?.let { NoteEditorSession(noteLibrary, it, appScope) }
    }
    val settingsRepository = remember(settings) { settings ?: InMemoryAppSettingsRepository() }
    val backgroundResolver = remember(noteLibrary) { NoteBackgroundResolver(noteLibrary) }
    var notebooks by remember { mutableStateOf<List<Notebook>>(emptyList()) }
    var activeNotes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var trashedNotes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<NoteSearchResult>>(emptyList()) }
    var recentQueries by remember { mutableStateOf<List<String>>(emptyList()) }
    var appSettings by remember { mutableStateOf<AppSettings>(defaultAppSettings()) }
    var defaultBackgroundResolution by remember { mutableStateOf(initialBackgroundResolution()) }
    var editorBackgroundResolution by remember { mutableStateOf(initialBackgroundResolution()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val unavailableBackgroundMessage = stringResource(R.string.background_resource_unavailable)

    LaunchedEffect(scopeEncoded, homeSortName, notebookSortName) {
        uiState.scope = decodeNotesScope(scopeEncoded)
        uiState.homeSort = runCatching { NoteListSort.valueOf(homeSortName) }.getOrDefault(NoteListSort.UpdatedAt)
        uiState.notebookSort = runCatching { NoteListSort.valueOf(notebookSortName) }.getOrDefault(NoteListSort.Manual)
    }
    LaunchedEffect(uiState.scope) { scopeEncoded = encodeNotesScope(uiState.scope) }
    LaunchedEffect(uiState.homeSort) { homeSortName = uiState.homeSort.name }
    LaunchedEffect(uiState.notebookSort) { notebookSortName = uiState.notebookSort.name }
    LaunchedEffect(noteLibrary) {
        launch { noteLibrary.observeNotebooks().collect { notebooks = it } }
        launch { noteLibrary.observeActiveNotes().collect { activeNotes = it } }
        launch { noteLibrary.observeTrashedNotes().collect { trashedNotes = it } }
    }
    LaunchedEffect(searchHistory) {
        searchHistory.recentQueries.collect { recentQueries = it }
    }
    LaunchedEffect(settingsRepository) {
        settingsRepository.settings.collect { appSettings = it }
    }
    LaunchedEffect(appSettings.defaultBackgroundKey, navigationState.isAppearanceOpen) {
        defaultBackgroundResolution = backgroundResolver.resolve(
            noteBackgroundKey = null,
            defaultBackgroundKeyRaw = appSettings.defaultBackgroundKey,
        )
        if (defaultBackgroundResolution.fellBack && navigationState.isAppearanceOpen) {
            toastHostState.showSnackbar(unavailableBackgroundMessage)
        }
    }
    LaunchedEffect(
        editorSession?.note?.id,
        editorSession?.note?.backgroundKey,
        appSettings.defaultBackgroundKey,
    ) {
        editorBackgroundResolution = backgroundResolver.resolve(
            noteBackgroundKey = editorSession?.note?.backgroundKey,
            defaultBackgroundKeyRaw = appSettings.defaultBackgroundKey,
        )
        if (editorSession?.note != null && editorBackgroundResolution.fellBack) {
            toastHostState.showSnackbar(unavailableBackgroundMessage)
        }
    }
    LaunchedEffect(navigationState.isSearchOpen, searchQuery, searchNotebookId, activeNotes) {
        if (!navigationState.isSearchOpen || searchQuery.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(120)
        searchResults = noteLibrary.searchNotes(searchQuery, searchNotebookId)
    }
    LaunchedEffect(notebooks, searchNotebookId) {
        if (searchNotebookId != null && notebooks.none { it.id == searchNotebookId }) {
            searchNotebookId = null
        }
    }
    LaunchedEffect(trashedNotes.map(Note::id)) {
        val currentIds = trashedNotes.mapTo(mutableSetOf(), Note::id)
        recycleBinUiState.selectedIds = recycleBinUiState.selectedIds.intersect(currentIds)
        recycleBinUiState.pendingPermanentDeleteIds =
            recycleBinUiState.pendingPermanentDeleteIds.intersect(currentIds)
        if (trashedNotes.isEmpty()) {
            recycleBinUiState.finishSelection()
            recycleBinUiState.moreVisible = false
        }
    }
    DisposableEffect(lifecycleOwner, editorSession) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                appScope.launch { editorSession?.flushSave() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun updateNavigationState(newState: XNoteNavigationState) {
        destinationName = newState.destination.name
        isSearchOpen = newState.isSearchOpen
        isRecycleBinOpen = newState.isRecycleBinOpen
        isAppearanceOpen = newState.isAppearanceOpen
        notesStackEncoded = encodeNotesStack(newState.notesStack)
    }

    fun createNote(notebookId: String?) {
        appScope.launch {
            val note = noteLibrary.createRichNote(notebookId)
            updateNavigationState(navigationState.openEditor(note.id))
        }
    }

    fun recordSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        searchQuery = normalized
        appScope.launch { searchHistory.record(normalized) }
    }

    fun popNotes() {
        appScope.launch {
            editorSession?.flushSave()
            updateNavigationState(navigationState.popNotes())
        }
    }

    fun resetDestination(destination: AppDestination) {
        if (destination == AppDestination.Notes && navigationState.notesStack.isNotEmpty()) {
            updateNavigationState(navigationState.copy(notesStack = emptyList()))
            return
        }

        val destinationListState = when (destination) {
            AppDestination.Notes -> notesListState
            AppDestination.Agent -> agentListState
            AppDestination.Profile -> profileListState
        }
        if (destinationListState.layoutInfo.totalItemsCount == 0) return
        appScope.launch {
            if (interactionSettings.reduceMotion) {
                destinationListState.scrollToItem(0)
            } else {
                destinationListState.animateScrollToItem(0)
            }
        }
    }

    val canGoBack = uiState.selectedIds.isNotEmpty() ||
        recycleBinUiState.selectionMode ||
        navigationState.isSearchOpen ||
        navigationState.isRecycleBinOpen ||
        navigationState.isAppearanceOpen ||
        navigationState.notesRoute !is NotesRoute.Home
    BackHandler(enabled = canGoBack) {
        when {
            recycleBinUiState.selectionMode -> recycleBinUiState.finishSelection()
            uiState.selectedIds.isNotEmpty() -> uiState.selectedIds = emptySet()
            navigationState.isSearchOpen -> updateNavigationState(navigationState.closeSearch())
            navigationState.isRecycleBinOpen -> {
                recycleBinUiState.finishSelection()
                updateNavigationState(navigationState.closeRecycleBin())
            }
            navigationState.isAppearanceOpen -> {
                updateNavigationState(navigationState.closeAppearance())
            }
            else -> popNotes()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= TabletBreakpoint
        val showsPrimaryChrome = navigationState.showsPrimaryChrome ||
            (isTablet && navigationState.isSearchOpen)
        val showsShellHeader = !navigationState.isRecycleBinOpen &&
            !navigationState.isAppearanceOpen &&
            (navigationState.isSearchOpen || navigationState.showsNotesPrimaryChrome)
        val isEditor = navigationState.destination == AppDestination.Notes &&
            navigationState.notesRoute is NotesRoute.Editor
        val showsEditorToolbar = isEditor && (
            editorSession?.isMarkdown != true ||
                editorSession.markdownMode == MarkdownEditorMode.Editing
        )
        val showsBottomNavigation = !isTablet && showsPrimaryChrome
        val showsRecycleSelection = navigationState.isRecycleBinOpen &&
            recycleBinUiState.selectionMode
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues()
            .calculateBottomPadding()
        val bottomOverlayHeight = when {
            showsEditorToolbar -> XNoteEditorToolbarHeight
            showsRecycleSelection -> XNoteRecycleSelectionHeight
            showsBottomNavigation -> XNoteBottomNavigationHeight
            else -> 0.dp
        }
        val contentStartPadding = when {
            isTablet && showsPrimaryChrome -> 112.dp
            isTablet -> 24.dp
            else -> XNoteSpacingMedium
        }
        val contentEndPadding = if (isTablet) 24.dp else XNoteSpacingMedium
        val contentTopPadding = statusBarHeight + XNoteHeaderHeight + XNoteSpacingMedium
        val contentPadding = PaddingValues(
            start = contentStartPadding,
            top = contentTopPadding,
            end = contentEndPadding,
            bottom = navigationBarHeight + bottomOverlayHeight + XNoteSpacingMedium,
        )
        val editorContentPadding = PaddingValues(
            start = contentStartPadding,
            top = contentTopPadding,
            end = contentEndPadding,
            bottom = bottomOverlayHeight + XNoteSpacingMedium,
        )
        val listState = when {
            navigationState.isSearchOpen -> searchListState
            navigationState.isRecycleBinOpen -> recycleBinListState
            else -> when (navigationState.destination) {
                AppDestination.Notes -> notesListState
                AppDestination.Agent -> agentListState
                AppDestination.Profile -> profileListState
            }
        }
        val scrollable = when {
            navigationState.isAppearanceOpen -> appearanceScrollState
            isEditor -> editorScrollState
            navigationState.notesRoute is NotesRoute.Notebook -> notebookListState
            else -> listState
        }
        val scrollEdgeState = rememberXNoteScrollEdgeState(scrollable)
        val scrollEdges = if (showsBottomNavigation || showsEditorToolbar || showsRecycleSelection) {
            setOf(XNoteScrollEdge.Top, XNoteScrollEdge.Bottom)
        } else {
            setOf(XNoteScrollEdge.Top)
        }
        val bottomScrollEdgeStyle = if (showsEditorToolbar) {
            com.xnote.app.design.XNoteScrollEdgeStyle.Hard
        } else {
            com.xnote.app.design.XNoteScrollEdgeStyle.Soft
        }

        XNotePageScaffold(
            backdrop = backdrop,
            scrollEdgeState = scrollEdgeState,
            scrollEdges = scrollEdges,
            bottomOverlayHeight = bottomOverlayHeight,
            bottomScrollEdgeStyle = bottomScrollEdgeStyle,
            toastHostState = toastHostState,
            content = {
                DestinationContent(
                    navigationState = navigationState,
                    noteLibrary = noteLibrary,
                    uiState = uiState,
                    notebooks = notebooks,
                    backdrop = backdrop,
                    contentPadding = contentPadding,
                    editorContentPadding = editorContentPadding,
                    listState = listState,
                    notebookListState = notebookListState,
                    editorScrollState = editorScrollState,
                    editorSession = editorSession,
                    editorBackground = editorBackgroundResolution.background,
                    defaultBackgroundKey = appSettings.defaultBackgroundKey,
                    defaultBackgroundResolution = defaultBackgroundResolution,
                    settings = settingsRepository,
                    appearanceScrollState = appearanceScrollState,
                    searchQuery = searchQuery,
                    searchNotebookId = searchNotebookId,
                    searchResults = searchResults,
                    recentQueries = recentQueries,
                    trashedNotes = trashedNotes,
                    recycleBinUiState = recycleBinUiState,
                    toastHostState = toastHostState,
                    onOpenNote = { updateNavigationState(navigationState.openEditor(it)) },
                    onCreateNote = ::createNote,
                    onSearchQueryChange = { searchQuery = it },
                    onSearch = ::recordSearch,
                    onSearchNotebookSelected = { searchNotebookId = it },
                    onOpenRecycleBin = {
                        updateNavigationState(navigationState.openRecycleBin())
                    },
                    onOpenBackgroundSettings = {
                        updateNavigationState(navigationState.openAppearance())
                    },
                )
            },
            overlay = {
                if (showsShellHeader) {
                    XNoteHeader(
                        title = if (navigationState.isSearchOpen) {
                            stringResource(R.string.search_title)
                        } else {
                            stringResource(navigationState.destination.titleRes)
                        },
                        backdrop = backdrop,
                        onBack = if (navigationState.isSearchOpen) {
                            { updateNavigationState(navigationState.closeSearch()) }
                        } else {
                            null
                        },
                        actions = if (navigationState.isSearchOpen) {
                            if (recentQueries.isEmpty()) {
                                emptyList()
                            } else {
                                listOf(
                                    XNoteHeaderAction(
                                        iconRes = R.drawable.ic_lucide_trash_2,
                                        contentDescription = stringResource(R.string.search_clear_history),
                                        onClick = { appScope.launch { searchHistory.clear() } },
                                    ),
                                )
                            }
                        } else {
                            listOf(
                                XNoteHeaderAction(
                                    iconRes = R.drawable.ic_lucide_search,
                                    contentDescription = stringResource(R.string.action_search),
                                    onClick = {
                                        updateNavigationState(navigationState.openSearch())
                                    },
                                ),
                            )
                        },
                        horizontalPadding = if (isTablet) 24.dp else XNoteSpacingMedium,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }

                if (showsPrimaryChrome) {
                    if (isTablet) {
                        XNoteNavigationRail(
                            currentDestination = navigationState.destination,
                            onDestinationSelected = {
                                updateNavigationState(navigationState.openDestination(it))
                            },
                            backdrop = backdrop,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                    } else {
                        XNoteBottomNavigation(
                            currentDestination = navigationState.destination,
                            onDestinationSelected = {
                                updateNavigationState(navigationState.openDestination(it))
                            },
                            onDestinationReselected = ::resetDestination,
                            backdrop = backdrop,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }

                if (!navigationState.isSearchOpen &&
                    !navigationState.isRecycleBinOpen &&
                    !navigationState.isAppearanceOpen &&
                    navigationState.destination == AppDestination.Notes
                ) {
                    NotesChrome(
                        route = navigationState.notesRoute,
                        library = noteLibrary,
                        ui = uiState,
                        notebooks = notebooks,
                        allNotesCount = activeNotes.size,
                        notebookStats = notebookStatsFrom(activeNotes),
                        unfiledStats = unfiledStatsFrom(activeNotes),
                        backdrop = backdrop,
                        isTablet = isTablet,
                        editorSession = editorSession,
                        editorBackground = editorBackgroundResolution.background,
                        toastHostState = toastHostState,
                        onOpenNotebook = { updateNavigationState(navigationState.openNotebook(it)) },
                        onCreateNote = ::createNote,
                        onPop = ::popNotes,
                    )
                }

                if (navigationState.isRecycleBinOpen) {
                    RecycleBinChrome(
                        notes = trashedNotes,
                        ui = recycleBinUiState,
                        library = noteLibrary,
                        backdrop = backdrop,
                        isTablet = isTablet,
                        toastHostState = toastHostState,
                        onBack = {
                            recycleBinUiState.finishSelection()
                            updateNavigationState(navigationState.closeRecycleBin())
                        },
                    )
                }

                if (navigationState.isAppearanceOpen) {
                    XNoteHeader(
                        title = stringResource(R.string.background_settings_title),
                        backdrop = backdrop,
                        onBack = {
                            updateNavigationState(navigationState.closeAppearance())
                        },
                        horizontalPadding = if (isTablet) 24.dp else XNoteSpacingMedium,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            },
        )
    }
}

@Composable
private fun DestinationContent(
    navigationState: XNoteNavigationState,
    noteLibrary: NoteLibrary,
    uiState: NotesUiState,
    notebooks: List<Notebook>,
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    editorContentPadding: PaddingValues,
    listState: LazyListState,
    notebookListState: LazyListState,
    editorScrollState: ScrollState,
    editorSession: NoteEditorSession?,
    editorBackground: ResolvedNoteBackground,
    defaultBackgroundKey: String,
    defaultBackgroundResolution: NoteBackgroundResolution,
    settings: AppSettingsRepository,
    appearanceScrollState: ScrollState,
    searchQuery: String,
    searchNotebookId: String?,
    searchResults: List<NoteSearchResult>,
    recentQueries: List<String>,
    trashedNotes: List<Note>,
    recycleBinUiState: RecycleBinUiState,
    toastHostState: androidx.compose.material3.SnackbarHostState,
    onOpenNote: (String) -> Unit,
    onCreateNote: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSearchNotebookSelected: (String?) -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val backgroundFailureMessage = stringResource(R.string.background_resource_unavailable)

    if (navigationState.isAppearanceOpen) {
        DefaultBackgroundScreen(
            defaultBackgroundKey = defaultBackgroundKey,
            resolution = defaultBackgroundResolution,
            library = noteLibrary,
            settings = settings,
            backdrop = backdrop,
            toastHostState = toastHostState,
            contentPadding = contentPadding,
            scrollState = appearanceScrollState,
            modifier = modifier,
        )
        return
    }

    if (navigationState.isRecycleBinOpen) {
        val restoredMessage = stringResource(R.string.recycle_bin_restored)
        RecycleBinScreen(
            notes = trashedNotes,
            notebooks = notebooks,
            selectedIds = recycleBinUiState.selectedIds,
            selectionMode = recycleBinUiState.selectionMode,
            backdrop = backdrop,
            contentPadding = contentPadding,
            listState = listState,
            onToggleSelection = { id ->
                recycleBinUiState.selectedIds = recycleBinUiState.selectedIds.toggle(id)
            },
            onEnterSelection = { id ->
                recycleBinUiState.selectionMode = true
                recycleBinUiState.selectedIds = setOf(id)
            },
            onRestore = { id ->
                scope.launch {
                    noteLibrary.restoreNotes(listOf(id))
                    toastHostState.showSnackbar(restoredMessage)
                }
            },
            onPermanentlyDelete = { id ->
                recycleBinUiState.pendingPermanentDeleteIds = setOf(id)
            },
            modifier = modifier,
        )
        return
    }

    if (navigationState.isSearchOpen) {
        SearchScreen(
            query = searchQuery,
            selectedNotebookId = searchNotebookId,
            results = searchResults,
            recentQueries = recentQueries,
            notebooks = notebooks,
            backdrop = backdrop,
            contentPadding = contentPadding,
            listState = listState,
            onQueryChange = onSearchQueryChange,
            onSearch = onSearch,
            onNotebookSelected = onSearchNotebookSelected,
            onOpenNote = onOpenNote,
            modifier = modifier,
        )
        return
    }

    when (navigationState.destination) {
        AppDestination.Notes -> when (val route = navigationState.notesRoute) {
            NotesRoute.Home -> NotesHomeScreen(
                library = noteLibrary,
                backdrop = backdrop,
                contentPadding = contentPadding,
                listState = listState,
                scope = uiState.scope,
                sort = uiState.homeSort,
                notebooks = notebooks,
                selectedIds = uiState.selectedIds,
                onOpenNote = onOpenNote,
                onToggleSelect = { id ->
                    uiState.selectedIds = uiState.selectedIds.toggle(id)
                },
                onEnterSelection = { id -> uiState.selectedIds = setOf(id) },
                onOpenPicker = { uiState.pickerVisible = true },
                onOpenSort = { uiState.sortMenuVisible = true },
                onCreateNote = {
                    val notebookId = when (val scope = uiState.scope) {
                        NotesScope.All, NotesScope.Unfiled -> null
                        is NotesScope.Notebook -> scope.id
                    }
                    onCreateNote(notebookId)
                },
                modifier = modifier,
            )
            is NotesRoute.Notebook -> NotebookDetailScreen(
                library = noteLibrary,
                notebook = notebooks.firstOrNull { it.id == route.notebookId },
                backdrop = backdrop,
                contentPadding = contentPadding,
                listState = notebookListState,
                sort = uiState.notebookSort,
                selectedIds = uiState.selectedIds,
                onOpenNote = onOpenNote,
                onToggleSelect = { id ->
                    uiState.selectedIds = uiState.selectedIds.toggle(id)
                },
                onEnterSelection = { id -> uiState.selectedIds = setOf(id) },
                onOpenSort = { uiState.sortMenuVisible = true },
                modifier = modifier,
            )
            is NotesRoute.Editor -> editorSession?.let { session ->
                NoteEditorScreen(
                    session = session,
                    background = editorBackground,
                    backdrop = backdrop,
                    contentPadding = editorContentPadding,
                    scrollState = editorScrollState,
                    onBackgroundLoadFailed = {
                        scope.launch { toastHostState.showSnackbar(backgroundFailureMessage) }
                    },
                    modifier = modifier,
                )
            }
        }

        AppDestination.Agent -> PlaceholderScreen(
            titleRes = R.string.agent_placeholder_title,
            descriptionRes = R.string.agent_placeholder_description,
            iconRes = R.drawable.ic_lucide_sparkles,
            backdrop = backdrop,
            contentPadding = contentPadding,
            listState = listState,
            modifier = modifier,
        )

        AppDestination.Profile -> ProfileScreen(
            trashCount = trashedNotes.size,
            contentPadding = contentPadding,
            listState = listState,
            onOpenRecycleBin = onOpenRecycleBin,
            onOpenBackgroundSettings = onOpenBackgroundSettings,
            modifier = modifier,
        )
    }
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

private fun initialBackgroundResolution(): NoteBackgroundResolution {
    val background = defaultResolvedBackground()
    return NoteBackgroundResolution(
        background = background,
        requestedKey = background.key,
        fellBack = false,
    )
}

@Composable
private fun XNoteBottomNavigation(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    onDestinationReselected: (AppDestination) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val selectedIndexState = rememberUpdatedState(currentDestination.ordinal)
    val selectedIndex = remember { { selectedIndexState.value } }

    LiquidBottomTabs(
        selectedTabIndex = selectedIndex,
        onTabSelected = { index ->
            onDestinationSelected(AppDestination.entries[index])
        },
        onTabReselected = { index ->
            onDestinationReselected(AppDestination.entries[index])
        },
        backdrop = backdrop,
        tabsCount = AppDestination.entries.size,
        modifier = modifier
            .testTag("xnote-bottom-navigation")
            .navigationBarsPadding()
            .padding(horizontal = 36.dp)
            .padding(top = XNoteSpacingSmall, bottom = XNoteSpacingMedium)
            .fillMaxWidth()
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = destination == currentDestination
            val label = stringResource(destination.labelRes)
            LiquidBottomTab(
                index = destination.ordinal,
                modifier = Modifier.semantics { this.selected = selected },
            ) {
                NavigationIcon(
                    destination = destination,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun XNoteNavigationRail(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    XNoteLiquidGlassPanel(
        backdrop = backdrop,
        shape = Capsule(),
        modifier = modifier
            .testTag("xnote-navigation-rail")
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .padding(start = XNoteSpacingMedium, top = 64.dp, bottom = XNoteSpacingMedium)
            .width(72.dp)
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            AppDestination.entries.forEach { destination ->
                XNoteNavigationRailItem(
                    destination = destination,
                    selected = destination == currentDestination,
                    onClick = { onDestinationSelected(destination) },
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                )
            }
        }
    }
}

@Composable
private fun XNoteNavigationRailItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(destination.labelRes)
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.semantics {
            this.selected = selected
        },
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
        height = 72.dp,
        contentPadding = PaddingValues(0.dp),
        role = Role.Tab,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            NavigationIcon(destination = destination, tint = contentColor)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun NavigationIcon(
    destination: AppDestination,
    tint: Color,
) {
    Icon(
        painter = painterResource(destination.iconRes),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp),
    )
}
