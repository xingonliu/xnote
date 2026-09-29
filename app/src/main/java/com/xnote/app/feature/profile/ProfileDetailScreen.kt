package com.xnote.app.feature.profile

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.data.agent.AgentNoteMemoryStore
import com.xnote.app.data.files.LocalStorage
import com.xnote.app.data.files.LocalStorageUsage
import com.xnote.app.design.XNoteButton
import com.xnote.app.design.XNoteDialog
import com.xnote.app.design.XNoteDialogAction
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteHeader
import com.xnote.app.design.XNoteHeaderHeight
import com.xnote.app.design.XNoteInsetDivider
import com.xnote.app.design.XNotePageScaffold
import com.xnote.app.design.XNoteScrollEdge
import com.xnote.app.design.xNoteScrollEdgePadding
import com.xnote.app.domain.model.Note
import com.xnote.app.domain.model.Notebook
import com.xnote.app.domain.model.libraryStatistics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// -- Functions

@Composable
fun ProfileDetailScreen(
    page: String,
    notes: List<Note>,
    notebooks: List<Notebook>,
    noteMemory: AgentNoteMemoryStore?,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val edges = setOf(XNoteScrollEdge.Top)
    BackHandler(onBack = onBack)
    XNotePageScaffold(
        backdrop = backdrop,
        scrollEdges = edges,
        alwaysVisibleScrollEdges = edges,
        content = {
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            val padding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = xNoteScrollEdgePadding(insets.calculateTopPadding() + XNoteHeaderHeight),
                bottom = insets.calculateBottomPadding() + 24.dp,
            )
            when (page) {
                "统计" -> StatisticsContent(notes, notebooks, padding, onOpenNote)
                "存储与隐私" -> StorageContent(padding, backdrop, noteMemory)
            }
        },
        overlay = {
            XNoteHeader(page, backdrop, onBack = onBack, modifier = Modifier.align(Alignment.TopCenter))
        },
    )
}

@Composable
private fun StatisticsContent(
    notes: List<Note>,
    notebooks: List<Notebook>,
    padding: PaddingValues,
    onOpenNote: (String) -> Unit,
) {
    val stats = remember(notes) { libraryStatistics(notes) }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("xnote-statistics"),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StatGroup(
                title = "全部笔记",
                values = listOf(
                    "笔记总数" to stats.noteCount,
                    "可见字符" to stats.characterCount,
                    "英文单词" to stats.latinWordCount,
                ),
            )
        }
        item {
            Text(
                "仅统计正常笔记正文与表格，不含标题、附件文件名和回收站。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        item {
            StatGroup(
                title = "各笔记本文字量",
                values = notebooks.map { it.name to (stats.notebookCharacters[it.id] ?: 0L) } +
                    ("未归档" to (stats.notebookCharacters[null] ?: 0L)),
            )
        }
        item {
            StatGroup(
                title = "包含内容的笔记数",
                values = listOf(
                    "表格" to stats.tableNoteCount,
                    "图片" to stats.imageNoteCount,
                    "贴纸" to stats.stickerNoteCount,
                    "画笔" to stats.drawingNoteCount,
                ),
            )
        }
        item {
            StatGroup(
                title = "内容数量",
                values = listOf(
                    "图片" to stats.imageCount,
                    "贴纸" to stats.stickerCount,
                    "画笔" to stats.drawingCount,
                ),
            )
        }
        item {
            RecentNotes("最近创建", stats.recentlyCreated, true, onOpenNote)
        }
        item {
            RecentNotes("最近修改", stats.recentlyUpdated, false, onOpenNote)
        }
    }
}

