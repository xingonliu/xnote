package com.xnote.app.domain.model

// -- Type Definitions

data class BackgroundKey(val id: String) {
    init {
        require(id in BuiltinBackgroundIds) { "Unsupported built-in background: $id" }
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

fun BackgroundKey.encode(): String = "builtin:$id"

fun parseBackgroundKey(raw: String?): BackgroundKey? {
    if (raw?.startsWith("builtin:") != true) return null
    val id = raw.removePrefix("builtin:")
    return if (id in BuiltinBackgroundIds) BackgroundKey(id) else null
}

fun defaultBackgroundKey(): BackgroundKey = BackgroundKey(DefaultBuiltinBackgroundId)

fun resolveBackgroundKey(
    noteBackground: BackgroundKey?,
    defaultBackground: BackgroundKey,
): BackgroundKey = noteBackground ?: defaultBackground
