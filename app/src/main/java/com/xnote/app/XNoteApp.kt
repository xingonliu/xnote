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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.xnote.app.design.XNotePageScaffold
import com.xnote.app.design.XNoteScrollEdge
import com.xnote.app.design.XNoteSpacingMedium
import com.xnote.app.design.XNoteSpacingSmall
import com.xnote.app.design.rememberXNoteScrollEdgeState
import com.xnote.app.design.rememberXNoteToastHostState
import com.xnote.app.design.liquidglass.LiquidBottomTab
import com.xnote.app.design.liquidglass.LiquidBottomTabs
import com.xnote.app.design.liquidglass.LiquidButton
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
    val toastHostState = rememberXNoteToastHostState()
    val notesListState = rememberLazyListState()
    val agentListState = rememberLazyListState()
    val profileListState = rememberLazyListState()
    val searchListState = rememberLazyListState()

    fun updateNavigationState(newState: XNoteNavigationState) {
        destinationName = newState.destination.name
        isSearchOpen = newState.isSearchOpen
    }

    BackHandler(enabled = navigationState.isSearchOpen) {
        updateNavigationState(navigationState.closeSearch())
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= TabletBreakpoint
        val showsBottomNavigation = !isTablet && !navigationState.isSearchOpen
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues()
            .calculateBottomPadding()
        val bottomOverlayHeight = if (showsBottomNavigation) {
            XNoteBottomNavigationHeight
        } else {
            0.dp
        }
        val contentPadding = PaddingValues(
            start = if (isTablet) 112.dp else XNoteSpacingMedium,
            top = statusBarHeight + XNoteHeaderHeight + XNoteSpacingMedium,
            end = if (isTablet) 24.dp else XNoteSpacingMedium,
            bottom = navigationBarHeight + bottomOverlayHeight + XNoteSpacingMedium,
        )
        val listState = if (navigationState.isSearchOpen) {
            searchListState
        } else {
            when (navigationState.destination) {
                AppDestination.Notes -> notesListState
                AppDestination.Agent -> agentListState
                AppDestination.Profile -> profileListState
            }
        }
        val scrollEdgeState = rememberXNoteScrollEdgeState(listState)
        val scrollEdges = if (showsBottomNavigation) {
            setOf(XNoteScrollEdge.Top, XNoteScrollEdge.Bottom)
        } else {
            setOf(XNoteScrollEdge.Top)
        }

        XNotePageScaffold(
            backdrop = backdrop,
            scrollEdgeState = scrollEdgeState,
            scrollEdges = scrollEdges,
            bottomOverlayHeight = bottomOverlayHeight,
            toastHostState = toastHostState,
            content = {
                DestinationContent(
                    navigationState = navigationState,
                    backdrop = backdrop,
                    contentPadding = contentPadding,
                    listState = listState,
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
                    actions = if (navigationState.isSearchOpen) {
                        emptyList()
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
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    if (navigationState.isSearchOpen) {
        PlaceholderScreen(
            titleRes = R.string.search_placeholder_title,
            descriptionRes = R.string.search_placeholder_description,
            iconRes = R.drawable.ic_lucide_search,
            backdrop = backdrop,
            contentPadding = contentPadding,
            listState = listState,
            modifier = modifier,
        )
        return
    }

    when (navigationState.destination) {
        AppDestination.Notes -> NotesHomeScreen(
            backdrop = backdrop,
            contentPadding = contentPadding,
            listState = listState,
            onCreateNote = {},
            createEnabled = false,
            modifier = modifier,
        )

        AppDestination.Agent -> PlaceholderScreen(
            titleRes = R.string.agent_placeholder_title,
            descriptionRes = R.string.agent_placeholder_description,
            iconRes = R.drawable.ic_lucide_sparkles,
            backdrop = backdrop,
            contentPadding = contentPadding,
            listState = listState,
            modifier = modifier,
        )

        AppDestination.Profile -> PlaceholderScreen(
            titleRes = R.string.profile_placeholder_title,
            descriptionRes = R.string.profile_placeholder_description,
            iconRes = R.drawable.ic_lucide_user_round,
            backdrop = backdrop,
            contentPadding = contentPadding,
            listState = listState,
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
    val selectedIndexState = rememberUpdatedState(currentDestination.ordinal)
    val selectedIndex = remember { { selectedIndexState.value } }

    LiquidBottomTabs(
        selectedTabIndex = selectedIndex,
        onTabSelected = { index ->
            onDestinationSelected(AppDestination.entries[index])
        },
        backdrop = backdrop,
        tabsCount = AppDestination.entries.size,
        modifier = modifier
            .testTag("xnote-bottom-navigation")
            .navigationBarsPadding()
            .padding(horizontal = 36.dp)
            .padding(top = XNoteSpacingSmall)
            .fillMaxWidth()
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = destination == currentDestination
            val label = stringResource(destination.labelRes)
            LiquidBottomTab(
                onClick = { onDestinationSelected(destination) },
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
