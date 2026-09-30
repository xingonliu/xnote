package com.xnote.app.design

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

// -- Functions

@Composable
fun XNoteSettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    actions: List<XNoteHeaderAction> = emptyList(),
    overlay: @Composable BoxScope.(Backdrop) -> Unit = {},
    content: LazyListScope.(Backdrop) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    BackHandler(onBack = onBack)
    XNotePageScaffold(backdrop, scrollEdgeState = rememberXNoteScrollEdgeState(listState), content = {
        LazyColumn(modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxSize().imePadding(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                top = xNoteScrollEdgePadding(insets.calculateTopPadding() + XNoteHeaderHeight),
                bottom = insets.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp), content = { content(backdrop) })
    }, overlay = { glass ->
        XNoteHeader(title, glass, onBack = onBack, actions = actions, modifier = Modifier.align(Alignment.TopCenter))
        overlay(glass)
    })
}
