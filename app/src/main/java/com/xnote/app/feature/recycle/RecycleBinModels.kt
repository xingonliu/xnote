package com.xnote.app.feature.recycle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// -- Type Definitions

class RecycleBinUiState {
    var actionNoteId by mutableStateOf<String?>(null)
    var actionAnchor by mutableStateOf<com.xnote.app.design.XNotePopupAnchor?>(null)
    var selectionMode by mutableStateOf(false)
    var selectedIds by mutableStateOf(emptySet<String>())
    var moreVisible by mutableStateOf(false)
    var pendingPermanentDeleteIds by mutableStateOf(emptySet<String>())
    var emptyTrashConfirmVisible by mutableStateOf(false)

    fun finishSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }
}
