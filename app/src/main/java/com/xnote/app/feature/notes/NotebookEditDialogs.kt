package com.xnote.app.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.*
import com.xnote.app.domain.model.Notebook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// -- Constants

private val notebookColors = linkedMapOf("gold" to "金色", "blue" to "蓝色", "purple" to "紫色", "green" to "绿色", "rose" to "玫瑰色")
private val notebookIcons = linkedMapOf("notebook" to "笔记", "star" to "星标", "inbox" to "收集箱")

// -- Functions

@Composable
internal fun notebookColor(key: String): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (key) {
        "blue" -> if (dark) Color(0xFF87B8FF) else Color(0xFF386DAB)
        "purple" -> if (dark) Color(0xFFC2A6F0) else Color(0xFF8054AD)
        "green" -> if (dark) Color(0xFF8DCCAC) else Color(0xFF377A5C)
        "rose" -> if (dark) Color(0xFFF0A3B6) else Color(0xFFA74F69)
        else -> if (dark) Color(0xFFE6C56D) else Color(0xFF936E20)
    }
}

internal fun notebookIcon(key: String): Int = when (key) {
    "star" -> R.drawable.ic_keyline_stroke_star
    "inbox" -> R.drawable.ic_keyline_stroke_inbox
    else -> R.drawable.ic_keyline_stroke_square_pen
}

@Composable
internal fun NotebookEditDialogs(notebook: Notebook?, action: String?, count: Int, library: NoteLibrary,
    backdrop: Backdrop, onDismiss: () -> Unit) {
    // -- State
    var name by rememberSaveable(notebook?.id, action) { mutableStateOf(notebook?.name.orEmpty()) }
    var color by rememberSaveable(notebook?.id, action) { mutableStateOf(notebook?.color ?: "gold") }
    var icon by rememberSaveable(notebook?.id, action) { mutableStateOf(notebook?.icon ?: "notebook") }
    var moveToUnfiled by rememberSaveable(notebook?.id, action) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember(notebook?.id, action) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // -- Functions
    fun dismiss() { if (!saving) onDismiss() }
    XNoteDialog(visible = notebook != null && action in listOf("rename", "appearance", "delete"),
        onDismissRequest = ::dismiss,
        title = when (action) { "rename" -> "重命名笔记本"; "appearance" -> "更换颜色/图标"; else -> "删除笔记本" },
        backdrop = backdrop,
        confirmAction = XNoteDialogAction(if (saving) "保存中…" else if (action == "delete") "删除笔记本" else "保存",
            enabled = !saving && (action != "rename" || name.isNotBlank()), destructive = action == "delete", onClick = {
                val id = notebook?.id
                if (id != null) scope.launch {
                    saving = true
                    error = false
                    try {
                        when (action) {
                            "rename" -> library.renameNotebook(id, name.trim())
                            "appearance" -> library.setNotebookAppearance(id, color, icon)
                            "delete" -> library.deleteNotebook(id, moveNotesToUnfiled = moveToUnfiled)
                        }
                        onDismiss()
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { error = true
                    } finally { saving = false }
                }
            }),
        dismissAction = XNoteDialogAction("取消", ::dismiss, enabled = !saving)) {
        when (action) {
            "rename" -> XNoteTextField(name, { name = it }, enabled = !saving, placeholder = "笔记本名称",
                modifier = Modifier.testTag("xnote-notebook-name"))
            "appearance" -> {
                Text("标记颜色", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    notebookColors.forEach { (key, label) ->
                        val accent = notebookColor(key)
                        Box(Modifier.size(48.dp).selectable(color == key, enabled = !saving, role = Role.RadioButton, onClick = { color = key })
                            .semantics { contentDescription = label }.testTag("xnote-notebook-color-$key"), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(32.dp).background(accent, XNoteSmoothCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                if (color == key) Icon(painterResource(R.drawable.ic_keyline_stroke_check), null, Modifier.size(20.dp), tint = if (accent.luminance() > 0.5f) Color.Black else Color.White)
                            }
                        }
                    }
                }
                Text("笔记本图标", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    notebookIcons.forEach { (key, label) ->
                        Column(Modifier.selectable(icon == key, enabled = !saving, role = Role.RadioButton, onClick = { icon = key })
                            .background(if (icon == key) notebookColor(color).copy(alpha = 0.16f) else Color.Transparent, XNoteSmoothCornerShape(12.dp))
                            .padding(12.dp).testTag("xnote-notebook-icon-$key"), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(notebookIcon(key)), null, Modifier.size(24.dp), tint = notebookColor(color))
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            "delete" -> {
                Text("“${notebook?.name.orEmpty()}”将被永久删除，笔记本本身无法恢复。", style = MaterialTheme.typography.bodyMedium)
                if (count > 0) {
                    Text("其中的 $count 篇笔记：", style = MaterialTheme.typography.titleSmall)
                    listOf(true to "移到未分类，保留笔记", false to "连同笔记移入最近删除").forEach { (move, label) ->
                        Row(Modifier.fillMaxWidth().selectable(moveToUnfiled == move, enabled = !saving, role = Role.RadioButton,
                            onClick = { moveToUnfiled = move }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = moveToUnfiled == move, onClick = null)
                            Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!moveToUnfiled) Text("笔记在最近删除中保留 30 天；恢复后归入未分类。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (error) Text("保存失败，请重试。", color = MaterialTheme.colorScheme.error)
    }
}
