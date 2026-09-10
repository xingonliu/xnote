package com.xnote.app.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.design.*

// -- Functions

@Composable
fun ProfileScreen(
    trashCount: Int,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onOpenRecycleBin: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.profile_library_section), style = MaterialTheme.typography.titleSmall) }
        item { ProfileEntry("统计", "文字量、内容数量与最近笔记") { onOpenDetail("统计") } }
        item { ProfileEntry(stringResource(R.string.recycle_bin_title), stringResource(R.string.recycle_bin_profile_summary, trashCount), onOpenRecycleBin) }
        item { ProfileEntry("存储与隐私", "本地占用与缓存清理") { onOpenDetail("存储与隐私") } }
        item { ProfileEntry(stringResource(R.string.profile_appearance_section), "主题、动画、对比度、字体、阅读与默认背景", onOpenAppearance) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 16.dp)) {
                listOf(
                    "贴纸库" to "将在图片抠图与贴纸功能完成后开放。",
                    "模型与服务商" to "尚未接入模型服务，暂不支持配置服务商。",
                    "Agent 权限" to "Agent 笔记工具尚未接入，暂不申请笔记操作权限。",
                    "Skill 管理" to "将在 Agent 扩展能力完成后开放。",
                    "MCP 管理" to "尚未接入外部工具服务器。",
                    "Linux 环境" to "尚未安装本地 Linux 环境。",
                ).forEach { (title, summary) ->
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEntry(title: String, summary: String, onClick: () -> Unit) {
    XNoteGroupCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_keyline_stroke_chevron_right), null, Modifier.size(XNoteIconSizeSmall))
        }
    }
}
