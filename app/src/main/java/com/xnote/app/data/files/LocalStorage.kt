package com.xnote.app.data.files

import android.content.Context
import com.xnote.app.data.db.XNoteDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// -- Type Definitions

data class LocalStorageUsage(val databaseBytes: Long, val attachmentBytes: Long, val cacheBytes: Long, val clearableBytes: Long)

class LocalStorage(context: Context) {
    // -- State
    private val appContext = context.applicationContext

    // -- Functions
    suspend fun usage(): LocalStorageUsage = withContext(Dispatchers.IO) {
        val cache = cacheFiles()
        val database = appContext.getDatabasePath(XNoteDatabase.FileName)
        LocalStorageUsage(
            listOf("", "-wal", "-shm", "-journal").sumOf { File(database.path + it).length() },
            size(File(appContext.filesDir, AttachmentFileStore.DirectoryName)),
            cache.sumOf { it.length() },
            cache.filter { clearable(it, System.currentTimeMillis()) }.sumOf { it.length() },
        )
    }

    suspend fun clearCache(): LocalStorageUsage = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cacheFiles().filter { clearable(it, now) }.forEach { file ->
            check(file.delete() || !file.exists()) { "缓存文件无法删除" }
        }
        usage()
    }

    private fun cacheFiles(): List<File> = appContext.cacheDir.walkTopDown().filter { it.isFile }.toList()

    private fun clearable(file: File, now: Long): Boolean {
        val root = appContext.cacheDir.canonicalFile
        val path = file.canonicalFile
        if (!path.toPath().startsWith(root.toPath())) return false
        val directory = path.relativeTo(root).invariantSeparatorsPath.substringBefore('/')
        // Camera results and export URIs may still be in use by another activity.
        return directory in setOf("exports", "camera") && now - file.lastModified() > 86_400_000L
    }

    private fun size(directory: File): Long = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
