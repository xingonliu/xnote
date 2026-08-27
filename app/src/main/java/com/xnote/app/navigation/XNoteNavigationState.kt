package com.xnote.app.navigation

// -- Type Definitions

sealed interface NotesRoute {
    data object Home : NotesRoute

    data class Notebook(
        val notebookId: String,
    ) : NotesRoute

    data class Editor(
        val noteId: String,
    ) : NotesRoute
}

data class XNoteNavigationState(
    val destination: AppDestination = AppDestination.Notes,
    val isSearchOpen: Boolean = false,
    val isRecycleBinOpen: Boolean = false,
    val isAppearanceOpen: Boolean = false,
    val notesStack: List<NotesRoute> = emptyList(),
) {
    val notesRoute: NotesRoute
        get() = notesStack.lastOrNull() ?: NotesRoute.Home

    val showsNotesPrimaryChrome: Boolean
        get() = destination != AppDestination.Notes || notesRoute is NotesRoute.Home

    val showsPrimaryChrome: Boolean
        get() = !isSearchOpen && !isRecycleBinOpen && !isAppearanceOpen && showsNotesPrimaryChrome

    fun openDestination(destination: AppDestination) = copy(
        destination = destination,
        isSearchOpen = false,
        isRecycleBinOpen = false,
        isAppearanceOpen = false,
    )

    fun openSearch() = copy(
        isSearchOpen = true,
        isRecycleBinOpen = false,
        isAppearanceOpen = false,
    )

    fun closeSearch() = copy(isSearchOpen = false)

    fun openRecycleBin() = copy(
        destination = AppDestination.Profile,
        isSearchOpen = false,
        isRecycleBinOpen = true,
        isAppearanceOpen = false,
    )

    fun closeRecycleBin() = copy(isRecycleBinOpen = false)

    fun openAppearance() = copy(
        destination = AppDestination.Profile,
        isSearchOpen = false,
        isRecycleBinOpen = false,
        isAppearanceOpen = true,
    )

    fun closeAppearance() = copy(isAppearanceOpen = false)

    fun openNotebook(notebookId: String) = copy(
        destination = AppDestination.Notes,
        isSearchOpen = false,
        isRecycleBinOpen = false,
        isAppearanceOpen = false,
        notesStack = listOf(NotesRoute.Notebook(notebookId)),
    )

    fun openEditor(noteId: String) = copy(
        destination = AppDestination.Notes,
        isSearchOpen = false,
        isRecycleBinOpen = false,
        isAppearanceOpen = false,
        notesStack = notesStack.filterNot { it is NotesRoute.Editor } + NotesRoute.Editor(noteId),
    )

    fun popNotes(): XNoteNavigationState {
        if (notesStack.isEmpty()) return this
        return copy(notesStack = notesStack.dropLast(1))
    }
}

// -- Functions

fun encodeNotesStack(stack: List<NotesRoute>): String {
    return stack.joinToString(separator = "|") { route ->
        when (route) {
            NotesRoute.Home -> "home"
            is NotesRoute.Notebook -> "notebook:${route.notebookId}"
            is NotesRoute.Editor -> "editor:${route.noteId}"
        }
    }
}

fun decodeNotesStack(raw: String): List<NotesRoute> {
    if (raw.isBlank()) return emptyList()
    return raw.split('|').mapNotNull { token ->
        when {
            token.isBlank() || token == "home" -> null
            token.startsWith("notebook:") -> NotesRoute.Notebook(token.removePrefix("notebook:"))
            token.startsWith("editor:") -> NotesRoute.Editor(token.removePrefix("editor:"))
            else -> null
        }
    }
}
