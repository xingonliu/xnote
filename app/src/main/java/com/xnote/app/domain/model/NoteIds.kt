package com.xnote.app.domain.model

import java.util.UUID

// -- Functions

fun newNoteId(): String = UUID.randomUUID().toString()
