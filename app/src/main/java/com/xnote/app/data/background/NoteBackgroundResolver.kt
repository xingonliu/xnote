package com.xnote.app.data.background

import android.graphics.BitmapFactory
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.domain.model.AttachmentKind
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.defaultBackgroundKey
import com.xnote.app.domain.model.isSupported
import com.xnote.app.domain.model.parseBackgroundKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// -- Type Definitions

sealed interface ResolvedNoteBackground {
    val key: BackgroundKey

    data class Builtin(
        override val key: BackgroundKey.Builtin,
    ) : ResolvedNoteBackground

    data class UserImage(
        override val key: BackgroundKey.UserImage,
        val file: File,
        val widthPx: Int,
        val heightPx: Int,
    ) : ResolvedNoteBackground
}

data class NoteBackgroundResolution(
    val background: ResolvedNoteBackground,
    val requestedKey: BackgroundKey,
    val fellBack: Boolean,
)

class NoteBackgroundResolver(
    private val library: NoteLibrary,
) {
    suspend fun resolve(
        noteBackgroundKey: String?,
        defaultBackgroundKeyRaw: String?,
    ): NoteBackgroundResolution = withContext(Dispatchers.IO) {
        val parsedDefault = parseBackgroundKey(defaultBackgroundKeyRaw)
        val resolvedDefault = resolveCandidate(parsedDefault)
        val fallback = resolvedDefault
            ?: defaultResolvedBackground()
        val parsedNote = parseBackgroundKey(noteBackgroundKey)
        val requested = if (noteBackgroundKey == null) {
            parsedDefault ?: defaultBackgroundKey()
        } else {
            parsedNote ?: defaultBackgroundKey()
        }
        val selected = if (noteBackgroundKey == null) {
            resolvedDefault
        } else {
            resolveCandidate(parsedNote)
        }
        NoteBackgroundResolution(
            background = selected ?: fallback,
            requestedKey = requested,
            fellBack = selected == null &&
                (noteBackgroundKey != null || defaultBackgroundKeyRaw != null),
        )
    }

    private suspend fun resolveCandidate(key: BackgroundKey?): ResolvedNoteBackground? {
        return when (key) {
            null -> null
            is BackgroundKey.Builtin -> {
                if (key.isSupported()) ResolvedNoteBackground.Builtin(key) else null
            }
            is BackgroundKey.UserImage -> {
                val attachment = library.getAttachment(key.attachmentId)
                    ?.takeIf { it.kind == AttachmentKind.UserBackground }
                    ?: return null
                val file = library.attachmentFile(attachment)
                if (!file.isFile || !canDecode(file)) return null
                ResolvedNoteBackground.UserImage(
                    key = key,
                    file = file,
                    widthPx = attachment.widthPx ?: 0,
                    heightPx = attachment.heightPx ?: 0,
                )
            }
        }
    }
}

// -- Functions

fun defaultResolvedBackground(): ResolvedNoteBackground.Builtin {
    return ResolvedNoteBackground.Builtin(
        BackgroundKey.Builtin(com.xnote.app.domain.model.DefaultBuiltinBackgroundId),
    )
}

private fun canDecode(file: File): Boolean {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return options.outWidth > 0 && options.outHeight > 0
}
