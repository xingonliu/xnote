package com.xnote.app.feature.creative

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xnote.app.data.files.renderDrawing
import com.xnote.app.data.files.saveMediaBitmap
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteButton
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.AttachmentKind
import com.xnote.app.domain.model.newNoteId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// -- Functions

@Composable
fun DrawingScreen(library: NoteLibrary, owner: String, initial: DrawingBlock?, onBack: () -> Unit, onSave: suspend (DrawingBlock) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = com.xnote.app.design.LocalXNoteToast.current
    val blockId = remember { initial?.id ?: newNoteId() }
    var strokes by remember { mutableStateOf(initial?.strokes.orEmpty()) }
    var live by remember { mutableStateOf<DrawingStroke?>(null) }
    val undo = remember { mutableStateListOf<List<DrawingStroke>>() }
    val redo = remember { mutableStateListOf<List<DrawingStroke>>() }
    var color by remember { mutableLongStateOf(0xff202020) }
    var width by remember { mutableFloatStateOf(6f) }
    var erase by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val latestStrokes by rememberUpdatedState(strokes)
    fun commit(next: List<DrawingStroke>) {
        undo.add(strokes)
        strokes = next
        redo.clear()
    }
    fun saveDrawing() {
        busy = true
        scope.launch {
            try {
                val savedStrokes = strokes
                val bitmap = withContext(Dispatchers.Default) { renderDrawing(savedStrokes) }
                val attachment = try { saveMediaBitmap(context, library, bitmap, AttachmentKind.Drawing, owner) } finally { bitmap.recycle() }
                onSave(DrawingBlock(blockId, attachment.id, DrawingWidth.toFloat(), DrawingHeight.toFloat(), savedStrokes))
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { toast.show("画板保存失败，请重试") }
            finally { busy = false }
        }
    }
    CreativePage("画板", onBack, actions = listOf(com.xnote.app.design.XNoteHeaderAction(
        com.xnote.app.R.drawable.ic_keyline_stroke_check, "完成", ::saveDrawing, enabled = !busy && live == null,
    ))) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            val ratio = DrawingWidth.toFloat() / DrawingHeight
            val canvasWidth = minOf(maxWidth, maxHeight * ratio)
            Box(Modifier.width(canvasWidth).height(canvasWidth / ratio)) {
                TransparencyGrid(Modifier.matchParentSize())
                Canvas(Modifier.fillMaxSize().testTag("xnote-drawing-canvas")
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .pointerInput(color, width, erase, busy) {
                        if (busy) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            fun point(position: androidx.compose.ui.geometry.Offset) = DrawingPoint(
                                (position.x / size.width).coerceIn(0f, 1f), (position.y / size.height).coerceIn(0f, 1f))
                            live = DrawingStroke(listOf(point(down.position)), color, width, erase)
                            down.consume()
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    live = live?.copy(points = live!!.points + point(change.position))
                                    change.consume()
                                } while (event.changes.any { it.id == down.id && it.pressed })
                                live?.let { commit(latestStrokes + it) }
                            } finally { live = null }
                        }
                    }) {
                    (strokes + listOfNotNull(live)).forEach { stroke ->
                        val points = stroke.points
                        if (points.isEmpty()) return@forEach
                        val path = Path().apply {
                            moveTo(points.first().x * size.width, points.first().y * size.height)
                            points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                        }
                        val brushWidth = stroke.width * size.width / DrawingWidth
                        val blend = if (stroke.erase) BlendMode.Clear else BlendMode.SrcOver
                        if (points.size == 1) drawCircle(Color(stroke.color), brushWidth / 2f,
                            androidx.compose.ui.geometry.Offset(points[0].x * size.width, points[0].y * size.height), blendMode = blend)
                        else drawPath(path, Color(stroke.color), style = Stroke(brushWidth, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
                    }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XNoteButton({ erase = false }, enabled = !busy) { Text(if (!erase) "✓ 笔" else "笔") }
            XNoteButton({ erase = true }, enabled = !busy) { Text(if (erase) "✓ 橡皮" else "橡皮") }
            listOf(3f, 6f, 14f, 28f).forEach { value -> XNoteButton({ width = value }, enabled = !busy) { Text("${if (width == value) "✓ " else ""}${value.toInt()}") } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("黑" to 0xff202020L, "蓝" to 0xff1769e0L, "红" to 0xffdb3030L, "绿" to 0xff268248L, "黄" to 0xffe1aa00L, "白" to 0xffffffffL).forEach { (name, value) ->
                XNoteButton({ color = value; erase = false }, enabled = !busy) { Text("${if (color == value) "✓ " else ""}$name") }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XNoteButton({ redo.add(strokes); strokes = undo.removeAt(undo.lastIndex) }, enabled = undo.isNotEmpty() && !busy) { Text("撤销") }
            XNoteButton({ undo.add(strokes); strokes = redo.removeAt(redo.lastIndex) }, enabled = redo.isNotEmpty() && !busy) { Text("重做") }
            XNoteButton({ commit(emptyList()) }, enabled = strokes.isNotEmpty() && !busy) { Text("清空") }
            XNoteButton(onBack, enabled = !busy) { Text("取消") }

        }
    }
}
