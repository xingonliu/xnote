package com.xnote.app.feature.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.data.agent.AgentTimeline
import com.xnote.app.data.db.AgentMemorySettingsEntity
import com.xnote.app.data.db.AgentProfileFactEntity
import com.xnote.app.design.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// -- Functions

@Composable
fun AgentMemoryScreen(timeline: AgentTimeline, onBack: () -> Unit) {
    // -- State and Variables

    val store = timeline.profileMemory
    val settings by store.settings.collectAsState(AgentMemorySettingsEntity())
    val facts by store.facts.collectAsState(emptyList())
    val usage by store.usage.collectAsState(emptyList())
    val problems by store.problems.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<AgentProfileFactEntity?>(null) }
    var value by remember { mutableStateOf("") }
    var clearing by remember { mutableStateOf(false) }
    val history by timeline.messages.collectAsState(emptyList())
    var historyQuery by remember { mutableStateOf("") }
    var historyLimit by remember { mutableIntStateOf(20) }
    var expandedMessage by remember { mutableStateOf<String?>(null) }

    // -- Derived Values

    val historyMatches = remember(history, historyQuery) {
        if (historyQuery.isBlank()) emptyList() else history.filter {
            it.role in setOf(com.xnote.app.domain.agent.AgentMessageRole.User, com.xnote.app.domain.agent.AgentMessageRole.Assistant) && it.text.contains(historyQuery, true)
        }.asReversed()
    }

    // -- Functions

    fun action(block: suspend () -> Unit) { scope.launch {
        try { block(); error = null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "操作未完成，请重试。" }
    } }

    // -- Listeners

    BackHandler(onBack = onBack)
    XNotePageScaffold(backdrop = backdrop, scrollEdges = setOf(XNoteScrollEdge.Top), alwaysVisibleScrollEdges = setOf(XNoteScrollEdge.Top), content = {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        LazyColumn(Modifier.fillMaxSize().testTag("agent-memory"), contentPadding = PaddingValues(start = 24.dp, end = 24.dp,
            top = xNoteScrollEdgePadding(insets.calculateTopPadding() + XNoteHeaderHeight), bottom = insets.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("自动记忆", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(settings.automatic, { action { store.setAutomatic(it) } }, Modifier.semantics { contentDescription = "自动记忆" }.testTag("automatic-memory"))
                }
                Text("关闭后仍可使用已有画像，明确要求记住时仍可保存。重新开启不会补记关闭期间的内容。对话与摘要继续保存。")
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Text("搜索聊天记录", style = MaterialTheme.typography.titleMedium)
                XNoteTextField(historyQuery, { historyQuery = it; historyLimit = 20 }, placeholder = "搜索全部已保存的聊天文字")
                if (historyQuery.isNotBlank()) Text("${historyMatches.size} 条结果")
            }
            items(historyMatches.take(historyLimit), key = { "history:${it.id}" }) { message ->
                XNoteGroupCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${if (message.role == com.xnote.app.domain.agent.AgentMessageRole.User) "你" else "Agent"} · ${DateFormat.getDateTimeInstance().format(Date(message.createdAtEpochMs))}")
                        SelectionContainer { Text(if (expandedMessage == message.id) message.text else message.text.take(240)) }
                        if (message.text.length > 240) TextButton({ expandedMessage = if (expandedMessage == message.id) null else message.id }) { Text(if (expandedMessage == message.id) "收起" else "展开全文") }
                    }
                }
            }
            if (historyMatches.size > historyLimit) item { TextButton({ historyLimit += 20 }) { Text("更多搜索结果") } }
            if (facts.isEmpty()) item { Text("还没有长期画像。你可以在对话中明确要求 Agent 记住偏好。") }
            items(facts, key = { it.id }) { fact ->
                XNoteGroupCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(fact.value, style = MaterialTheme.typography.titleMedium)
                        Text(fact.key, style = MaterialTheme.typography.labelMedium)
                        Text(if (fact.status == "review") "待确认，尚未使用" else if (fact.evidence in setOf("Inferred", "Repeated")) "推断记忆，已确认" else "已生效")
                        Text("来源：${fact.sourceQuote}", style = MaterialTheme.typography.bodySmall)
                        Text(DateFormat.getDateTimeInstance().format(Date(fact.createdAtEpochMs)), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton({ editing = fact; value = fact.value }) { Text(if (fact.status == "review") "确认或更正" else "更正") }
                            TextButton({ action { store.forget(fact.key) } }) { Text("删除并停止记住") }
                        }
                    }
                }
            }
            item { TextButton({ clearing = true }) { Text("清除长期画像") }; Text("保留聊天记录，并阻止旧聊天和旧任务恢复已清除的画像。") }
            item { Text("后台模型用量", style = MaterialTheme.typography.titleMedium) }
            items(usage.groupBy { it.taskType }.toList()) { (kind, rows) ->
                Text("${if (kind == "episode") "片段摘要" else "笔记摘要"}：${rows.size} 次请求\n服务返回：${rows.sumOf { it.inputTokens ?: 0 }} 输入 / ${rows.sumOf { it.outputTokens ?: 0 }} 输出 Token\n${rows.count { it.inputTokens == null || it.outputTokens == null }} 次未返回完整用量；保守预算预留 ${rows.sumOf { it.reservedTokens }} Token")
            }
            if (usage.isEmpty()) item { Text("暂无后台模型请求。") }
            if (problems.isNotEmpty()) item { Text("${problems.size} 个摘要任务尚未完成。配置已变更或输入过大时保留原文回退；网络或结构错误最多尝试 3 次。", style = MaterialTheme.typography.bodySmall) }
        }
    }, overlay = { XNoteHeader("记忆与画像", backdrop, onBack = onBack, modifier = Modifier.align(Alignment.TopCenter)) })
    editing?.let { fact ->
        XNoteDialog(true, { editing = null }, "确认记忆", backdrop,
            confirmAction = XNoteDialogAction("保存", { action { store.confirm(fact.id, value); editing = null } }, value.isNotBlank()),
            dismissAction = XNoteDialogAction("取消", { editing = null })) {
            XNoteTextField(value, { value = it }, placeholder = "记忆内容")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    XNoteDialog(clearing, { clearing = false }, "清除长期画像？", backdrop,
        confirmAction = XNoteDialogAction("清除", { action { store.clear(); clearing = false } }),
        dismissAction = XNoteDialogAction("取消", { clearing = false })) { Text("聊天记录保留，旧任务不会重新提取这些画像。") }
}
