package com.xnote.app.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.domain.model.Attachment
import com.xnote.app.domain.model.AttachmentKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// -- Functions

suspend fun decodeEditableMedia(file: File): Bitmap = withContext(Dispatchers.IO) {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val ratio = minOf(1.0, 1280.0 / maxOf(info.size.width, info.size.height))
        decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
    }
}

suspend fun saveMediaBitmap(context: Context, library: NoteLibrary, bitmap: Bitmap, kind: AttachmentKind, owner: String): Attachment =
    withContext(Dispatchers.IO) {
        val temporary = File.createTempFile("creative-", ".png", context.cacheDir)
        try {
            temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            temporary.inputStream().use {
                library.putAttachment(kind, "image/png", "png", it, widthPx = bitmap.width, heightPx = bitmap.height, sessionOwner = owner)
            }
        } finally {
            temporary.delete()
        }
    }

fun applyCutoutMask(source: Bitmap, alpha: IntArray): Bitmap {
    require(alpha.size == source.width * source.height)
    val pixels = IntArray(alpha.size)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    for (index in pixels.indices) {
        val opacity = (pixels[index] ushr 24) * alpha[index].coerceIn(0, 255) / 255
        pixels[index] = (pixels[index] and 0x00ffffff) or (opacity shl 24)
    }
    return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
}

fun paintMask(alpha: IntArray, width: Int, height: Int, startX: Float, startY: Float, endX: Float, endY: Float, radius: Float, value: Int) {
    val distance = kotlin.math.hypot(endX - startX, endY - startY)
    val steps = kotlin.math.ceil(distance / maxOf(1f, radius / 2f)).toInt().coerceAtLeast(1)
    for (step in 0..steps) {
        val cx = startX + (endX - startX) * step / steps
        val cy = startY + (endY - startY) * step / steps
        val left = (cx - radius).toInt().coerceAtLeast(0)
        val right = (cx + radius).toInt().coerceAtMost(width - 1)
        val top = (cy - radius).toInt().coerceAtLeast(0)
        val bottom = (cy + radius).toInt().coerceAtMost(height - 1)
        for (y in top..bottom) for (x in left..right) {
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius) alpha[y * width + x] = value
        }
    }
}
