package com.xnote.app.feature.creative

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.files.renderDrawing
import com.xnote.app.data.files.saveMediaBitmap
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.LocalXNoteToast
import com.xnote.app.design.XNoteButton
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteHeaderAction
import com.xnote.app.design.XNoteSegmentedControl
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.domain.document.DrawingBlock
import com.xnote.app.domain.document.DrawingHeight
import com.xnote.app.domain.document.DrawingPoint
import com.xnote.app.domain.document.DrawingStroke
import com.xnote.app.domain.document.DrawingWidth
import com.xnote.app.domain.model.AttachmentKind
import com.xnote.app.domain.model.newNoteId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// -- Functions

@Composable
fun DrawingScreen(
    library: NoteLibrary,
    owner: String,
    initial: DrawingBlock?,
    onBack: () -> Unit,
    onSave: suspend (DrawingBlock) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalXNoteToast.current
    val blockId = remember { initial?.id ?: newNoteId() }
    var strokes by remember { mutableStateOf(initial?.strokes.orEmpty()) }
    var live by remember { mutableStateOf<DrawingStroke?>(null) }
    val undo = remember { mutableStateListOf<List<DrawingStroke>>() }
    val redo = remember { mutableStateListOf<List<DrawingStroke>>() }
    var color by remember { mutableLongStateOf(0xff202020L) }
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
                val attachment = try {
                    saveMediaBitmap(context, library, bitmap, AttachmentKind.Drawing, owner)
                } finally {
                    bitmap.recycle()
                }
                onSave(DrawingBlock(blockId, attachment.id, DrawingWidth.toFloat(), DrawingHeight.toFloat(), savedStrokes))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                toast.show("画板保存失败，请重试")
            } finally {
                busy = false
            }
        }
    }

    CreativePage(
        title = "画板",
        onBack = onBack,
        actions = listOf(
            XNoteHeaderAction(
                iconRes = R.drawable.ic_keyline_stroke_check,
                contentDescription = "完成",
                onClick = ::saveDrawing,
                enabled = !busy && live == null,
            ),
        ),
    ) {
        // -- Canvas Area
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val ratio = DrawingWidth.toFloat() / DrawingHeight
            val canvasWidth = minOf(maxWidth, maxHeight * ratio)
            Box(
                Modifier
                    .width(canvasWidth)
                    .height(canvasWidth / ratio)
                    .clip(XNoteSmoothCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), XNoteSmoothCornerShape(12.dp)),
            ) {
                TransparencyGrid(Modifier.matchParentSize())
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .testTag("xnote-drawing-canvas")
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .pointerInput(color, width, erase, busy) {
                            if (busy) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun point(position: Offset) = DrawingPoint(
                                    (position.x / size.width).coerceIn(0f, 1f),
                                    (position.y / size.height).coerceIn(0f, 1f),
                                )
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
                                } finally {
                                    live = null
                                }
                            }
                        },
                ) {
                    (strokes + listOfNotNull(live)).forEach { stroke ->
                        val points = stroke.points
                        if (points.isEmpty()) return@forEach
                        val path = Path().apply {
                            moveTo(points.first().x * size.width, points.first().y * size.height)
                            points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                        }
                        val brushWidth = stroke.width * size.width / DrawingWidth
                        val blend = if (stroke.erase) BlendMode.Clear else BlendMode.SrcOver
                        if (points.size == 1) {
                            drawCircle(
                                color = Color(stroke.color),
                                radius = brushWidth / 2f,
                                center = Offset(points[0].x * size.width, points[0].y * size.height),
                                blendMode = blend,
                            )
                        } else {
                            drawPath(
                                path = path,
                                color = Color(stroke.color),
                                style = Stroke(brushWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                blendMode = blend,
                            )
                        }
                    }
                }
            }
        }

        // -- Floating Tool Dock
        XNoteGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Row 1: Tool Selection & Stroke Width
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Pen / Eraser Capsule
                    XNoteSegmentedControl(
                        items = listOf(false, true),
                        selectedItem = erase,
                        onItemSelected = { erase = it },
                        label = { isEraser -> if (isEraser) "橡皮" else "画笔" },
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                    )

                    // Stroke Width Indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(3f to 4.dp, 6f to 7.dp, 14f to 11.dp, 28f to 16.dp).forEach { (valWidth, dotSize) ->
                            val selected = width == valWidth
                            val dotShape = CircleShape
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(dotShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent,
                                    )
                                    .border(
                                        width = if (selected) 1.5.dp else 0.5.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        shape = dotShape,
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = !busy,
                                    ) { width = valWidth },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(dotSize)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            CircleShape,
                                        ),
                                )
                            }
                        }
                    }
                }

                // Row 2: Color Palette (when in pen mode)
                if (!erase) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(
                            0xff202020L to "黑",
                            0xff1769e0L to "蓝",
                            0xffdb3030L to "红",
                            0xff268248L to "绿",
                            0xffe1aa00L to "黄",
                            0xffffffffL to "白",
                        ).forEach { (colorValue, name) ->
                            val isSelected = color == colorValue
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = !busy,
                                    ) { color = colorValue },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 34.dp else 26.dp)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                            shape = CircleShape,
                                        )
                                        .padding(if (isSelected) 3.dp else 0.dp)
                                        .background(Color(colorValue), CircleShape)
                                        .border(
                                            width = if (colorValue == 0xffffffffL) 0.5.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                            shape = CircleShape,
                                        ),
                                )
                            }
                        }
                    }
                }

                // Row 3: Action Buttons (Undo, Redo, Clear, Cancel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XNoteButton(
                        onClick = { redo.add(strokes); strokes = undo.removeAt(undo.lastIndex) },
                        enabled = undo.isNotEmpty() && !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_keyline_stroke_arrow_u_turn_left), null, Modifier.size(16.dp))
                            Text("撤销")
                        }
                    }
                    XNoteButton(
                        onClick = { undo.add(strokes); strokes = redo.removeAt(redo.lastIndex) },
                        enabled = redo.isNotEmpty() && !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_keyline_stroke_arrow_u_turn_right), null, Modifier.size(16.dp))
                            Text("重做")
                        }
                    }
                    XNoteButton(
                        onClick = { commit(emptyList()) },
                        enabled = strokes.isNotEmpty() && !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("清空")
                    }
                    XNoteButton(
                        onClick = onBack,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消")
                    }
                }
            }
        }
    }
}
