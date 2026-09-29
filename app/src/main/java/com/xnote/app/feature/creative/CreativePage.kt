package com.xnote.app.feature.creative

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.design.*

// -- Functions

@Composable
fun CreativePage(title: String, onBack: () -> Unit, toast: SnackbarHostState,
    actions: List<XNoteHeaderAction> = emptyList(),
    overlay: @Composable BoxScope.(com.kyant.backdrop.Backdrop) -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val edges = setOf(XNoteScrollEdge.Top, XNoteScrollEdge.Bottom)
    BackHandler(onBack = onBack)
    XNotePageScaffold(backdrop, modifier = Modifier.xNoteOverlayInputBarrier(), scrollEdges = edges, alwaysVisibleScrollEdges = edges, toastHostState = toast,
        content = {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(top = XNoteHeaderHeight + 16.dp, bottom = 24.dp)
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }, overlay = { glass ->
            XNoteHeader(title, glass, onBack = onBack, actions = actions, modifier = Modifier.align(Alignment.TopCenter))
            overlay(glass)
        })
}

@Composable
fun TransparencyGrid(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 12.dp.toPx()
        for (row in 0..(size.height / step).toInt()) for (column in 0..(size.width / step).toInt()) {
            drawRect(if ((row + column) % 2 == 0) Color(0xffeeeeee) else Color(0xffcccccc),
                Offset(column * step, row * step), Size(step, step))
        }
    }
}
