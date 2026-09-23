package com.xnote.app.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.kyant.backdrop.Backdrop
import com.xnote.app.design.liquidglass.LiquidButton

// -- Type Definitions

data class XNoteSelectOption(val id: String, val label: String)

@Stable
class XNoteSelectState {
    // -- State and Variables

    var anchor by mutableStateOf<XNotePopupAnchor?>(null)
    var options by mutableStateOf<List<XNoteSelectOption>>(emptyList())
    var selectedId by mutableStateOf("")
    var query by mutableStateOf("")
    var onSelect: (String) -> Unit = {}

    // -- Functions

    fun dismiss() { anchor = null; query = ""; options = emptyList(); onSelect = {} }
}

// -- Functions

@Composable
fun rememberXNoteSelectState(): XNoteSelectState = remember { XNoteSelectState() }

@Composable
fun XNoteSelectField(
    label: String,
    selectedId: String,
    selectedLabel: String,
    options: List<XNoteSelectOption>,
    state: XNoteSelectState,
    backdrop: Backdrop,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val anchor = rememberXNotePopupAnchor()
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(XNoteSpacingSmall)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        LiquidButton({
            keyboard?.hide()
            state.options = options
            state.selectedId = selectedId
            state.query = ""
            state.onSelect = onSelect
            state.anchor = anchor
        }, backdrop, enabled = enabled && options.isNotEmpty(), modifier = modifier.fillMaxWidth().xNotePopupAnchor(anchor)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(selectedLabel.ifBlank { "请选择" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("⌄")
            }
        }
    }
}

@Composable
fun BoxScope.XNoteSelectMenu(state: XNoteSelectState, backdrop: Backdrop) {
    val keyboard = LocalSoftwareKeyboardController.current
    val filtered = state.options.filter { it.label.contains(state.query, true) || it.id.contains(state.query, true) }
    XNoteDropdownMenu(
        expanded = state.anchor != null,
        onDismissRequest = { keyboard?.hide(); state.dismiss() },
        items = filtered.map { option -> XNoteDropdownMenuItem(option.label, selected = option.id == state.selectedId,
            onClick = { state.onSelect(option.id) }) },
        backdrop = backdrop,
        anchor = state.anchor,
        placement = XNotePopupPlacement.BelowStart,
        modifier = Modifier.testTag("select-menu"),
        header = {
            XNoteTextField(state.query, { state.query = it }, Modifier.testTag("select-search"), placeholder = "搜索名称或 ID")
            if (filtered.isEmpty()) Text("没有匹配选项")
        },
    )
}
