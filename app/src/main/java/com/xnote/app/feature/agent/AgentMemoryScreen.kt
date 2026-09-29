package com.xnote.app.feature.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.R
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

    fun action(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "操作未完成，请重试。"
            }
        }
    }

    // -- Listeners

    BackHandler(onBack = onBack)
    XNotePageScaffold(
        backdrop = backdrop,
        scrollEdges = setOf(XNoteScrollEdge.Top),
        alwaysVisibleScrollEdges = setOf(XNoteScrollEdge.Top),
        content = {
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            val colors = MaterialTheme.colorScheme
            val typography = MaterialTheme.typography

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("agent-memory"),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = xNoteScrollEdgePadding(insets.calculateTopPadding() + XNoteHeaderHeight),
                    bottom = insets.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Section 1: 自动记忆总控开关
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "记忆开关",
                            style = typography.labelLarge,
                            color = colors.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        XNoteGroupCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_keyline_stroke_star),
                                            contentDescription = null,
                                            tint = colors.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "自动记忆",
                                            style = typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "自动从会话与任务中学习你的长期偏好",
                                            style = typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = settings.automatic,
                                        onCheckedChange = { action { store.setAutomatic(it) } },
                                        modifier = Modifier
                                            .semantics { contentDescription = "自动记忆" }
                                            .testTag("automatic-memory"),
                                    )
                                }
                            }
                        }
                        Text(
                            text = "关闭后仍可使用已有画像，明确要求记住时仍可保存。重新开启不会补记关闭期间的内容。对话与摘要继续保存。",
                            style = typography.bodySmall,
                            color = colors.outline,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }

                // Error Message Banner
                error?.let { errText ->
                    item {
                        XNoteGroupCard(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_keyline_stroke_file_text),
                                    contentDescription = null,
                                    tint = colors.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = errText,
                                    style = typography.bodyMedium,
                                    color = colors.error,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                // Section 2: 搜索聊天记录
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "搜索聊天记录",
                                style = typography.labelLarge,
                                color = colors.primary,
                            )
                            if (historyQuery.isNotBlank()) {
                                Text(
                                    text = "${historyMatches.size} 条结果",
                                    style = typography.labelMedium,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        XNoteGroupCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                XNoteTextField(
                                    value = historyQuery,
                                    onValueChange = { historyQuery = it; historyLimit = 20 },
                                    placeholder = "搜索全部已保存的聊天文字",
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }

                // History search results
                items(historyMatches.take(historyLimit), key = { "history:${it.id}" }) { message ->
                    val isUser = message.role == com.xnote.app.domain.agent.AgentMessageRole.User
                    XNoteGroupCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isUser) colors.primary.copy(alpha = 0.14f) else colors.tertiary.copy(alpha = 0.14f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = if (isUser) "你" else "Agent",
                                        style = typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isUser) colors.primary else colors.tertiary,
                                    )
                                }
                                Text(
                                    text = DateFormat.getDateTimeInstance().format(Date(message.createdAtEpochMs)),
                                    style = typography.bodySmall,
                                    color = colors.outline,
                                )
                            }
                            SelectionContainer {
                                Text(
                                    text = if (expandedMessage == message.id) message.text else message.text.take(240),
                                    style = typography.bodyMedium,
                                    color = colors.onSurface,
                                    lineHeight = typography.bodyMedium.lineHeight,
                                )
                            }
                            if (message.text.length > 240) {
                                TextButton(
                                    onClick = { expandedMessage = if (expandedMessage == message.id) null else message.id },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text(if (expandedMessage == message.id) "收起" else "展开全文")
                                }
                            }
                        }
                    }
                }

                if (historyMatches.size > historyLimit) {
                    item {
                        XNoteGroupCard(Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { historyLimit += 20 }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "更多搜索结果",
                                    style = typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primary,
                                )
                            }
                        }
                    }
                }

                // Section 3: 长期画像列表
                item {
                    Text(
                        text = "长期画像",
                        style = typography.labelLarge,
                        color = colors.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                if (facts.isEmpty()) {
                    item {
                        XNoteGroupCard(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_keyline_stroke_star),
                                    contentDescription = null,
                                    tint = colors.outline,
                                    modifier = Modifier.size(32.dp),
                                )
                                Text(
                                    text = "还没有长期画像。你可以在对话中明确要求 Agent 记住偏好。",
                                    style = typography.bodyMedium,
                                    color = colors.outline,
                                )
                            }
                        }
                    }
                }

                items(facts, key = { it.id }) { fact ->
                    val statusText = if (fact.status == "review") "待确认，尚未使用" else if (fact.evidence in setOf("Inferred", "Repeated")) "推断记忆，已确认" else "已生效"
                    val isReview = fact.status == "review"

                    XNoteGroupCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = fact.value,
                                    style = typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isReview) colors.error.copy(alpha = 0.12f) else colors.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = statusText,
                                        style = typography.labelSmall,
                                        color = if (isReview) colors.error else colors.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }

                            if (fact.sourceQuote.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "来源：${fact.sourceQuote}",
                                        style = typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                            }

                            Text(
                                text = DateFormat.getDateTimeInstance().format(Date(fact.createdAtEpochMs)),
                                style = typography.bodySmall,
                                color = colors.outline,
                            )

                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { editing = fact; value = fact.value },
                                ) {
                                    Text(if (fact.status == "review") "确认或更正" else "更正")
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = { action { store.forget(fact.key) } },
                                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                                ) {
                                    Text("删除并停止记住")
                                }
                            }
                        }
                    }
                }

                // Section 4: 危险操作：清除画像
                item {
                    XNoteGroupCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { clearing = true }
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_keyline_stroke_bin),
                                    contentDescription = null,
                                    tint = colors.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "清除长期画像",
                                    style = typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.error,
                                )
                            }
                            Text(
                                text = "保留聊天记录，并阻止旧聊天和旧任务恢复已清除的画像。",
                                style = typography.bodySmall,
                                color = colors.outline,
                                modifier = Modifier.padding(start = 28.dp),
                            )
                        }
                    }
                }

                // Section 5: 后台模型用量
                item {
                    Text(
                        text = "后台模型用量",
                        style = typography.labelLarge,
                        color = colors.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                if (usage.isEmpty()) {
                    item {
                        XNoteGroupCard(Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "暂无后台模型请求。",
                                    style = typography.bodyMedium,
                                    color = colors.outline,
                                )
                            }
                        }
                    }
                }

                items(usage.groupBy { it.taskType }.toList()) { (kind, rows) ->
                    val kindTitle = if (kind == "episode") "片段摘要" else "笔记摘要"
                    val inputTokens = rows.sumOf { it.inputTokens ?: 0 }
                    val outputTokens = rows.sumOf { it.outputTokens ?: 0 }
                    val unrecordedCount = rows.count { it.inputTokens == null || it.outputTokens == null }
                    val reservedTokens = rows.sumOf { it.reservedTokens }

                    XNoteGroupCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_keyline_stroke_refresh_cw),
                                            contentDescription = null,
                                            tint = colors.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    Text(
                                        text = kindTitle,
                                        style = typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.surfaceVariant)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "${rows.size} 次请求",
                                        style = typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceVariant.copy(alpha = 0.45f))
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        text = "服务返回 Token",
                                        style = typography.labelSmall,
                                        color = colors.outline,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "$inputTokens 输入 / $outputTokens 输出",
                                        style = typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface,
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceVariant.copy(alpha = 0.45f))
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        text = "预算预留 Token",
                                        style = typography.labelSmall,
                                        color = colors.outline,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "$reservedTokens",
                                        style = typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface,
                                    )
                                }
                            }

                            if (unrecordedCount > 0) {
                                Text(
                                    text = "$unrecordedCount 次请求未返回完整用量统计",
                                    style = typography.bodySmall,
                                    color = colors.outline,
                                )
                            }
                        }
                    }
                }

                // Section 6: 未完成问题提示
                if (problems.isNotEmpty()) {
                    item {
                        XNoteGroupCard(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_keyline_stroke_file_text),
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "${problems.size} 个摘要任务尚未完成。配置已变更或输入过大时保留原文回退；网络或结构错误最多尝试 3 次。",
                                    style = typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        overlay = {
            XNoteHeader(
                title = "记忆与画像",
                backdrop = backdrop,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    )

    // Edit Fact Dialog
    editing?.let { fact ->
        XNoteDialog(
            visible = true,
            onDismissRequest = { editing = null },
            title = "确认记忆",
            backdrop = backdrop,
            confirmAction = XNoteDialogAction(
                label = "保存",
                onClick = { action { store.confirm(fact.id, value); editing = null } },
                enabled = value.isNotBlank(),
            ),
            dismissAction = XNoteDialogAction(
                label = "取消",
                onClick = { editing = null },
            ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                XNoteTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "记忆内容",
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    // Clear Fact Dialog
    XNoteDialog(
        visible = clearing,
        onDismissRequest = { clearing = false },
        title = "清除长期画像？",
        backdrop = backdrop,
        confirmAction = XNoteDialogAction(
            label = "清除",
            onClick = { action { store.clear(); clearing = false } },
        ),
        dismissAction = XNoteDialogAction(
            label = "取消",
            onClick = { clearing = false },
        ),
    ) {
        Text("聊天记录保留，旧任务不会重新提取这些画像。")
    }
}
