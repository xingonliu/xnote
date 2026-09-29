package com.xnote.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.design.XNoteBottomNavigationHeight
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteInsetDivider
import com.xnote.app.design.XNoteSettingRow

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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // -- Section 1: 内容与创作
        item {
            ProfileSectionHeader("内容与创作")
        }
        item {
            XNoteGroupCard(Modifier.fillMaxWidth()) {
                XNoteSettingRow(
                    title = "贴纸库",
                    summary = "创建、管理和预览贴纸",
                    iconRes = R.drawable.ic_keyline_stroke_grid_squares_x,
                    iconTint = Color(0xFF6750A4),
                    iconContainerColor = Color(0xFFEADDFF).copy(alpha = 0.5f),
                    onClick = { onOpenDetail("贴纸库") },
                )
                XNoteInsetDivider(startIndent = 62.dp)
                XNoteSettingRow(
                    title = "统计",
                    summary = "文字量、内容数量与最近笔记",
                    iconRes = R.drawable.ic_keyline_stroke_file_text,
                    iconTint = Color(0xFF00639B),
                    iconContainerColor = Color(0xFFC2E7FF).copy(alpha = 0.5f),
                    onClick = { onOpenDetail("统计") },
                )
                XNoteInsetDivider(startIndent = 62.dp)
                XNoteSettingRow(
                    title = stringResource(R.string.recycle_bin_title),
                    summary = stringResource(R.string.recycle_bin_profile_summary, trashCount),
                    iconRes = R.drawable.ic_keyline_stroke_bin,
                    iconTint = Color(0xFFB3261E),
                    iconContainerColor = Color(0xFFF9DEDC).copy(alpha = 0.5f),
                    onClick = onOpenRecycleBin,
                )
            }
        }

        // -- Section 2: AI 智能体
        item {
            ProfileSectionHeader("AI 智能体")
        }
        item {
            XNoteGroupCard(Modifier.fillMaxWidth()) {
                XNoteSettingRow(
                    title = "模型与服务商",
                    summary = "配置 OpenAI、Anthropic 或 Gemini 协议",
                    iconRes = R.drawable.ic_keyline_stroke_code,
                    iconTint = Color(0xFF006874),
                    iconContainerColor = Color(0xFF97F0FF).copy(alpha = 0.4f),
                    onClick = { onOpenDetail("模型与服务商") },
                )
                XNoteInsetDivider(startIndent = 62.dp)
                XNoteSettingRow(
                    title = "记忆与画像",
                    summary = "自动记忆、来源、更正与遗忘",
                    iconRes = R.drawable.ic_keyline_stroke_scan_text,
                    iconTint = Color(0xFF386A20),
                    iconContainerColor = Color(0xFFB7F397).copy(alpha = 0.45f),
                    onClick = { onOpenDetail("记忆与画像") },
                )
            }
        }

        // -- Section 3: 偏好与系统
        item {
            ProfileSectionHeader("偏好与系统")
        }
        item {
            XNoteGroupCard(Modifier.fillMaxWidth()) {
                XNoteSettingRow(
                    title = stringResource(R.string.profile_appearance_section),
                    summary = "主题、动画、对比度、字体、阅读与默认背景",
                    iconRes = R.drawable.ic_keyline_stroke_paintbrush,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    onClick = onOpenAppearance,
                )
                XNoteInsetDivider(startIndent = 62.dp)
                XNoteSettingRow(
                    title = "存储与隐私",
                    summary = "本地占用与缓存清理",
                    iconRes = R.drawable.ic_keyline_stroke_refresh_cw,
                    iconTint = Color(0xFF555F71),
                    iconContainerColor = Color(0xFFD9E2F4).copy(alpha = 0.5f),
                    onClick = { onOpenDetail("存储与隐私") },
                )
            }
        }

        // Bottom spacing to avoid liquid bottom tabs overlay
        item {
            Spacer(Modifier.height(XNoteBottomNavigationHeight + 16.dp))
        }
    }
}

@Composable
private fun ProfileSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
    )
}
