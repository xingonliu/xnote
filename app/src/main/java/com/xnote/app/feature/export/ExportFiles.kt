package com.xnote.app.feature.export

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// -- Type Definitions

class NoteExportFileProvider : FileProvider()

// -- Functions

fun createExportDirectory(context: Context): File {
    val root = File(context.cacheDir, "exports").apply { mkdirs() }
    // Shared files remain readable after leaving preview; expire only on a later export.
    root.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }
        ?.forEach { it.deleteRecursively() }
    return File(root, UUID.randomUUID().toString()).apply { check(mkdirs()) }
}

suspend fun saveExportToGallery(context: Context, files: List<File>): List<Uri> = withContext(Dispatchers.IO) {
    require(files.isNotEmpty())
    val resolver = context.contentResolver
    val inserted = mutableListOf<Uri>()
    try {
        files.forEach { file ->
            ensureActive()
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "XNote-${file.parentFile!!.name}-${file.name}")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/XNote")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            inserted += uri
            checkNotNull(resolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } }
        }
        ensureActive()
        inserted.forEach { uri ->
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
        }
        inserted.toList()
    } catch (error: Exception) {
        withContext(NonCancellable) { inserted.forEach { runCatching { resolver.delete(it, null, null) } } }
        throw error
    }
}

fun exportShareIntent(context: Context, files: List<File>): Intent {
    require(files.isNotEmpty())
    val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.exports", it) }
    return Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/png"
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        clipData = ClipData.newUri(context.contentResolver, "XNote", uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
