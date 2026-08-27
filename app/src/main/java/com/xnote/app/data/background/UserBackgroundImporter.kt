package com.xnote.app.data.background

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.domain.model.AttachmentKind
import com.xnote.app.domain.model.BackgroundKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// -- Type Definitions

class UserBackgroundImporter(
    private val contentResolver: ContentResolver,
    private val library: NoteLibrary,
) {
    suspend fun import(uri: Uri): BackgroundKey.UserImage = withContext(Dispatchers.IO) {
        val mimeType = contentResolver.getType(uri)
            ?.takeIf { it.startsWith("image/") }
            ?: error("Selected content is not an image")
        val dimensions = readImageDimensions(uri)
        check(dimensions.first > 0 && dimensions.second > 0) {
            "Selected image cannot be decoded"
        }
        val input = contentResolver.openInputStream(uri)
            ?: error("Selected image cannot be opened")
        val attachment = input.use { stream ->
            library.putAttachment(
                kind = AttachmentKind.UserBackground,
                mimeType = mimeType,
                extension = safeExtension(mimeType),
                input = stream,
                originalFileName = displayName(uri),
                widthPx = dimensions.first,
                heightPx = dimensions.second,
            )
        }
        BackgroundKey.UserImage(attachment.id)
    }

    private fun readImageDimensions(uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        return options.outWidth to options.outHeight
    }

    private fun displayName(uri: Uri): String? {
        return contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use(::readDisplayName)
    }
}

// -- Functions

private fun readDisplayName(cursor: Cursor): String? {
    if (!cursor.moveToFirst()) return null
    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    return if (columnIndex < 0) null else cursor.getString(columnIndex)
}

private fun safeExtension(mimeType: String): String {
    return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        ?: "image"
}
