package com.xnote.app.feature.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.files.decodeNoteImage
import com.xnote.app.data.repository.NoteLibrary
import kotlinx.coroutines.CancellationException

// -- Functions

@Composable
fun ReadingPageContent(
    page: ReadingPage<ReadingContent>,
    library: NoteLibrary,
    modifier: Modifier = Modifier,
    loadedMedia: Map<String, ImageBitmap>? = null,
) {
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val quote = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    Column(modifier.testTag("xnote-reader-page")) {
        page.units.forEachIndexed { index, unit ->
            val content = unit.content
            val height = with(density) { unit.height.toDp() }
            if (content is ReadingContent.Media) {
                ReadingMedia(content, library, Modifier.fillMaxWidth().height(height), loadedMedia)
            } else {
                val lines = when (content) {
                    is ReadingContent.TextLine -> listOf(content)
                    is ReadingContent.TableLine -> content.cells.filterNotNull()
                }
                val accessibleText = lines.joinToString(" ") { line ->
                    line.layout.layoutInput.text.text.substring(line.layout.getLineStart(line.line), line.layout.getLineEnd(line.line))
                }
                Canvas(Modifier.fillMaxWidth().height(height).semantics { text = AnnotatedString(accessibleText) }
                    .pointerInput(content, uriHandler) {
                        detectTapGestures { position ->
                            val table = content as? ReadingContent.TableLine
                            val line = if (table != null) table.cells.getOrNull((position.x / table.columnWidth).toInt()) else lines.firstOrNull()
                            if (line != null) {
                                val offset = line.layout.getOffsetForPosition(Offset(position.x - line.x,
                                    line.layout.getLineTop(line.line) + 1f))
                                line.layout.layoutInput.text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                                    runCatching { uriHandler.openUri(it.item) }
                                }
                            }
                        }
                    }) {
                    val table = content as? ReadingContent.TableLine
                    val padding = if (table?.first == true) 8.dp.toPx() else 0f
                    for (line in lines) {
                        val lineHeight = line.layout.getLineBottom(line.line) - line.layout.getLineTop(line.line)
                        clipRect(line.x, padding, size.width, padding + lineHeight) {
                            drawText(line.layout, topLeft = Offset(line.x, padding - line.layout.getLineTop(line.line)))
                        }
                        if (line.quoted) drawLine(quote, Offset(line.x - 8.dp.toPx(), 0f), Offset(line.x - 8.dp.toPx(), lineHeight), 3.dp.toPx())
                    }
                    if (table != null) {
                        if (table.first || index == 0) drawLine(outline, Offset.Zero, Offset(size.width, 0f))
                        if (table.last || index == page.units.lastIndex) drawLine(outline, Offset(0f, size.height), Offset(size.width, size.height))
                        for (column in 0..table.cells.size) {
                            val x = column * table.columnWidth
                            drawLine(outline, Offset(x, 0f), Offset(x, size.height))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingMedia(media: ReadingContent.Media, library: NoteLibrary, modifier: Modifier, loadedMedia: Map<String, ImageBitmap>?) {
    var bitmap by remember(media.attachmentId, loadedMedia) { mutableStateOf(loadedMedia?.get(media.attachmentId)) }
    var failed by remember(media.attachmentId, loadedMedia) { mutableStateOf(loadedMedia != null && bitmap == null) }
    val density = LocalDensity.current
    LaunchedEffect(media.attachmentId, library) {
        if (loadedMedia != null) return@LaunchedEffect
        try {
            val attachment = library.getAttachment(media.attachmentId) ?: error("Missing attachment")
            bitmap = decodeNoteImage(library.attachmentFile(attachment)).asImageBitmap()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        }
    }
    Box(modifier.testTag("xnote-reader-media"), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image == null) {
            Text(stringResource(if (failed) R.string.reader_image_failed else R.string.image_loading), color = MaterialTheme.colorScheme.onSurface)
        } else {
            Image(image, stringResource(R.string.image_description),
                Modifier.size(with(density) { media.width.toDp() }, with(density) { media.height.toDp() })
                    .graphicsLayer {
                        rotationZ = media.rotation
                        translationX = media.x
                        translationY = media.y
                    })
        }
    }
}
