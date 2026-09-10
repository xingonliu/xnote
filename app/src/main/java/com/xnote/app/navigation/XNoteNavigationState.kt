package com.xnote.app.navigation

// -- Type Definitions

enum class NoteCollection {
    All,
    Unfiled,
}

sealed interface NotesRoute {
    data object Home : NotesRoute

    data class Collection(val collection: NoteCollection) : NotesRoute

    data class Notebook(
        val notebookId: String,
    ) : NotesRoute

    data class Reader(
        val notebookId: String? = null,
        val noteId: String? = null,
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

    fun openCollection(collection: NoteCollection) = copy(
        destination = AppDestination.Notes,
        isSearchOpen = false,
        isRecycleBinOpen = false,
        isAppearanceOpen = false,
        notesStack = listOf(NotesRoute.Collection(collection)),
    )

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
        notesStack = (if (notesRoute is NotesRoute.Editor) notesStack.dropLast(1) else notesStack) + NotesRoute.Editor(noteId),
    )

    fun openReader(notebookId: String? = null, noteId: String? = null): XNoteNavigationState {
        require((notebookId == null) != (noteId == null))
        return copy(notesStack = notesStack + NotesRoute.Reader(notebookId, noteId))
    }

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
            is NotesRoute.Collection -> "collection:${route.collection.name}"
            is NotesRoute.Notebook -> "notebook:${route.notebookId}"
            is NotesRoute.Editor -> "editor:${route.noteId}"
            is NotesRoute.Reader -> if (route.notebookId != null) "read-notebook:${route.notebookId}" else "read-note:${route.noteId}"
        }
    }
}

fun decodeNotesStack(raw: String): List<NotesRoute> {
    if (raw.isBlank()) return emptyList()
    return raw.split('|').mapNotNull { token ->
        when {
            token.isBlank() || token == "home" -> null
            token.startsWith("collection:") -> NoteCollection.entries
                .firstOrNull { it.name == token.removePrefix("collection:") }
                ?.let { NotesRoute.Collection(it) }
            token.startsWith("notebook:") -> NotesRoute.Notebook(token.removePrefix("notebook:"))
            token.startsWith("read-notebook:") -> NotesRoute.Reader(notebookId = token.removePrefix("read-notebook:"))
            token.startsWith("read-note:") -> NotesRoute.Reader(noteId = token.removePrefix("read-note:"))
            token.startsWith("editor:") -> NotesRoute.Editor(token.removePrefix("editor:"))
            else -> null
        }
    }
}
