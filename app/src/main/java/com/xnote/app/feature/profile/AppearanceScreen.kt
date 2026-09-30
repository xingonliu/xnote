package com.xnote.app.feature.profile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.settings.AppSettingsRepository
import com.xnote.app.design.*
import com.xnote.app.domain.model.*
import com.xnote.app.feature.background.XNoteBackgroundPicker
import kotlinx.coroutines.launch

// -- Functions

@Composable
fun AppearanceScreen(settings: AppSettingsRepository, contentPadding: PaddingValues, scrollState: ScrollState) {
    // -- State and Variables

    val value by settings.settings.collectAsState(defaultAppSettings())
    val scope = rememberCoroutineScope()
    val toast = LocalXNoteToast.current

    // -- Functions

    fun save(action: suspend () -> Unit) {
        scope.launch {
            try { action() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { toast.show("设置未保存，请重试") }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("xnote-appearance")
            .verticalScroll(scrollState).padding(contentPadding), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            XNoteSettingsSection("外观") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("主题", style = MaterialTheme.typography.bodyLarge)
                    XNoteChoiceGroup(ThemeMode.entries, value.themeMode, { when (it) {
                        ThemeMode.System -> "跟随系统"; ThemeMode.Light -> "浅色"; ThemeMode.Dark -> "深色"
                    } }, { save { settings.setThemeMode(it) } })
                }
                XNoteInsetDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("字体大小", style = MaterialTheme.typography.bodyLarge)
                    XNoteChoiceGroup(AppFontSize.entries, value.fontSize, { when (it) {
                        AppFontSize.Standard -> "标准"; AppFontSize.Large -> "大"; AppFontSize.ExtraLarge -> "特大"
                    } }, { save { settings.setFontSize(it) } })
                    Text("记录此刻的想法", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            XNoteSettingsSection("辅助功能") {
                XNoteSettingsSwitch("减少动画", value.reduceMotion, { save { settings.setReduceMotion(it) } },
                    Modifier.testTag("setting-减少动画"))
                XNoteInsetDivider()
                XNoteSettingsSwitch("高对比度", value.highContrast, { save { settings.setHighContrast(it) } },
                    Modifier.testTag("setting-高对比度"), summary = "让文字和控件更清晰")
            }
            XNoteSettingsSection("阅读与编辑") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("阅读排版", style = MaterialTheme.typography.bodyLarge)
                    XNoteChoiceGroup(ReadingLayout.entries, value.readingLayout, { when (it) {
                        ReadingLayout.Compact -> "窄栏"; ReadingLayout.Standard -> "标准"; ReadingLayout.Relaxed -> "宽松行距"
                    } }, { save { settings.setReadingLayout(it) } })
                    Text("同时用于导出图片。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                XNoteInsetDivider()
                XNoteSettingsSwitch(stringResource(R.string.editor_markdown_shortcuts_title), value.markdownShortcutsEnabled,
                    { save { settings.setMarkdownShortcutsEnabled(it) } }, Modifier.testTag("xnote-markdown-shortcuts-switch"),
                    summary = "输入 #、- 等符号时自动设置格式")
            }
            XNoteSettingsSection(stringResource(R.string.background_settings_title), description = "用于未单独设置背景的笔记。") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    XNoteBackgroundPicker(value.defaultBackground, value.defaultBackground, "",
                        onSelect = { selected -> save { settings.setDefaultBackground(selected ?: defaultBackgroundKey()) } })
                }
                XNoteInsetDivider()
                XNoteSettingsRow(stringResource(R.string.background_restore_initial),
                    onClick = { save { settings.setDefaultBackground(defaultBackgroundKey()) } })
            }
        }
    }
}
