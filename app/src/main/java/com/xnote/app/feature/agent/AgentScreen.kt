package com.xnote.app.feature.agent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.data.agent.AgentTimeline
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteTextField
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// -- Functions

@Composable
fun AgentScreen(timeline: AgentTimeline, backdrop: Backdrop, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val messages by timeline.messages.collectAsState(emptyList())
    val runs by timeline.runs.collectAsState(emptyList())
    val state by timeline.state.collectAsState()
    val savedDraft by timeline.draft.collectAsState()
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(false) }
    val unresolved = runs.any { it.status !in setOf(AgentRunStatus.Complete, AgentRunStatus.Failed, AgentRunStatus.Cancelled) }
    val keyboardVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    LaunchedEffect(state.ready) { if (state.ready && !restored) { input = savedDraft; restored = true } }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex)
    }
    fun action(block: suspend () -> Unit) { scope.launch {
        try { block(); error = null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = if (failure is AgentBudgetException) failure.message else safeModelError(failure) }
    } }
    Column(modifier.fillMaxSize().imePadding().padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!keyboardVisible || state.running || unresolved) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidButton({ action { timeline.newTopic() } }, backdrop, enabled = state.ready && !unresolved, modifier = Modifier.testTag("agent-new-topic")) { Text("开始新话题") }
            if (state.running) LiquidButton(timeline::stop, backdrop, modifier = Modifier.testTag("agent-stop")) { Text("停止") }
            if (!state.running && unresolved) LiquidButton({ action { timeline.finishUnresolved() } }, backdrop) { Text("保留内容并结束任务") }
        }
        if (!state.ready) Text("正在恢复对话…")
        (error ?: state.notice)?.let { Text(it, Modifier.testTag("agent-notice"), style = MaterialTheme.typography.bodySmall) }
        if (messages.isEmpty() && state.ready && !keyboardVisible) Text("在这里与 Agent 对话。模型在“我的 → 模型与服务商”配置。", style = MaterialTheme.typography.bodyMedium)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("agent-timeline"), state = list, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(messages, key = { it.sequence }) { message ->
                val run = runs.find { it.id == message.runId }
                XNoteGroupCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(when (message.role) { AgentMessageRole.User -> "你"; AgentMessageRole.Assistant -> "Agent"; else -> "话题" }, style = MaterialTheme.typography.labelMedium)
                        SelectionContainer { Text(message.text.ifEmpty { if (message.status == AgentMessageStatus.Streaming) "正在生成…" else "未生成回复" }) }
                        if (message.role == AgentMessageRole.Assistant) {
                            Text(when (message.status) {
                                AgentMessageStatus.Complete -> "已完成"
                                AgentMessageStatus.Streaming, AgentMessageStatus.Pending -> "生成中"
                                AgentMessageStatus.Failed -> "失败 · 已保留内容"
                                AgentMessageStatus.Cancelled -> "已停止"
                                AgentMessageStatus.Interrupted -> if (run?.status == AgentRunStatus.PausedBudget) "达到容量上限" else "已中断"
                            }, style = MaterialTheme.typography.bodySmall)
                            if (run?.inputTokens != null || run?.outputTokens != null) Text(
                                "服务用量：输入 ${run.inputTokens ?: "未知"} / 输出 ${run.outputTokens ?: "未知"} Token", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        XNoteTextField(input, { value -> input = value; action { timeline.saveDraft(value) } },
            Modifier.heightIn(min = 48.dp, max = 160.dp).testTag("agent-input"), placeholder = "输入消息", singleLine = false, enabled = state.ready && restored)
        LiquidButton({ val sent = input; action { timeline.send(sent); input = "" } }, backdrop,
            enabled = state.ready && restored && !state.running && !unresolved && input.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("agent-send")) { Text("发送") }
    }
}
