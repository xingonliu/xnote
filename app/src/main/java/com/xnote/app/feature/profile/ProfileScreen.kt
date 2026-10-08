package com.xnote.app.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), state = listState, contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                XNoteSettingsSection("我的内容") {
                    XNoteSettingsRow("统计", icon = R.drawable.ic_keyline_stroke_list, onClick = { onOpenDetail("统计") })
                    XNoteInsetDivider(startIndent = 52.dp)
                    XNoteSettingsRow(stringResource(R.string.recycle_bin_title), icon = R.drawable.ic_keyline_stroke_bin,
                        value = "$trashCount 篇", onClick = onOpenRecycleBin)
                }
            }
            item {
                XNoteSettingsSection("Agent") {
                    XNoteSettingsRow("模型与服务商", icon = R.drawable.ic_keyline_stroke_star, onClick = { onOpenDetail("模型与服务商") })
                    XNoteInsetDivider(startIndent = 52.dp)
                    XNoteSettingsRow("记忆与画像", icon = R.drawable.ic_keyline_stroke_user, onClick = { onOpenDetail("记忆与画像") })
                }
            }
            item {
                XNoteSettingsSection("应用设置") {
                    XNoteSettingsRow(stringResource(R.string.profile_appearance_section), icon = R.drawable.ic_keyline_stroke_paintbrush,
                        onClick = onOpenAppearance)
                    XNoteInsetDivider(startIndent = 52.dp)
                    XNoteSettingsRow("存储与隐私", icon = R.drawable.ic_keyline_stroke_inbox, onClick = { onOpenDetail("存储与隐私") })
                }
            }
        }
    }
}
