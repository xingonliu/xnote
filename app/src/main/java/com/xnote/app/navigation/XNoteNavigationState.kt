package com.xnote.app.navigation

// -- Type Definitions

data class XNoteNavigationState(
    val destination: AppDestination = AppDestination.Notes,
    val isSearchOpen: Boolean = false,
) {
    fun openDestination(destination: AppDestination) = copy(
        destination = destination,
        isSearchOpen = false,
    )

    fun openSearch() = copy(isSearchOpen = true)

    fun closeSearch() = copy(isSearchOpen = false)
}
