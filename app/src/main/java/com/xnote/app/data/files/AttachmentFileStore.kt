package com.xnote.app.data.files

import java.io.File
import java.io.InputStream

// -- Type Definitions

class AttachmentFileStore(
    private val rootDirectory: File,
) {
    fun write(relativePath: String, input: InputStream): File {
        val file = resolve(relativePath)
        file.parentFile?.mkdirs()
        try {
            file.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        return file
    }

    fun delete(relativePath: String) {
        val file = resolve(relativePath)
        if (file.exists()) {
            file.delete()
        }
    }

    fun resolve(relativePath: String): File = File(rootDirectory, relativePath)

    companion object {
        const val DirectoryName = "attachments"

        fun relativePath(id: String, extension: String): String {
            val suffix = extension.trim().trimStart('.')
            return if (suffix.isEmpty()) {
                "$DirectoryName/$id"
            } else {
                "$DirectoryName/$id.$suffix"
            }
        }
    }
}
