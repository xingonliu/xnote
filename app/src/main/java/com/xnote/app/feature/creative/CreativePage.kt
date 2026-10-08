package com.xnote.app.feature.creative

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.design.*

// -- Functions

@Composable
fun CreativePage(
    title: String,
    onBack: (() -> Unit)?,
    actions: List<XNoteHeaderAction> = emptyList(),
    toolbar: (@Composable ColumnScope.() -> Unit)? = null,
    overlay: @Composable BoxScope.(Backdrop) -> Unit = {},
    contentPadding: PaddingValues? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    XNotePageScaffold(backdrop, modifier = Modifier.xNoteOverlayInputBarrier(), scrollEdges = emptySet(),
        content = {
            val paddingModifier = if (contentPadding != null) Modifier.imePadding().padding(contentPadding)
                else Modifier.safeDrawingPadding().imePadding()
                    .padding(top = XNoteHeaderHeight + 12.dp, bottom = 12.dp).padding(horizontal = 16.dp)
            BoxWithConstraints(Modifier.fillMaxSize().then(paddingModifier)) {
                if (toolbar != null && maxWidth >= 600.dp && maxWidth > maxHeight) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f).fillMaxHeight(), content = content)
                        XNoteLiquidGlassPanel(backdrop, Modifier.width(280.dp).fillMaxHeight()) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp), content = toolbar)
                        }
                    }
                } else {
                    val toolbarMaxHeight = maxHeight * 0.5f
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
                        if (toolbar != null) XNoteLiquidGlassPanel(backdrop) {
                            Column(Modifier.heightIn(max = toolbarMaxHeight).verticalScroll(rememberScrollState()).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp), content = toolbar)
                        }
                    }
                }
            }
        }, overlay = { glass ->
            XNoteHeader(title, glass, onBack = onBack, actions = actions, modifier = Modifier.align(Alignment.TopCenter))
            overlay(glass)
        })
}

@Composable
fun TransparencyGrid(modifier: Modifier = Modifier) {
    val light = MaterialTheme.colorScheme.surface
    val dark = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val step = 12.dp.toPx()
        for (row in 0..(size.height / step).toInt()) for (column in 0..(size.width / step).toInt()) {
            drawRect(if ((row + column) % 2 == 0) light else dark,
                Offset(column * step, row * step), Size(step, step))
        }
    }
}
