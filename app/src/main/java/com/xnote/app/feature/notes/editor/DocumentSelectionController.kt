package com.xnote.app.feature.notes.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xnote.app.domain.document.*
import kotlin.math.roundToInt

// -- Type Definitions

internal data class SelectionGeometry(
    val bounds: Rect,
    val offsetAt: (Offset) -> Int,
    val cursorAt: (Int) -> Rect,
)

internal class DocumentSelectionController(val session: NoteEditorSession, private val context: Context) {
    // -- State and Variables

    val fields = mutableStateMapOf<TextAddress, SelectionGeometry>()
    var paper by mutableStateOf<LayoutCoordinates?>(null)
    var viewport by mutableStateOf(Rect.Zero)
    var dragging by mutableStateOf<Boolean?>(null)
    var dragPosition by mutableStateOf(Offset.Zero)
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    // -- Derived Values

    val selection get() = session.selection
    val active get() = !selection.isCollapsed

    // -- Functions

    fun range(address: TextAddress): TextRange? = session.document.selectionParts(selection)
        .firstOrNull { it.anchor().address == address }?.let { TextRange(it.start, it.end) }

    fun cursor(position: TextPosition): Offset? {
        val addresses = session.document.textAddresses()
        val requestedIndex = addresses.indexOf(position.address)
        val address = if (position.address in fields) position.address else {
            // Folded text remains part of select-all; place its handle at the nearest visible boundary.
            fields.keys.filter { it in addresses }.minByOrNull { kotlin.math.abs(addresses.indexOf(it) - requestedIndex) }
                ?: return null
        }
        val geometry = fields[address] ?: return null
        val offset = if (address == position.address) position.offset else if (addresses.indexOf(address) < requestedIndex)
            session.document.inlinesAt(address).plainText().length else 0
        val rect = geometry.cursorAt(offset)
        return geometry.bounds.topLeft + Offset(rect.left, rect.bottom)
    }

    fun selectAll() {
        session.document.selectAllText()?.let { session.select(it) }
        prepareInput()
    }

    fun prepareInput() {
        val first = session.document.selectionParts(selection).firstOrNull() ?: return
        session.focusBlockId = first.blockId
    }

    fun copy(cut: Boolean = false) {
        clipboard.setPrimaryClip(ClipData.newPlainText("", session.document.selectedText(selection)))
        if (cut) session.replaceSelection("")
        else collapseToFocus()
    }

    fun collapseToFocus() {
        val position = selection.focus()
        session.select(selectionBetween(position, position))
        session.focusBlockId = position.address.blockId
    }

    fun paste() {
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: return
        session.replaceSelection(text)
    }

    fun moveHandle(start: Boolean, root: Offset) {
        val entry = fields.entries.minByOrNull { (_, geometry) ->
            val bounds = geometry.bounds
            val dx = (bounds.left - root.x).coerceAtLeast(0f) + (root.x - bounds.right).coerceAtLeast(0f)
            val dy = (bounds.top - root.y).coerceAtLeast(0f) + (root.y - bounds.bottom).coerceAtLeast(0f)
            dx * dx + dy * dy
        } ?: return
        val position = TextPosition(entry.key, entry.value.offsetAt(root - entry.value.bounds.topLeft))
        session.select(if (start) selectionBetween(position, selection.focus()) else selectionBetween(selection.anchor(), position))
    }

    fun key(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (session.focusBlockId == null) return false
        if (event.isCtrlPressed || event.isMetaPressed) return when (event.key) {
            Key.A -> { selectAll(); true }
            Key.C -> if (active) { copy(); true } else false
            Key.X -> if (active) { copy(true); true } else false
            Key.V -> if (active) { paste(); true } else false
            else -> false
        }
        if (active && (event.key == Key.Backspace || event.key == Key.Delete)) {
            session.replaceSelection("")
            return true
        }
        if (active && event.key == Key.Escape) {
            collapseToFocus()
            return true
        }
        return false
    }
}

// -- Constants

internal val LocalDocumentSelection = staticCompositionLocalOf<DocumentSelectionController?> { null }

// -- Functions

@Composable
internal fun rememberDocumentSelection(session: NoteEditorSession): DocumentSelectionController {
    val context = LocalContext.current
    return remember(session) { DocumentSelectionController(session, context) }
}

@Composable
internal fun DocumentSelectionOverlay(controller: DocumentSelectionController, scroll: ScrollState) {
    val density = LocalDensity.current
    val anchor = controller.cursor(controller.selection.anchor())
    val focus = controller.cursor(controller.selection.focus())
    val origin = controller.paper?.takeIf { it.isAttached }?.localToRoot(Offset.Zero) ?: Offset.Zero
    val active = controller.active
    BackHandler(active) {
        controller.collapseToFocus()
    }

    LaunchedEffect(controller.dragging) {
        while (controller.dragging != null) {
            withFrameNanos { }
            val point = controller.dragPosition
            val edge = with(density) { 48.dp.toPx() }
            val viewport = controller.viewport
            val delta = when {
                point.y < viewport.top + edge -> -12f
                point.y > viewport.bottom - edge -> 12f
                else -> 0f
            }
            if (delta != 0f) {
                scroll.scrollBy(delta)
                controller.moveHandle(controller.dragging == true, point)
            }
        }
    }
    if (!active && controller.dragging == null) return
    if (anchor != null) SelectionHandle(controller, true, anchor - origin)
    if (focus != null) SelectionHandle(controller, false, focus - origin)
    if (active && controller.dragging == null && (anchor != null || focus != null)) {
        val point = anchor ?: requireNotNull(focus)
        val top = maxOf(point.y - with(density) { 56.dp.toPx() }, controller.viewport.top) - origin.y
        Surface(Modifier.offset { IntOffset(0, top.roundToInt().coerceAtLeast(0)) }
            .testTag("xnote-selection-menu"), shape = MaterialTheme.shapes.small, shadowElevation = 4.dp) {
            Row {
                SelectionAction("复制", "copy") { controller.copy() }
                SelectionAction("剪切", "cut") { controller.copy(true) }
                SelectionAction("粘贴", "paste") { controller.paste() }
                SelectionAction("全选", "all") { controller.selectAll() }
            }
        }
    }
}

@Composable
private fun SelectionAction(label: String, tag: String, action: () -> Unit) {
    Text(label, Modifier.clickable(interactionSource = null, indication = null, onClick = action)
        .padding(12.dp).testTag("xnote-selection-$tag"), style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun SelectionHandle(controller: DocumentSelectionController, start: Boolean, position: Offset) {
    val radius = with(LocalDensity.current) { 12.dp.toPx() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(Modifier.offset { IntOffset((position.x - radius).roundToInt(), position.y.roundToInt()) }
        .size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
        .testTag(if (start) "xnote-selection-start" else "xnote-selection-end")
        .semantics { contentDescription = if (start) "选区起点" else "选区终点" }
        .onGloballyPositioned { coordinates = it }
        .pointerInput(controller, start) {
            detectDragGestures(
                onDragStart = { point ->
                    controller.dragPosition = (coordinates?.localToRoot(point) ?: Offset.Zero) - Offset(0f, radius)
                    controller.dragging = start
                },
                onDragEnd = { controller.dragging = null; controller.prepareInput() },
                onDragCancel = { controller.dragging = null; controller.prepareInput() },
            ) { change, amount ->
                change.consume()
                controller.dragPosition += amount
                controller.moveHandle(start, controller.dragPosition)
            }
        })
}
