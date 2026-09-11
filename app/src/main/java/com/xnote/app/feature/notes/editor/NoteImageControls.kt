package com.xnote.app.feature.notes.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.design.XNotePopupAnchor
import com.xnote.app.design.xNotePopupAnchor
import com.xnote.app.design.EditorSymbolButton
import com.xnote.app.design.XNoteLiquidGlassPanel
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.domain.document.*
import kotlin.math.*

// -- Type Definitions

data class ImagePlacement(val id: String, val center: Offset, val width: Float, val height: Float, val rotation: Float)

// -- Functions

@Composable
fun BoxScope.NoteImageControls(session: NoteEditorSession, backdrop: Backdrop, replacementAnchor: XNotePopupAnchor) {
    val block = session.document.block(session.selection.blockId) as? ImageBlock ?: return
    val placement = session.imagePlacement?.takeIf { it.id == block.id } ?: return
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = if (primary.luminance() > 0.179f) Color.Black else Color.White
    val handleDescription = stringResource(R.string.image_transform_handle)
    BoxWithConstraints(Modifier.matchParentSize().onGloballyPositioned { overlayOrigin = it.localToRoot(Offset.Zero) }) {
        val center = (placement.center - overlayOrigin) / density
        val width = placement.width / density
        val height = placement.height / density
        val angle = Math.toRadians(block.rotationDegrees.toDouble())
        fun corner(x: Float, y: Float): Offset = center + Offset(
            (x * cos(angle) - y * sin(angle)).toFloat(), (x * sin(angle) + y * cos(angle)).toFloat())
        val top = center.y - (abs(sin(angle)).toFloat() * width + abs(cos(angle)).toFloat() * height) / 2f - 6f
        val pillWidth = minOf(240f, maxWidth.value - 16f)
        XNoteLiquidGlassPanel(backdrop, shape = XNoteSmoothCornerShape(24.dp),
            modifier = Modifier.offset {
                IntOffset(((center.x - pillWidth / 2f).coerceIn(8f, maxWidth.value - pillWidth - 8f) * density).roundToInt(),
                    ((top - 52f).coerceAtLeast(80f) * density).roundToInt())
            }.width(pillWidth.dp).testTag("xnote-image-pill")) {
            Row(Modifier.fillMaxWidth()) {
                EditorSymbolButton("替换", stringResource(R.string.image_replace), Modifier.weight(1f).xNotePopupAnchor(replacementAnchor),
                    onClick = { session.replaceImageId = block.id })
                Spacer(Modifier.width(1.dp).height(20.dp).align(Alignment.CenterVertically)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)))
                listOf(Triple("↑", R.string.image_forward, ImageAction.Forward),
                    Triple("↓", R.string.image_backward, ImageAction.Backward),
                    Triple("⧉", R.string.image_duplicate, ImageAction.Duplicate),
                    Triple("↺", R.string.image_reset, ImageAction.Reset)).forEachIndexed { index, (symbol, label, action) ->
                    EditorSymbolButton(symbol, stringResource(label), Modifier.weight(1f),
                        iconRes = when (action) {
                            ImageAction.Forward -> R.drawable.ic_keyline_stroke_arrow_up
                            ImageAction.Backward -> R.drawable.ic_keyline_stroke_arrow_down
                            ImageAction.Duplicate -> R.drawable.ic_keyline_stroke_copy
                            else -> R.drawable.ic_keyline_stroke_rotate_ccw
                        }, onClick = { session.editImage(block.id, action) })
                    if (index == 1) Spacer(Modifier.width(1.dp).height(20.dp).align(Alignment.CenterVertically)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)))
                }
            }
        }
        val delete = corner(width / 2f + 6f, -height / 2f - 6f)
        XNoteLiquidGlassPanel(backdrop, shape = XNoteSmoothCornerShape(22.dp),
            modifier = Modifier.offset { IntOffset(((delete.x - 22f) * density).roundToInt(), ((delete.y - 22f) * density).roundToInt()) }
                .size(44.dp)) {
            EditorSymbolButton("×", stringResource(R.string.image_delete), Modifier.fillMaxWidth(), destructive = true,
                iconRes = R.drawable.ic_keyline_stroke_bin, onClick = { session.editImage(block.id, ImageAction.Delete) })
        }
        val handle = corner(width / 2f + 6f, height / 2f + 6f)
        var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val latestBlock by rememberUpdatedState(block)
        val latestPlacement by rememberUpdatedState(placement)
        Box(Modifier.offset { IntOffset(((handle.x - 22f) * density).roundToInt(), ((handle.y - 22f) * density).roundToInt()) }
            .size(44.dp).onGloballyPositioned { handleCoordinates = it }
            .testTag("xnote-image-transform-handle").semantics { contentDescription = handleDescription }
            .pointerInput(block.id) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val initial = latestBlock
                    val origin = latestPlacement.center
                    val start = (handleCoordinates?.localToRoot(down.position) ?: return@awaitEachGesture) - origin
                    down.consume()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val end = (handleCoordinates?.localToRoot(change.position) ?: break) - origin
                            val next = initial.transformFromHandle(start.x, start.y, end.x, end.y)
                            session.transformImage(block.id, next.scale, next.rotationDegrees, next.offsetX, next.offsetY)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        session.finishImageGesture()
                    }
                }
            }.clip(XNoteSmoothCornerShape(22.dp)).background(primary), contentAlignment = Alignment.Center) {
            Text("⤢", color = onPrimary)
        }
    }
}
