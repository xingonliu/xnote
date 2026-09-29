package com.xnote.app.feature.notes.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xnote.app.R
import com.xnote.app.data.files.decodeNoteImage
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.domain.document.*
import kotlinx.coroutines.CancellationException
import kotlin.math.*

// -- Functions

@Composable
fun NoteImageBlock(
    block: PlacedMediaBlock,
    session: NoteEditorSession,
    originY: Float,
    modifier: Modifier = Modifier,
    onBottomChanged: (Float) -> Unit,
) {
    var bitmap by remember(block.attachmentId) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(block.attachmentId) { mutableStateOf(false) }
    var bodyCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current.density
    val selected = session.selection.blockId == block.id
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    fun select() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        session.focusBlockId = null
        session.select(EditorSelection(block.id))
    }
    LaunchedEffect(block.attachmentId) {
        try {
            val file = session.imageFile(block.attachmentId) ?: error("Missing image")
            bitmap = decodeNoteImage(file).asImageBitmap()
            bitmap?.let { session.mediaRatios[block.attachmentId] = it.height.toFloat() / it.width }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        }
    }
    BoxWithConstraints(modifier.zIndex(1f + block.zIndex.toFloat())) {
        val ratio = bitmap?.let { it.height.toFloat() / it.width } ?: 0.65f
        val geometry = block.geometry(maxWidth.value, ratio)
        val width = geometry.width
        val height = geometry.height
        val displayHeight = geometry.boundsHeight
        val center = Offset(geometry.centerX, geometry.centerY)
        SideEffect {
            onBottomChanged(originY + center.y + displayHeight / 2f + 44f)
        }
        fun updatePlacement() {
            val coordinates = bodyCoordinates ?: return
            if (selected && coordinates.isAttached) session.imagePlacement = ImagePlacement(
                block.id, coordinates.localToRoot(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)),
                coordinates.size.width.toFloat(), coordinates.size.height.toFloat(), block.rotationDegrees)
        }
        SideEffect { updatePlacement() }
        val bodyModifier = Modifier.offset { IntOffset(((center.x - width / 2f) * density).roundToInt(),
            ((center.y - height / 2f) * density).roundToInt()) }
            .wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(width.dp, height.dp).graphicsLayer { rotationZ = block.rotationDegrees }
            .onGloballyPositioned { bodyCoordinates = it; updatePlacement() }
            .testTag("xnote-image-${block.id}")
            .pointerInput(block.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    select()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val current = session.document.block(block.id) as? PlacedMediaBlock ?: break
                            val pan = event.calculatePan()
                            val radians = Math.toRadians(current.rotationDegrees.toDouble())
                            val screenPan = Offset((pan.x * cos(radians) - pan.y * sin(radians)).toFloat(),
                                (pan.x * sin(radians) + pan.y * cos(radians)).toFloat()) / density
                            val zoom = event.calculateZoom()
                            val rotation = event.calculateRotation()
                            if (screenPan != Offset.Zero || zoom != 1f || rotation != 0f) {
                                session.transformMedia(block.id, current.scale * zoom, current.rotationDegrees + rotation,
                                    current.offsetX + screenPan.x, current.offsetY + screenPan.y)
                            }
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        session.finishMediaGesture()
                    }
                }
            }
            .drawWithContent {
                drawContent()
                if (selected) {
                    val gap = 6.dp.toPx()
                    drawRoundRect(borderColor, topLeft = Offset(-gap, -gap),
                        size = Size(size.width + gap * 2, size.height + gap * 2),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                }
            }
        // Keep the image and its flow height in the same layout so removing siblings
        // cannot separate the rendered image from its text anchor.
        Box(Modifier.fillMaxWidth().height(displayHeight.dp)) {
            Box(bodyModifier) {
                val image = bitmap
                if (image == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (failed) R.string.image_load_failed else R.string.image_loading))
                    }
                } else {
                    Image(image, stringResource(R.string.image_description),
                        Modifier.fillMaxSize().then(if (block is ImageBlock) Modifier.clip(XNoteSmoothCornerShape(12.dp)) else Modifier))
                }
            }
        }
    }
}
