package com.xnote.app.data.files

import java.io.File

// -- Type Definitions

class AttachmentFileStore(
    private val rootDirectory: File,
) {
    fun write(relativePath: String, bytes: ByteArray): File {
        val file = resolve(relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
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
