package com.xnote.app.feature.notes

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNotePopup
import com.xnote.app.design.XNotePopupAnchor
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.Notebook

// -- Type Definitions

@Stable
class NotebookHomeUiState(selectedId: String? = null, action: String? = null, managing: Boolean = false) {
    // -- State
    var selectedId by mutableStateOf(selectedId)
    var action by mutableStateOf(action)
    var managing by mutableStateOf(managing)
    var menuAnchor by mutableStateOf<XNotePopupAnchor?>(null)
}

// -- Functions

@Composable
fun rememberNotebookHomeUiState(): NotebookHomeUiState = rememberSaveable(saver = listSaver(
    save = { listOf(it.selectedId.orEmpty(), it.action.orEmpty(), it.managing.toString()) },
    restore = { NotebookHomeUiState(it[0].ifEmpty { null }, it[1].ifEmpty { null }, it[2].toBoolean()) },
)) { NotebookHomeUiState() }

@Composable
internal fun BoxScope.NotebookHomeOverlays(ui: NotebookHomeUiState, notebooks: List<Notebook>, notes: List<Note>,
    library: NoteLibrary, backdrop: Backdrop) {
    val selected = notebooks.firstOrNull { it.id == ui.selectedId }
    XNotePopup(visible = ui.action == "menu" && selected != null, onDismissRequest = { ui.action = null },
        backdrop = backdrop, anchor = ui.menuAnchor) {
        NotebookMenuAction("重命名", R.drawable.ic_keyline_stroke_square_pen) { ui.action = "rename" }
        NotebookMenuAction("更换颜色/图标", R.drawable.ic_keyline_stroke_star) { ui.action = "appearance" }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NotebookMenuAction("删除笔记本", R.drawable.ic_keyline_stroke_bin, destructive = true) { ui.action = "delete" }
    }
    NotebookEditDialogs(selected, ui.action, notes.count { it.notebookId == ui.selectedId && !it.isTrashed }, library, backdrop,
        onDismiss = { ui.action = null })
    NotebookManager(visible = ui.managing, notebooks = notebooks, library = library, backdrop = backdrop,
        onDismiss = { ui.managing = false }, onManage = { id ->
            ui.selectedId = id
            ui.menuAnchor = null
            ui.managing = false
            ui.action = "menu"
        })
}