@Composable
private fun StatGroup(title: String, values: List<Pair<String, Number>>) {
    XNoteGroupCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            values.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    XNoteInsetDivider(startIndent = 0.dp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(value.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RecentNotes(title: String, notes: List<Note>, created: Boolean, onOpenNote: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
        XNoteGroupCard(Modifier.fillMaxWidth()) {
            if (notes.isEmpty()) {
                Text(
                    "暂无笔记",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                notes.forEachIndexed { index, note ->
                    if (index > 0) {
                        XNoteInsetDivider(startIndent = 16.dp)
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenNote(note.id) }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(note.title.ifBlank { "未命名笔记" }, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(
                                Date(if (created) note.createdAtEpochMs else note.updatedAtEpochMs),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageContent(
    padding: PaddingValues,
    backdrop: Backdrop,
    noteMemory: AgentNoteMemoryStore?,
) {
    val context = LocalContext.current
    val storage = remember(context) { LocalStorage(context) }
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<LocalStorageUsage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val memories by (noteMemory?.entries ?: flowOf(emptyList())).collectAsState(emptyList())
    var clearingMemory by remember { mutableStateOf(false) }

    fun refresh(clear: Boolean) {
        scope.launch {
            busy = true
            try {
                usage = if (clear) storage.clearCache() else storage.usage()
                message = if (clear) "缓存清理完成" else null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "无法读取或清理，请重试"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(storage) { refresh(false) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .testTag("xnote-storage"),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("本地存储", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            XNoteGroupCard(Modifier.fillMaxWidth()) {
                val value = usage
                if (value != null) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StorageRow("笔记数据库（含历史与回收站）", Formatter.formatFileSize(context, value.databaseBytes))
                        XNoteInsetDivider(startIndent = 0.dp)
                        StorageRow("附件", Formatter.formatFileSize(context, value.attachmentBytes))
                        XNoteInsetDivider(startIndent = 0.dp)
                        StorageRow("抠图模型", Formatter.formatFileSize(context, value.cutoutModelBytes))
                        XNoteInsetDivider(startIndent = 0.dp)
                        StorageRow("其中聊天文件", Formatter.formatFileSize(context, value.chatAttachmentBytes), "删除聊天后回收无引用文件")
                        XNoteInsetDivider(startIndent = 0.dp)
                        StorageRow("缓存总量", Formatter.formatFileSize(context, value.cacheBytes))
                        XNoteInsetDivider(startIndent = 0.dp)
                        StorageRow("可清理缓存", Formatter.formatFileSize(context, value.clearableBytes))
                    }
                } else {
                    Text(
                        if (busy) "正在计算…" else "尚未读取占用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        item {
            Text(
                "清理超过 24 小时的导出和相机临时文件。保留近期分享文件、笔记、回收站、附件和设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                XNoteButton(
                    onClick = { refresh(true) },
                    enabled = !busy && (usage?.clearableBytes ?: 0) > 0,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("清理缓存")
                }
                XNoteButton(
                    onClick = { refresh(false) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("重新计算")
                }
            }
        }
        message?.let { text ->
            item {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        item {
            Text("笔记记忆索引", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            XNoteGroupCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val totalMemoryBytes = memories.sumOf { (it.title + it.plainText + it.summaryJson).toByteArray(Charsets.UTF_8).size.toLong() }
                    StorageRow("索引笔记", "${memories.size} 篇")
                    XNoteInsetDivider(startIndent = 0.dp)
                    StorageRow("内容估算", Formatter.formatFileSize(context, totalMemoryBytes))
                    XNoteInsetDivider(startIndent = 0.dp)
                    val pendingCount = memories.count { it.status in setOf("failed", "blocked") }
                    StorageRow("未完成摘要", "$pendingCount 个")
                }
            }
        }
        item {
            Text(
                "清除后保留笔记和聊天，后续后台任务按当前权限重建；生成摘要可能产生模型服务费用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        item {
            XNoteButton(
                onClick = { clearingMemory = true },
                enabled = memories.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清除笔记记忆索引")
            }
        }
        item {
            Text(
                "笔记、聊天与附件存储于本机应用私有目录。发送给模型的资料遵守当前权限。Linux 环境尚未接入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }

    XNoteDialog(
        visible = clearingMemory,
        onDismissRequest = { clearingMemory = false },
        title = "清除笔记记忆索引？",
        backdrop = backdrop,
        confirmAction = XNoteDialogAction(
            label = "清除",
            destructive = true,
            onClick = {
                scope.launch {
                    try {
                        noteMemory?.clear()
                        clearingMemory = false
                        message = "笔记记忆索引已清除"
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        message = "清除失败，请重试"
                    }
                }
            },
        ),
        dismissAction = XNoteDialogAction("取消", { clearingMemory = false }),
    ) {
        Text("保留原笔记；正在生成的旧摘要不会重新写回。")
    }
}

@Composable
private fun StorageRow(label: String, value: String, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
