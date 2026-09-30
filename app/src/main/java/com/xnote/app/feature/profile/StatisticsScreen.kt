package com.xnote.app.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.design.*
import com.xnote.app.domain.model.*
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

// -- Functions

@Composable
fun StatisticsScreen(notes: List<Note>, notebooks: List<Notebook>, onBack: () -> Unit, onOpenNote: (String) -> Unit) {
    val stats = remember(notes) { libraryStatistics(notes) }
    var explanation by remember { mutableStateOf(false) }
    XNoteSettingsPage("统计", onBack, Modifier.testTag("xnote-statistics"),
        actions = listOf(XNoteHeaderAction(R.drawable.ic_keyline_stroke_more_horizontal, "统计说明", { explanation = true })),
        overlay = { glass ->
            XNoteDialog(explanation, { explanation = false }, "统计说明", glass,
                XNoteDialogAction("知道了", { explanation = false })) {
                Text("统计笔记正文和表格中的文字，不含标题与已删除的笔记。图片、贴纸和画板分别按使用它们的笔记篇数与内容个数统计。")
            }
        }) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatisticHighlight("笔记", stats.noteCount, "篇", Modifier.weight(1f))
                StatisticHighlight("正文字符", stats.characterCount, "字", Modifier.weight(1f))
            }
        }
        item {
            XNoteSettingsSection("文字") {
                XNoteSettingsRow("英文单词", value = "${number(stats.latinWordCount)} 个")
            }
        }
        item {
            XNoteSettingsSection("各笔记本文字量") {
                val rows = notebooks.map { it.name to (stats.notebookCharacters[it.id] ?: 0L) } +
                    ("未分类" to (stats.notebookCharacters[null] ?: 0L))
                rows.forEachIndexed { index, (title, count) ->
                    if (index > 0) XNoteInsetDivider()
                    XNoteSettingsRow(title, value = "${number(count)} 字")
                }
            }
        }
        item {
            XNoteSettingsSection("使用这些内容的笔记") {
                listOf("表格" to stats.tableNoteCount, "图片" to stats.imageNoteCount,
                    "贴纸" to stats.stickerNoteCount, "画板" to stats.drawingNoteCount).forEachIndexed { index, (label, count) ->
                    if (index > 0) XNoteInsetDivider()
                    XNoteSettingsRow(label, value = "${number(count)} 篇")
                }
            }
        }
        item {
            XNoteSettingsSection("内容个数") {
                listOf("图片" to stats.imageCount, "贴纸" to stats.stickerCount, "画板" to stats.drawingCount)
                    .forEachIndexed { index, (label, count) ->
                        if (index > 0) XNoteInsetDivider()
                        XNoteSettingsRow(label, value = "${number(count)} 个")
                    }
            }
        }
        item { RecentNotes("最近创建", stats.recentlyCreated, true, onOpenNote) }
        item { RecentNotes("最近修改", stats.recentlyUpdated, false, onOpenNote) }
    }
}

@Composable
private fun StatisticHighlight(title: String, value: Number, unit: String, modifier: Modifier) {
    XNoteGroupCard(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(number(value), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecentNotes(title: String, notes: List<Note>, created: Boolean, onOpenNote: (String) -> Unit) {
    XNoteSettingsSection(title) {
        if (notes.isEmpty()) XNoteSettingsRow("暂无笔记")
        notes.forEachIndexed { index, note ->
            if (index > 0) XNoteInsetDivider()
            XNoteSettingsRow(note.title.ifBlank { "未命名笔记" },
                summary = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(if (created) note.createdAtEpochMs else note.updatedAtEpochMs)),
                onClick = { onOpenNote(note.id) })
        }
    }
}

private fun number(value: Number): String = NumberFormat.getIntegerInstance().format(value)
