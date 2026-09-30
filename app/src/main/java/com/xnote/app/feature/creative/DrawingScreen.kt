package com.xnote.app.feature.creative

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.files.renderDrawing
import com.xnote.app.data.files.saveMediaBitmap
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidSlider
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
    // -- State and Variables

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalXNoteToast.current
    val blockId = remember { initial?.id ?: newNoteId() }
    var strokes by remember { mutableStateOf(initial?.strokes.orEmpty()) }
    var live by remember { mutableStateOf<DrawingStroke?>(null) }
    val undo = remember { mutableStateListOf<List<DrawingStroke>>() }
    val redo = remember { mutableStateListOf<List<DrawingStroke>>() }
    var color by remember { mutableLongStateOf(0xff202020) }
    var width by remember { mutableFloatStateOf(6f) }
    var erase by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var brushSettings by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val latestStrokes by rememberUpdatedState(strokes)

    // -- Derived Values

    val dirty = strokes != initial?.strokes.orEmpty()
    val toolsEnabled = !busy && live == null

    // -- Functions

    fun back() {
        if (busy) return
        if (dirty || live != null) discard = true else onBack()
    }
    fun commit(next: List<DrawingStroke>) {
        undo.add(strokes)
        strokes = next
        redo.clear()
    }
    fun saveDrawing() {
        if (busy || live != null) return
        busy = true
        scope.launch {
            try {
                val savedStrokes = strokes
                val bitmap = withContext(Dispatchers.Default) { renderDrawing(savedStrokes) }
                val attachment = try { saveMediaBitmap(context, library, bitmap, AttachmentKind.Drawing, owner) } finally { bitmap.recycle() }
                onSave(DrawingBlock(blockId, attachment.id, DrawingWidth.toFloat(), DrawingHeight.toFloat(), savedStrokes))
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { toast.show("画板未保存，请重试") }
            finally { busy = false }
        }
    }

    CreativePage("画板", ::back, actions = listOf(
        XNoteHeaderAction(R.drawable.ic_keyline_stroke_bin, "清空画板", { commit(emptyList()) }, enabled = strokes.isNotEmpty() && toolsEnabled),
        XNoteHeaderAction(R.drawable.ic_keyline_stroke_check, "完成", ::saveDrawing,
            enabled = toolsEnabled && (initial != null || strokes.isNotEmpty())),
    ), toolbar = {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CreativeModeButton("画笔", !erase, toolsEnabled) { erase = false }
            CreativeModeButton("橡皮", erase, toolsEnabled) { erase = true }
            CreativeIconButton("笔触粗细", R.drawable.ic_keyline_stroke_paintbrush, toolsEnabled) { brushSettings = true }
            CreativeIconButton("撤销", R.drawable.ic_keyline_stroke_arrow_u_turn_left, undo.isNotEmpty() && toolsEnabled) {
                redo.add(strokes); strokes = undo.removeAt(undo.lastIndex)
            }
            CreativeIconButton("重做", R.drawable.ic_keyline_stroke_arrow_u_turn_right, redo.isNotEmpty() && toolsEnabled) {
                undo.add(strokes); strokes = redo.removeAt(redo.lastIndex)
            }
        }
        if (!erase) DrawingColorPicker(color, toolsEnabled) { color = it }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }, overlay = { glass ->
        XNoteDialog(discard, { discard = false }, "放弃这次绘画？", glass,
            XNoteDialogAction("放弃", onBack, destructive = true),
            dismissAction = XNoteDialogAction("继续编辑", { discard = false })) {
            Text("尚未保存的改动会丢失。")
        }
        XNoteDialog(brushSettings, { brushSettings = false }, "笔触粗细", glass,
            XNoteDialogAction("完成", { brushSettings = false })) {
            val ink = if (erase) MaterialTheme.colorScheme.onSurface else Color(color)
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                drawLine(ink, androidx.compose.ui.geometry.Offset(size.width * .2f, size.height / 2),
                    androidx.compose.ui.geometry.Offset(size.width * .8f, size.height / 2), width.dp.toPx(), StrokeCap.Round)
            }
            LiquidSlider(value = { width }, onValueChange = { width = it }, valueRange = 2f..32f,
                visibilityThreshold = .1f, backdrop = glass, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("drawing-brush-size"))
        }
    }) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    }
}
