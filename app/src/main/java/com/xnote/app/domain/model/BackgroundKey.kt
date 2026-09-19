package com.xnote.app.domain.model

// -- Type Definitions

sealed interface BackgroundKey {
    data class Builtin(val id: String) : BackgroundKey {
        init { require(id in BuiltinBackgroundIds) { "Unsupported built-in background: $id" } }
    }

    data class Image(val attachmentId: String, val maskOpacity: Int = 72) : BackgroundKey {
        init {
            require(attachmentId.isNotBlank()) { "Missing background attachment ID" }
            require(maskOpacity in 0..100) { "Background mask opacity must be between 0 and 100" }
        }
    }
}

// -- Constants

const val DefaultBuiltinBackgroundId = "default"
const val CreamBuiltinBackgroundId = "cream"
const val RuledBuiltinBackgroundId = "ruled"
const val GridBuiltinBackgroundId = "grid"

private val BuiltinBackgroundIds = setOf(
    DefaultBuiltinBackgroundId,
    CreamBuiltinBackgroundId,
    RuledBuiltinBackgroundId,
    GridBuiltinBackgroundId,
)

// -- Functions

fun BackgroundKey.encode(): String = when (this) {
    is BackgroundKey.Builtin -> "builtin:$id"
    is BackgroundKey.Image -> "image:$maskOpacity:$attachmentId"
}

fun parseBackgroundKey(raw: String?): BackgroundKey? {
    val id = raw?.substringAfter(':', "") ?: return null
    return when {
        raw.startsWith("builtin:") && id in BuiltinBackgroundIds -> BackgroundKey.Builtin(id)
        raw.startsWith("image:") -> {
            val opacity = id.substringBefore(':').toIntOrNull()
            val attachmentId = id.substringAfter(':', "")
            if (opacity != null && opacity in 0..100 && attachmentId.isNotBlank()) {
                BackgroundKey.Image(attachmentId, opacity)
            } else null
        }
        else -> null
    }
}

fun defaultBackgroundKey(): BackgroundKey = BackgroundKey.Builtin(DefaultBuiltinBackgroundId)

fun resolveBackgroundKey(
    noteBackground: BackgroundKey?,
    defaultBackground: BackgroundKey,
): BackgroundKey = noteBackground ?: defaultBackground
