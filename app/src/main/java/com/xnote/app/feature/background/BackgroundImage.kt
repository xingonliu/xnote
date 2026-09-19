package com.xnote.app.feature.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.xnote.app.data.files.decodeNoteImage
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.domain.model.BackgroundKey
import kotlinx.coroutines.CancellationException

// -- Functions

suspend fun loadBackgroundImage(background: BackgroundKey, library: NoteLibrary): ImageBitmap? {
    if (background !is BackgroundKey.Image) return null
    return try {
        val attachment = library.getAttachment(background.attachmentId) ?: return null
        decodeNoteImage(library.attachmentFile(attachment)).asImageBitmap()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // Unreadable attachments fall back to the theme's plain paper.
        null
    }
}

@Composable
fun rememberBackgroundImage(background: BackgroundKey, library: NoteLibrary): ImageBitmap? {
    val attachmentId = (background as? BackgroundKey.Image)?.attachmentId
    var image by remember(attachmentId, library) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(attachmentId, library) { image = loadBackgroundImage(background, library) }
    return image
}
