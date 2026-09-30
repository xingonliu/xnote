package com.xnote.app.feature.agent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xnote.app.data.agent.AgentTimeline
import com.xnote.app.data.db.AgentMemorySettingsEntity
import com.xnote.app.data.db.AgentProfileFactEntity
import com.xnote.app.design.*
import com.xnote.app.domain.agent.AgentMessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

// -- Type Definitions

private enum class MemoryPage(val title: String) {
    Memory("记忆与画像"), History("聊天记录"), Usage("模型用量"),
}

// -- Functions

@Composable
fun AgentMemoryScreen(timeline: AgentTimeline, onBack: () -> Unit) {
    // -- State and Variables

    val store = timeline.profileMemory
    val settings by store.settings.collectAsState(AgentMemorySettingsEntity())
    val facts by store.facts.collectAsState(emptyList())
    val usage by store.usage.collectAsState(emptyList())
    val problems by store.problems.collectAsState(emptyList())
    val history by timeline.messages.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val toast = LocalXNoteToast.current
    var page by rememberSaveable { mutableStateOf(MemoryPage.Memory) }
    var editing by remember { mutableStateOf<AgentProfileFactEntity?>(null) }
    var forgetting by remember { mutableStateOf<AgentProfileFactEntity?>(null) }
    var value by remember { mutableStateOf("") }
    var clearing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var historyLimit by rememberSaveable { mutableIntStateOf(20) }
    var expandedMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // -- Derived Values

    val historyMatches = remember(history, historyQuery) {
        if (historyQuery.isBlank()) emptyList() else history.filter {
            it.role in setOf(AgentMessageRole.User, AgentMessageRole.Assistant) && it.text.contains(historyQuery, true)
        }.asReversed()
    }

    // -- Functions

    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { toast.show("操作未完成，请重试") }
            finally { busy = false }
        }
    }
    fun back() { if (page == MemoryPage.Memory) onBack() else page = MemoryPage.Memory }

    key(page) {
        XNoteSettingsPage(page.title, ::back, Modifier.testTag("agent-memory"), overlay = { glass ->
            editing?.let { fact ->
                XNoteDialog(true, { if (!busy) editing = null }, if (fact.status == "review") "确认记忆" else "编辑记忆", glass,
                    XNoteDialogAction("保存", { action { store.confirm(fact.id, value); editing = null; toast.show("记忆已保存") } },
                        enabled = value.isNotBlank() && !busy),
                    dismissAction = XNoteDialogAction("取消", { editing = null }, enabled = !busy)) {
                    XNoteTextField(value, { value = it }, placeholder = "记忆内容", singleLine = false, enabled = !busy)
                    Text("来自你的话：${fact.sourceQuote}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(DateFormat.getDateInstance().format(Date(fact.createdAtEpochMs)), style = MaterialTheme.typography.bodySmall)
                    XNoteSettingsRow("删除这条记忆", destructive = true, enabled = !busy, onClick = { editing = null; forgetting = fact })
                }
            }
            forgetting?.let { fact ->
                XNoteDialog(true, { if (!busy) forgetting = null }, "删除这条记忆？", glass,
                    XNoteDialogAction("删除", { action { store.forget(fact.key); forgetting = null; toast.show("记忆已删除") } },
                        enabled = !busy, destructive = true),
                    dismissAction = XNoteDialogAction("取消", { forgetting = null }, enabled = !busy)) {
                    Text("Agent 会停止记住这项偏好，聊天记录会保留。")
                }
            }
            XNoteDialog(clearing, { if (!busy) clearing = false }, "清除所有记忆？", glass,
                XNoteDialogAction("清除", { action { store.clear(); clearing = false; toast.show("记忆已清除") } }, enabled = !busy, destructive = true),
                dismissAction = XNoteDialogAction("取消", { clearing = false }, enabled = !busy)) {
                Text("聊天记录会保留，已清除的偏好不会从旧聊天中重新记住。")
            }
        }) { backdrop ->
            when (page) {
                MemoryPage.Memory -> {
                    item {
                        XNoteSettingsSection("记忆偏好", description = "关闭后，已保存的记忆仍可使用。你也可以在对话中要求记住或忘记。") {
                            XNoteSettingsSwitch("自动记忆", settings.automatic, { action { store.setAutomatic(it) } },
                                Modifier.testTag("automatic-memory"), backdrop = backdrop, summary = "记住对话中有用的偏好", enabled = !busy)
                        }
                    }
                    item {
                        XNoteSettingsSection("已记住的偏好") {
                            if (facts.isEmpty()) XNoteSettingsRow("还没有记忆", summary = "告诉 Agent 你的偏好，例如喜欢简短的回答。")
                            facts.forEachIndexed { index, fact ->
                                if (index > 0) XNoteInsetDivider()
                                XNoteSettingsRow(fact.value, Modifier.testTag("agent-memory-fact-${fact.id}"),
                                    summary = if (fact.status == "review") "待你确认" else null,
                                    onClick = { editing = fact; value = fact.value })
                            }
                        }
                    }
                    item {
                        XNoteSettingsSection("记录与用量") {
                            XNoteSettingsRow("聊天记录", onClick = { page = MemoryPage.History })
                            XNoteInsetDivider()
                            XNoteSettingsRow("模型用量", onClick = { page = MemoryPage.Usage })
                        }
                    }
                    if (facts.isNotEmpty()) item {
                        XNoteGroupCard {
                            XNoteSettingsRow("清除所有记忆", destructive = true, enabled = !busy, onClick = { clearing = true })
                        }
                    }
                }
                MemoryPage.History -> {
                    item {
                        XNoteTextField(historyQuery, { historyQuery = it; historyLimit = 20 },
                            Modifier.testTag("agent-history-search"), placeholder = "搜索聊天记录")
                    }
                    if (historyQuery.isBlank()) item {
                        Text("输入关键词，查找过去的对话。", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else item {
                        Text(if (historyMatches.isEmpty()) "没有找到相关对话" else "${historyMatches.size} 条结果",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(historyMatches.take(historyLimit), key = { "history:${it.id}" }) { message ->
                        XNoteGroupCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${if (message.role == AgentMessageRole.User) "你" else "Agent"} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(message.createdAtEpochMs))}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SelectionContainer { Text(if (expandedMessage == message.id) message.text else message.text.take(240)) }
                                if (message.text.length > 240) XNoteButton({ expandedMessage = if (expandedMessage == message.id) null else message.id }) {
                                    Text(if (expandedMessage == message.id) "收起" else "展开全文")
                                }
                            }
                        }
                    }
                    if (historyMatches.size > historyLimit) item { XNoteButton({ historyLimit += 20 }) { Text("更多结果") } }
                }
                MemoryPage.Usage -> {
                    item { Text("用于整理聊天与笔记的模型请求。费用由模型服务商收取。", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (usage.isEmpty()) item { XNoteGroupCard { XNoteSettingsRow("暂无用量") } }
                    items(usage.groupBy { it.taskType }.toList()) { (kind, rows) ->
                        XNoteSettingsSection(if (kind == "episode") "整理聊天" else "整理笔记",
                            description = if (rows.any { it.inputTokens == null || it.outputTokens == null }) "部分请求未返回用量，统计可能不完整。" else null) {
                            XNoteSettingsRow("请求次数", value = "${rows.size} 次")
                            XNoteInsetDivider()
                            XNoteSettingsRow("输入 Token", value = NumberFormat.getIntegerInstance().format(rows.sumOf { it.inputTokens ?: 0 }))
                            XNoteInsetDivider()
                            XNoteSettingsRow("输出 Token", value = NumberFormat.getIntegerInstance().format(rows.sumOf { it.outputTokens ?: 0 }))
                        }
                    }
                    if (problems.isNotEmpty()) item {
                        Text("${problems.size} 项内容尚未整理完成，原文仍保留。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
