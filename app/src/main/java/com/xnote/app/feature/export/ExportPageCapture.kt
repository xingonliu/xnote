package com.xnote.app.feature.export

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import com.xnote.app.data.files.decodeNoteImage
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.feature.background.XNoteNoteSurface
import com.xnote.app.feature.reader.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// -- Functions

@Composable
internal fun ExportPageCapture(
    page: ReadingPage<ReadingContent>,
    library: NoteLibrary,
    background: BackgroundKey,
    width: Dp,
    height: Dp,
    margin: Dp,
    file: File,
    onComplete: () -> Unit,
    onError: () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val drawn = remember { CompletableDeferred<Unit>() }
    var media by remember { mutableStateOf<Map<String, ImageBitmap>?>(null) }
    LaunchedEffect(Unit) {
        try {
            val images = mutableMapOf<String, ImageBitmap>()
            page.units.mapNotNull { (it.content as? ReadingContent.Media)?.attachmentId }.distinct().forEach { id ->
                try {
                    val attachment = library.getAttachment(id) ?: error("Missing attachment")
                    images[id] = decodeNoteImage(library.attachmentFile(attachment)).asImageBitmap()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Use the same unreadable-media placeholder as the reader.
                }
            }
            media = images
            drawn.await()
            val bitmap = layer.toImageBitmap().asAndroidBitmap()
            try {
                withContext(Dispatchers.IO) {
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
            } finally {
                bitmap.recycle()
            }
            onComplete()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            onError()
        }
    }
    media?.let { loaded ->
        XNoteNoteSurface(background, Modifier.requiredSize(width, height).drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
            drawn.complete(Unit)
        }) {
            ReadingPageContent(page, library, Modifier.fillMaxSize().padding(margin), loadedMedia = loaded)
        }
    }
}
