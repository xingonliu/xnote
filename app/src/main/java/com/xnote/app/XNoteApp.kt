package com.xnote.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.xnote.app.design.XNoteAccentYellow
import com.xnote.app.design.XNoteHeader
import com.xnote.app.design.XNoteLiquidGlassButton
import com.xnote.app.design.XNoteLiquidGlassButtonType
import com.xnote.app.design.XNoteLiquidGlassPanel
import com.xnote.app.design.XNotePageScaffold
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.feature.PlaceholderScreen
import com.xnote.app.feature.notes.NotesHomeScreen
import com.xnote.app.navigation.AppDestination
import com.xnote.app.navigation.XNoteNavigationState

// -- Constants

private val TabletBreakpoint = 600.dp

// -- Composables

@Composable
fun XNoteApp() {
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.Notes.name) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    val navigationState = remember(destinationName, isSearchOpen) {
        XNoteNavigationState(
            destination = AppDestination.valueOf(destinationName),
            isSearchOpen = isSearchOpen,
        )
    }
    val backdrop = rememberLayerBackdrop()

    fun updateNavigationState(newState: XNoteNavigationState) {
        destinationName = newState.destination.name
        isSearchOpen = newState.isSearchOpen
    }

    BackHandler(enabled = navigationState.isSearchOpen) {
        updateNavigationState(navigationState.closeSearch())
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= TabletBreakpoint
        val contentPadding = PaddingValues(
            start = if (isTablet) 112.dp else XNoteSpacingMedium,
            top = 76.dp,
            end = if (isTablet) 24.dp else XNoteSpacingMedium,
            bottom = if (isTablet || navigationState.isSearchOpen) 24.dp else 112.dp,
        )

        XNotePageScaffold(
            backdrop = backdrop,
            content = {
                DestinationContent(
                    navigationState = navigationState,
                    backdrop = backdrop,
                    contentPadding = contentPadding,
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    ),
                )
            },
            overlay = {
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
                    onSearch = if (navigationState.isSearchOpen) {
                        null
                    } else {
                        { updateNavigationState(navigationState.openSearch()) }
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                if (!navigationState.isSearchOpen) {
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
                            backdrop = backdrop,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun DestinationContent(
    navigationState: XNoteNavigationState,
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (navigationState.isSearchOpen) {
        PlaceholderScreen(
            titleRes = R.string.search_placeholder_title,
            descriptionRes = R.string.search_placeholder_description,
            backdrop = backdrop,
            contentPadding = contentPadding,
            modifier = modifier,
        )
        return
    }

    when (navigationState.destination) {
        AppDestination.Notes -> NotesHomeScreen(
            backdrop = backdrop,
            contentPadding = contentPadding,
            onCreateNote = {},
            createEnabled = false,
            modifier = modifier,
        )

        AppDestination.Agent -> PlaceholderScreen(
            titleRes = R.string.agent_placeholder_title,
            descriptionRes = R.string.agent_placeholder_description,
            backdrop = backdrop,
            contentPadding = contentPadding,
            modifier = modifier,
        )

        AppDestination.Profile -> PlaceholderScreen(
            titleRes = R.string.profile_placeholder_title,
            descriptionRes = R.string.profile_placeholder_description,
            backdrop = backdrop,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    }
}

@Composable
private fun XNoteBottomNavigation(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    XNoteLiquidGlassPanel(
        backdrop = backdrop,
        shape = Capsule(),
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = XNoteSpacingMedium, vertical = XNoteSpacingSmall)
            .fillMaxWidth()
            .height(72.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppDestination.entries.forEach { destination ->
                XNoteNavigationItem(
                    destination = destination,
                    selected = destination == currentDestination,
                    onClick = { onDestinationSelected(destination) },
                    backdrop = backdrop,
                    showLabelBesideIcon = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
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
                XNoteNavigationItem(
                    destination = destination,
                    selected = destination == currentDestination,
                    onClick = { onDestinationSelected(destination) },
                    backdrop = backdrop,
                    showLabelBesideIcon = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                )
            }
        }
    }
}

@Composable
private fun XNoteNavigationItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop,
    showLabelBesideIcon: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(destination.labelRes)
    XNoteLiquidGlassButton(
        onClick = onClick,
        backdrop = backdrop,
        type = XNoteLiquidGlassButtonType.Rect,
        selected = selected,
        modifier = modifier.semantics {
            this.selected = selected
            role = Role.Tab
        },
    ) {
        if (showLabelBesideIcon) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(XNoteSpacingSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavigationIcon(destination = destination, selected = selected)
                Text(text = label, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                NavigationIcon(destination = destination, selected = selected)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun NavigationIcon(
    destination: AppDestination,
    selected: Boolean,
) {
    Icon(
        painter = painterResource(destination.iconRes),
        contentDescription = null,
        tint = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.size(22.dp),
    )
}
