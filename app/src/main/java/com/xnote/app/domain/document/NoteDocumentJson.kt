package com.xnote.app.domain.document

import kotlinx.serialization.json.Json

// -- Constants

val NoteDocumentJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = false
}

// -- Functions

fun NoteDocument.encodeToJson(): String = NoteDocumentJson.encodeToString(NoteDocument.serializer(), this)

fun decodeNoteDocument(json: String): NoteDocument =
    NoteDocumentJson.decodeFromString(NoteDocument.serializer(), json)
