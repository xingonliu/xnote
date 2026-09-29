package com.xnote.app.feature.profile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.settings.AppSettingsRepository
import com.xnote.app.design.XNoteBottomNavigationHeight
import com.xnote.app.design.XNoteButton
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteInsetDivider
import com.xnote.app.design.XNoteSegmentedControl
import com.xnote.app.design.XNoteSettingSwitch
import com.xnote.app.domain.model.AppFontSize
import com.xnote.app.domain.model.ReadingLayout
import com.xnote.app.domain.model.ThemeMode
import com.xnote.app.domain.model.defaultAppSettings
import com.xnote.app.domain.model.defaultBackgroundKey
import com.xnote.app.feature.background.XNoteBackgroundPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// -- Functions

@Composable
fun AppearanceScreen(
    settings: AppSettingsRepository,
    contentPadding: PaddingValues,
    scrollState: ScrollState,
) {
    val value by settings.settings.collectAsState(defaultAppSettings())
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }

    fun save(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                error = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = true
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("xnote-appearance")
            .verticalScroll(scrollState)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (error) {
            Text("设置保存失败，请重试", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        // -- Section 1: 主题与视觉
        Text("主题与动效", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        XNoteGroupCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("主题模式", style = MaterialTheme.typography.bodyLarge)
                XNoteSegmentedControl(
                    items = ThemeMode.entries,
                    selectedItem = value.themeMode,
                    onItemSelected = { save { settings.setThemeMode(it) } },
                    label = { mode ->
                        when (mode) {
                            ThemeMode.System -> "跟随系统"
                            ThemeMode.Light -> "浅色"
                            ThemeMode.Dark -> "深色"
                        }
                    },
                )
            }
            XNoteInsetDivider(startIndent = 16.dp)
            XNoteSettingSwitch(
                title = "减少动画",
                summary = "同时尊重系统关闭动画设置",
                checked = value.reduceMotion,
                onCheckedChange = { save { settings.setReduceMotion(it) } },
                testTag = "setting-减少动画",
            )
            XNoteInsetDivider(startIndent = 16.dp)
            XNoteSettingSwitch(
                title = "高对比度",
                summary = "增强文字、边界和玻璃控件的对比度",
                checked = value.highContrast,
                onCheckedChange = { save { settings.setHighContrast(it) } },
                testTag = "setting-高对比度",
            )
        }

        // -- Section 2: 排版与阅读
        Text("排版与阅读", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        XNoteGroupCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("字体大小", style = MaterialTheme.typography.bodyLarge)
                XNoteSegmentedControl(
                    items = AppFontSize.entries,
                    selectedItem = value.fontSize,
                    onItemSelected = { save { settings.setFontSize(it) } },
                    label = { size ->
                        when (size) {
                            AppFontSize.Standard -> "标准"
                            AppFontSize.Large -> "大"
                            AppFontSize.ExtraLarge -> "特大"
                        }
                    },
                )
                Text(
                    "在系统字体大小基础上调整，立即应用于所有页面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            XNoteInsetDivider(startIndent = 16.dp)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("阅读排版", style = MaterialTheme.typography.bodyLarge)
                XNoteSegmentedControl(
                    items = ReadingLayout.entries,
                    selectedItem = value.readingLayout,
                    onItemSelected = { save { settings.setReadingLayout(it) } },
                    label = { layout ->
                        when (layout) {
                            ReadingLayout.Compact -> "窄栏"
                            ReadingLayout.Standard -> "标准"
                            ReadingLayout.Relaxed -> "宽松行距"
                        }
                    },
                )
                Text(
                    "调整阅读栏宽与行距，图片导出使用相同排版；窄窗口自动适配。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            XNoteInsetDivider(startIndent = 16.dp)
            XNoteSettingSwitch(
                title = stringResource(R.string.editor_markdown_shortcuts_title),
                summary = stringResource(R.string.editor_markdown_shortcuts_summary),
                checked = value.markdownShortcutsEnabled,
                onCheckedChange = { save { settings.setMarkdownShortcutsEnabled(it) } },
                modifier = Modifier.testTag("xnote-markdown-shortcuts"),
                testTag = "xnote-markdown-shortcuts-switch",
            )
        }

        // -- Section 3: 默认背景
        Text(stringResource(R.string.background_settings_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        XNoteGroupCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                XNoteBackgroundPicker(
                    selectedKey = value.defaultBackground,
                    previewBackground = value.defaultBackground,
                    scopeDescription = stringResource(R.string.background_scope_default),
                    onSelect = { selected ->
                        save { settings.setDefaultBackground(selected ?: defaultBackgroundKey()) }
                    },
                )
                XNoteButton(
                    onClick = { save { settings.setDefaultBackground(defaultBackgroundKey()) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.background_restore_initial))
                }
            }
        }

        Spacer(Modifier.height(XNoteBottomNavigationHeight + 16.dp))
    }
}
