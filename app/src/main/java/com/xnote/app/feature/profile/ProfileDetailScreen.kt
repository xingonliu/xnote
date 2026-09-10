package com.xnote.app.feature.profile

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.data.files.LocalStorage
import com.xnote.app.data.files.LocalStorageUsage
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// -- Functions

@Composable
fun ProfileDetailScreen(page: String, notes: List<Note>, notebooks: List<Notebook>, onBack: () -> Unit, onOpenNote: (String) -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val edges = setOf(XNoteScrollEdge.Top, XNoteScrollEdge.Bottom)
    BackHandler(onBack = onBack)
    XNotePageScaffold(backdrop = backdrop, scrollEdges = edges, alwaysVisibleScrollEdges = edges, content = {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val padding = PaddingValues(start = 24.dp, end = 24.dp, top = insets.calculateTopPadding() + XNoteHeaderHeight + 16.dp,
            bottom = insets.calculateBottomPadding() + 24.dp)
        when (page) {
            "统计" -> StatisticsContent(notes, notebooks, padding, onOpenNote)
            "存储与隐私" -> StorageContent(padding, backdrop)
        }
    }, overlay = {
        XNoteHeader(page, backdrop, onBack = onBack, modifier = Modifier.align(Alignment.TopCenter))
    })
}

@Composable
private fun StatisticsContent(notes: List<Note>, notebooks: List<Notebook>, padding: PaddingValues, onOpenNote: (String) -> Unit) {
    val stats = remember(notes) { libraryStatistics(notes) }
    LazyColumn(Modifier.fillMaxSize().testTag("xnote-statistics"), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { StatGroup("全部笔记", listOf("笔记总数" to stats.noteCount, "可见字符" to stats.characterCount, "英文单词" to stats.latinWordCount)) }
        item { Text("仅统计正常笔记正文与表格，不含标题、附件文件名和回收站。", style = MaterialTheme.typography.bodyMedium) }
        item { StatGroup("各笔记本文字量", notebooks.map { it.name to (stats.notebookCharacters[it.id] ?: 0L) } +
            ("未归档" to (stats.notebookCharacters[null] ?: 0L))) }
        item { StatGroup("包含内容的笔记数", listOf("表格" to stats.tableNoteCount, "图片" to stats.imageNoteCount,
            "贴纸" to stats.stickerNoteCount, "画笔" to stats.drawingNoteCount)) }
        item { StatGroup("内容数量", listOf("图片" to stats.imageCount, "贴纸" to stats.stickerCount, "画笔" to stats.drawingCount)) }
        item { RecentNotes("最近创建", stats.recentlyCreated, true, onOpenNote) }
        item { RecentNotes("最近修改", stats.recentlyUpdated, false, onOpenNote) }
    }
}

@Composable
private fun StatGroup(title: String, values: List<Pair<String, Number>>) {
    XNoteGroupCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            values.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, Modifier.weight(1f))
                    Text(value.toString())
                }
            }
        }
    }
}

@Composable
private fun RecentNotes(title: String, notes: List<Note>, created: Boolean, onOpenNote: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (notes.isEmpty()) Text("暂无笔记")
        notes.forEach { note ->
            Column(Modifier.fillMaxWidth().clickable { onOpenNote(note.id) }.padding(vertical = 12.dp)) {
                Text(note.title.ifBlank { "未命名笔记" })
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(if (created) note.createdAtEpochMs else note.updatedAtEpochMs)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StorageContent(padding: PaddingValues, backdrop: com.kyant.backdrop.Backdrop) {
    val context = LocalContext.current
    val storage = remember(context) { LocalStorage(context) }
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<LocalStorageUsage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun refresh(clear: Boolean) { scope.launch {
        busy = true
        try {
            usage = if (clear) storage.clearCache() else storage.usage()
            message = if (clear) "缓存清理完成" else null
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { message = "无法读取或清理，请重试" }
        finally { busy = false }
    } }
    LaunchedEffect(storage) { refresh(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("xnote-storage"), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("本地存储", style = MaterialTheme.typography.titleMedium) }
        item {
            usage?.let { value ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("笔记数据库（含历史与回收站）：${Formatter.formatFileSize(context, value.databaseBytes)}")
                    Text("附件：${Formatter.formatFileSize(context, value.attachmentBytes)}")
                    Text("缓存总量：${Formatter.formatFileSize(context, value.cacheBytes)}")
                    Text("可清理缓存：${Formatter.formatFileSize(context, value.clearableBytes)}")
                }
            } ?: Text(if (busy) "正在计算…" else "尚未读取占用")
        }
        item { Text("清理超过 24 小时的导出和相机临时文件。保留近期分享文件、笔记、回收站、附件和设置。", style = MaterialTheme.typography.bodyMedium) }
        item { LiquidButton({ refresh(true) }, backdrop, enabled = !busy && (usage?.clearableBytes ?: 0) > 0) { Text("清理缓存") } }
        item { LiquidButton({ refresh(false) }, backdrop, enabled = !busy) { Text("重新计算") } }
        message?.let { item { Text(it) } }
        item { Text("笔记与附件存储于本机应用私有目录。模型与 Linux 环境尚未接入，未下载相关内容。", style = MaterialTheme.typography.bodyMedium) }
    }
}
