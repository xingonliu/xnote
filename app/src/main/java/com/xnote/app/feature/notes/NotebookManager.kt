package com.xnote.app.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.*
import com.xnote.app.domain.model.Notebook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// -- Functions

@Composable
internal fun NotebookManager(visible: Boolean, notebooks: List<Notebook>, library: NoteLibrary, backdrop: Backdrop,
    onDismiss: () -> Unit, onManage: (String) -> Unit) {
    if (!visible) return

    // -- State
    var ordered by remember { mutableStateOf(notebooks.map { it.id }) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 48.dp.toPx() }

    // -- Derived Values
    val byId = notebooks.associateBy { it.id }
    val currentIds = ordered.filter { it in byId } + notebooks.map { it.id }.filter { it !in ordered }
    val latestIds by rememberUpdatedState(currentIds)

    // -- Functions
    fun move(id: String, to: Int) {
        val ids = latestIds.toMutableList()
        val from = ids.indexOf(id)
        if (from >= 0 && to in ids.indices && from != to) {
            ids.add(to, ids.removeAt(from))
            ordered = ids
        }
    }
    fun moveAtPointer() {
        val id = draggingId ?: return
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            dragY >= it.offset && dragY < it.offset + it.size && it.key != id
        } ?: return
        move(id, latestIds.indexOf(target.key))
    }
    fun save(afterSave: () -> Unit) {
        scope.launch {
            saving = true
            error = false
            try {
                library.reorderNotebooks(latestIds)
                afterSave()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { error = true
            } finally { saving = false }
        }
    }

    XNoteDialog(visible = true, onDismissRequest = { if (!saving) onDismiss() }, title = "管理笔记本", backdrop = backdrop,
        confirmAction = XNoteDialogAction(if (saving) "保存中…" else "完成", { save(onDismiss) }, enabled = !saving && draggingId == null),
        dismissAction = XNoteDialogAction("取消", onDismiss, enabled = !saving)) {
        Text("拖动右侧手柄排序，点按更多管理笔记本。", style = MaterialTheme.typography.bodyMedium)
        if (currentIds.isEmpty()) Text("暂无笔记本，请先创建笔记本。")
        LazyColumn(state = listState, userScrollEnabled = draggingId == null,
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).testTag("xnote-notebook-reorder")) {
            items(currentIds, key = { it }) { id ->
                val book = byId.getValue(id)
                val index = currentIds.indexOf(id)
                Row(Modifier.fillMaxWidth().zIndex(if (draggingId == id) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (draggingId == id) {
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                            if (item == null) 0f else dragY - item.offset - item.size / 2f
                        } else 0f
                        shadowElevation = if (draggingId == id) 8.dp.toPx() else 0f
                    }
                    .background(MaterialTheme.colorScheme.surface, XNoteSmoothCornerShape(12.dp))
                    .heightIn(min = 72.dp).padding(horizontal = 8.dp)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("上移") { if (!saving && index > 0) { move(id, index - 1); true } else false },
                            CustomAccessibilityAction("下移") { if (!saving && index < currentIds.lastIndex) { move(id, index + 1); true } else false })
                    }.testTag("xnote-reorder-row-$id"), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(notebookIcon(book.icon)), null, Modifier.size(24.dp), tint = notebookColor(book.color))
                    Text(book.name, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { save { onManage(id) } }, enabled = !saving && draggingId == null) {
                        Icon(painterResource(R.drawable.ic_keyline_stroke_more_horizontal), "管理 ${book.name}", Modifier.size(20.dp))
                    }
                    Box(Modifier.size(48.dp).testTag("xnote-reorder-handle-$id")
                        .pointerInput(id, saving) {
                            if (!saving) detectDragGestures(
                                onDragStart = {
                                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                                    if (item != null) {
                                        draggingId = id
                                        dragY = item.offset + item.size / 2f
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                onDragEnd = { draggingId = null },
                                onDragCancel = { draggingId = null },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragY += amount.y
                                    moveAtPointer()
                                })
                        }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_keyline_stroke_grip_vertical), "拖动排序 ${book.name}", Modifier.size(24.dp))
                    }
                }
            }
        }
        if (error) Text("排序保存失败，请重试。", color = MaterialTheme.colorScheme.error)
    }

    // -- Lifecycle Hooks
    LaunchedEffect(draggingId) {
        while (draggingId != null) {
            val info = listState.layoutInfo
            val speed = when {
                dragY < info.viewportStartOffset + edge -> -edge / 5f
                dragY > info.viewportEndOffset - edge -> edge / 5f
                else -> 0f
            }
            if (speed != 0f) {
                listState.scrollBy(speed)
                moveAtPointer()
            }
            delay(16)
        }
    }
}
