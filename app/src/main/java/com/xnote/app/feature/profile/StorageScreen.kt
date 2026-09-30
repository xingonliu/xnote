package com.xnote.app.feature.profile

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.agent.AgentNoteMemoryStore
import com.xnote.app.data.files.LocalStorage
import com.xnote.app.data.files.LocalStorageUsage
import com.xnote.app.design.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

// -- Functions

@Composable
fun StorageScreen(noteMemory: AgentNoteMemoryStore?, onBack: () -> Unit) {
    // -- State and Variables

    val context = LocalContext.current
    val storage = remember(context) { LocalStorage(context) }
    val scope = rememberCoroutineScope()
    val toast = LocalXNoteToast.current
    val memories by (noteMemory?.entries ?: flowOf(emptyList())).collectAsState(emptyList())
    var usage by remember { mutableStateOf<LocalStorageUsage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var clearingMemory by remember { mutableStateOf(false) }
    var memoryBusy by remember { mutableStateOf(false) }

    // -- Functions

    fun size(bytes: Long) = Formatter.formatFileSize(context, bytes)
    fun refresh(clear: Boolean = false) {
        scope.launch {
            busy = true
            failed = false
            try {
                usage = if (clear) storage.clearCache() else storage.usage()
                if (clear) toast.show("缓存已清理")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; toast.show(if (clear) "清理失败，请重试" else "无法读取存储用量，请重试") }
            finally { busy = false }
        }
    }

    // -- Lifecycle Hooks

    LaunchedEffect(storage) { refresh() }
    XNoteSettingsPage("存储与隐私", onBack, Modifier.testTag("xnote-storage"),
        actions = listOf(XNoteHeaderAction(R.drawable.ic_keyline_stroke_refresh_cw, "重新计算", { refresh() }, enabled = !busy)),
        overlay = { glass ->
            XNoteDialog(clearingMemory, { if (!memoryBusy) clearingMemory = false }, "清除笔记记忆？", glass,
                XNoteDialogAction("清除", {
                    scope.launch {
                        memoryBusy = true
                        try {
                            noteMemory?.clear()
                            clearingMemory = false
                            toast.show("笔记记忆已清除")
                            refresh()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { toast.show("清除失败，请重试") }
                        finally { memoryBusy = false }
                    }
                }, enabled = !memoryBusy, destructive = true),
                dismissAction = XNoteDialogAction("取消", { clearingMemory = false }, enabled = !memoryBusy)) {
                Text("原笔记和聊天记录会保留。Agent 再次整理笔记时可能产生模型服务费用。")
                if (memoryBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }) {
        item {
            XNoteSettingsSection("本地存储") {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已用空间", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(usage?.let { size(it.databaseBytes + it.attachmentBytes + it.cutoutModelBytes + it.cacheBytes) }
                        ?: if (failed) "暂不可用" else "正在计算…", style = MaterialTheme.typography.headlineMedium)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                usage?.let { value ->
                    XNoteInsetDivider()
                    XNoteSettingsRow("笔记与聊天", value = size(value.databaseBytes))
                    XNoteInsetDivider()
                    XNoteSettingsRow("附件", value = size(value.attachmentBytes),
                        summary = "聊天文件 ${size(value.chatAttachmentBytes)}")
                    XNoteInsetDivider()
                    XNoteSettingsRow("抠图资源", value = size(value.cutoutModelBytes))
                    XNoteInsetDivider()
                    XNoteSettingsRow("缓存", value = size(value.cacheBytes))
                }
                if (failed) XNoteSettingsRow("重新读取", showsDisclosure = false, enabled = !busy, onClick = { refresh() })
            }
        }
        item {
            XNoteSettingsSection("清理空间", description = "清理临时文件，保留笔记、附件和近期分享的图片。") {
                XNoteSettingsRow("可清理缓存", value = usage?.let { size(it.clearableBytes) } ?: "—")
                XNoteInsetDivider()
                XNoteSettingsRow(if (busy) "正在处理…" else "清理缓存", showsDisclosure = false,
                    enabled = !busy && (usage?.clearableBytes ?: 0) > 0, onClick = { refresh(true) })
            }
        }
        if (noteMemory != null) item {
            XNoteSettingsSection("Agent 笔记记忆", description = "帮助 Agent 查找和理解你的笔记。清除不会删除原笔记。") {
                XNoteSettingsRow("已整理的笔记", value = "${memories.size} 篇")
                XNoteInsetDivider()
                XNoteSettingsRow("清除笔记记忆", destructive = true, enabled = memories.isNotEmpty() && !memoryBusy,
                    onClick = { clearingMemory = true })
            }
        }
        item {
            XNoteSettingsSection("隐私") {
                XNoteSettingsRow("数据保存在本机", summary = "笔记、聊天和附件保存在此设备。")
                XNoteInsetDivider()
                XNoteSettingsRow("发送给 Agent 的内容", summary = "由你在对话中选择的权限决定；可随时在输入框旁调整。")
            }
        }
    }
}
