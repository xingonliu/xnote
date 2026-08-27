package com.xnote.app.domain.model

// -- Type Definitions

sealed interface BackgroundKey {
    data class Builtin(val id: String) : BackgroundKey
    data class UserImage(val attachmentId: String) : BackgroundKey
}

// -- Constants

const val DefaultBuiltinBackgroundId = "default"
const val CreamBuiltinBackgroundId = "cream"
const val RuledBuiltinBackgroundId = "ruled"
const val GridBuiltinBackgroundId = "grid"

val BuiltinBackgroundIds = setOf(
    DefaultBuiltinBackgroundId,
    CreamBuiltinBackgroundId,
    RuledBuiltinBackgroundId,
    GridBuiltinBackgroundId,
)

// -- Functions

fun BackgroundKey.encode(): String = when (this) {
    is BackgroundKey.Builtin -> "builtin:$id"
    is BackgroundKey.UserImage -> "attachment:$attachmentId"
}

fun parseBackgroundKey(raw: String?): BackgroundKey? {
    if (raw.isNullOrBlank()) return null
    return when {
        raw.startsWith("builtin:") -> {
            val id = raw.removePrefix("builtin:")
            if (id.isBlank()) null else BackgroundKey.Builtin(id)
        }
        raw.startsWith("attachment:") -> {
            val id = raw.removePrefix("attachment:")
            if (id.isBlank()) null else BackgroundKey.UserImage(id)
        }
        else -> null
    }
}

fun BackgroundKey.attachmentIdOrNull(): String? = when (this) {
    is BackgroundKey.Builtin -> null
    is BackgroundKey.UserImage -> attachmentId
}

fun defaultBackgroundKey(): BackgroundKey = BackgroundKey.Builtin(DefaultBuiltinBackgroundId)

fun BackgroundKey.isSupported(): Boolean = when (this) {
    is BackgroundKey.Builtin -> id in BuiltinBackgroundIds
    is BackgroundKey.UserImage -> attachmentId.isNotBlank()
}

fun resolveBackgroundKey(
    noteBackgroundKey: String?,
    defaultBackgroundKeyRaw: String?,
): BackgroundKey {
    val defaultKey = parseBackgroundKey(defaultBackgroundKeyRaw)
        ?.takeIf(BackgroundKey::isSupported)
        ?: defaultBackgroundKey()
    return parseBackgroundKey(noteBackgroundKey)
        ?.takeIf(BackgroundKey::isSupported)
        ?: defaultKey
}
