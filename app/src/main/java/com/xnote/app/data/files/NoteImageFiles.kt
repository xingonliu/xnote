package com.xnote.app.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.domain.model.Attachment
import com.xnote.app.domain.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// -- Type Definitions

class NoteCameraFileProvider : FileProvider()

// -- Functions

fun cameraFile(context: Context, name: String): File = File(File(context.cacheDir, "camera"), name)

fun cameraUri(context: Context, name: String): Uri = FileProvider.getUriForFile(
    context, "${context.packageName}.camera", cameraFile(context, name),
)

suspend fun importNoteImage(context: Context, library: NoteLibrary, uri: Uri, owner: String): Attachment =
    withContext(Dispatchers.IO) {
        // Decode into a bounded, orientation-correct still image before registering an attachment.
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val ratio = minOf(1.0, 2560.0 / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val temporary = File.createTempFile("image-", ".png", context.cacheDir)
        try {
            temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            temporary.inputStream().use {
                library.putAttachment(
                    kind = AttachmentKind.Image, mimeType = "image/png", extension = "png", input = it,
                    widthPx = bitmap.width, heightPx = bitmap.height, sessionOwner = owner,
                )
            }
        } finally {
            bitmap.recycle()
            temporary.delete()
        }
    }

suspend fun decodeNoteImage(file: File): Bitmap = withContext(Dispatchers.IO) {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val ratio = minOf(1.0, 1280.0 / maxOf(info.size.width, info.size.height))
        decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
    }
}
