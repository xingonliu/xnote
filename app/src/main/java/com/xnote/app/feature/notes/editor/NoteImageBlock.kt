package com.xnote.app.feature.notes.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xnote.app.R
import com.xnote.app.data.files.decodeNoteImage
import com.xnote.app.design.XNoteRadiusSmall
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.design.xNotePopupAnchor
import com.xnote.app.domain.document.EditorSelection
import com.xnote.app.domain.document.ImageBlock
import com.xnote.app.domain.document.block
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// -- Composables

@Composable
fun NoteImageBlock(block: ImageBlock, session: NoteEditorSession) {
    var bitmap by remember(block.attachmentId) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(block.attachmentId) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val selected = session.selection.blockId == block.id
    val transform = rememberTransformableState { _, zoom, pan, rotation ->
        val current = session.document.block(block.id) as? ImageBlock
        if (current != null) session.transformImage(
            block.id, current.scale * zoom, current.rotationDegrees + rotation,
            current.offsetX + pan.x / density.density, current.offsetY + pan.y / density.density,
        )
    }
    LaunchedEffect(transform.isTransformInProgress) {
        if (!transform.isTransformInProgress) session.finishImageGesture()
    }
    LaunchedEffect(block.attachmentId) {
        try {
            val file = session.imageFile(block.attachmentId) ?: error("Missing image")
            bitmap = decodeNoteImage(file).asImageBitmap()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().zIndex(block.zIndex.toFloat())
            .xNotePopupAnchor(session.imageAnchor(block.id))
            .testTag("xnote-image-${block.id}")
            .clickable {
                focus.clearFocus(force = true)
                keyboard?.hide()
                session.focusBlockId = null
                session.select(EditorSelection(block.id))
                session.imageMenuId = block.id
            }
            .then(if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, XNoteSmoothCornerShape(XNoteRadiusSmall)) else Modifier)
            .transformable(transform, enabled = selected),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image == null) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(if (failed) R.string.image_load_failed else R.string.image_loading))
            }
        } else {
            val radians = Math.toRadians(block.rotationDegrees.toDouble())
            val ratio = image.height.toFloat() / image.width
            val rotatedRatio = abs(cos(radians)).toFloat() + ratio * abs(sin(radians)).toFloat()
            val width = minOf(maxWidth.value * 0.7f * block.scale, maxWidth.value / rotatedRatio)
            val height = width * ratio
            val rotatedWidth = width * rotatedRatio
            val rotatedHeight = width * abs(sin(radians)).toFloat() + height * abs(cos(radians)).toFloat()
            val horizontalSpace = ((maxWidth.value - rotatedWidth) / 2f).coerceAtLeast(0f)
            val x = block.offsetX.coerceIn(-horizontalSpace, horizontalSpace)
            Box(Modifier.fillMaxWidth().height((rotatedHeight + abs(block.offsetY) * 2f).dp), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = image, contentDescription = stringResource(R.string.image_description),
                    modifier = Modifier.size(width.dp, height.dp).graphicsLayer {
                        rotationZ = block.rotationDegrees
                        translationX = x * density.density
                        translationY = block.offsetY * density.density
                    },
                )
            }
        }
    }
}
