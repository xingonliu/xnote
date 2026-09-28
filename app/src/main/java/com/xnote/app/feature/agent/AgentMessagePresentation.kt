package com.xnote.app.feature.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.xnote.app.R
import com.xnote.app.data.db.AgentMessageEntity
import com.xnote.app.data.db.AgentRunEntity
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.domain.agent.AgentMessageRole

// -- Functions

@Composable
fun AgentMessageSurface(role: AgentMessageRole, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = if (role == AgentMessageRole.User) Modifier.align(Alignment.CenterEnd)
                .fillMaxWidth(0.88f).background(MaterialTheme.colorScheme.surfaceVariant, XNoteSmoothCornerShape(20.dp)).padding(16.dp)
            else Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
        )
    }
}

@Composable
fun AgentMessageActions(message: AgentMessageEntity, run: AgentRunEntity?,
    canDelete: Boolean, onDelete: () -> Unit) {
    // -- State

    var expanded by remember(message.id) { mutableStateOf(false) }

    // -- Derived Values

    val hasUsage = message.role == AgentMessageRole.Assistant && (run?.inputTokens != null || run?.outputTokens != null)

    if (canDelete || hasUsage) {
        IconButton({ expanded = !expanded }, Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_keyline_stroke_more_horizontal), if (expanded) "收起消息选项" else "消息选项",
                Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            if (hasUsage) Text("服务用量：输入 ${run.inputTokens ?: "未知"} / 输出 ${run.outputTokens ?: "未知"} Token",
                style = MaterialTheme.typography.bodySmall)
            if (canDelete) TextButton(onDelete) { Text("删除消息", color = MaterialTheme.colorScheme.error) }
        }
    }
}
