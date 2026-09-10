package com.xnote.app.feature.profile

import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.xnote.app.R
import com.kyant.backdrop.Backdrop
import com.xnote.app.data.settings.AppSettingsRepository
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.*
import com.xnote.app.feature.background.XNoteBackgroundPicker
import kotlinx.coroutines.launch

// -- Functions

@Composable
fun AppearanceScreen(settings: AppSettingsRepository, backdrop: Backdrop, contentPadding: PaddingValues, scrollState: ScrollState) {
    val value by settings.settings.collectAsState(defaultAppSettings())
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    fun save(action: suspend () -> Unit) { scope.launch {
        try { action(); error = false } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    } }
    Column(Modifier.fillMaxSize().testTag("xnote-appearance").verticalScroll(scrollState).padding(contentPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (error) Text("设置保存失败，请重试", color = MaterialTheme.colorScheme.error)
        SettingsChoices("主题", ThemeMode.entries, value.themeMode, { when (it) {
            ThemeMode.System -> "跟随系统"; ThemeMode.Light -> "浅色"; ThemeMode.Dark -> "深色"
        } }, { save { settings.setThemeMode(it) } })
        SettingsSwitch("减少动画", "同时尊重系统关闭动画设置", value.reduceMotion) { save { settings.setReduceMotion(it) } }
        SettingsSwitch("高对比度", "增强文字、边界和玻璃控件的对比度", value.highContrast) { save { settings.setHighContrast(it) } }
        SettingsChoices("字体大小", AppFontSize.entries, value.fontSize, { when (it) {
            AppFontSize.Standard -> "标准"; AppFontSize.Large -> "大"; AppFontSize.ExtraLarge -> "特大"
        } }, { save { settings.setFontSize(it) } })
        Text("在系统字体大小基础上调整，立即应用于所有页面。", style = MaterialTheme.typography.bodyMedium)
        SettingsChoices("阅读排版", ReadingLayout.entries, value.readingLayout, { when (it) {
            ReadingLayout.Compact -> "窄栏"; ReadingLayout.Standard -> "标准"; ReadingLayout.Relaxed -> "宽松行距"
        } }, { save { settings.setReadingLayout(it) } })
        Text("调整阅读栏宽与行距，图片导出使用相同排版；窄窗口自动适配。", style = MaterialTheme.typography.bodyMedium)
        SettingsSwitch(stringResource(R.string.editor_markdown_shortcuts_title), stringResource(R.string.editor_markdown_shortcuts_summary), value.markdownShortcutsEnabled,
            Modifier.testTag("xnote-markdown-shortcuts")) { save { settings.setMarkdownShortcutsEnabled(it) } }
        Text(stringResource(R.string.background_settings_title), style = MaterialTheme.typography.titleMedium)
        XNoteBackgroundPicker(value.defaultBackground, value.defaultBackground, stringResource(R.string.background_scope_default),
            onSelect = { selected -> save { settings.setDefaultBackground(selected ?: defaultBackgroundKey()) } })
        LiquidButton({ save { settings.setDefaultBackground(defaultBackgroundKey()) } }, backdrop, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.background_restore_initial))
        }
    }
}

@Composable
private fun <T> SettingsChoices(title: String, choices: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        choices.forEach { choice ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = choice == selected, role = Role.RadioButton, onClick = { onSelect(choice) }).padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(choice == selected, onClick = null)
                Text(label(choice), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SettingsSwitch(title: String, summary: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title }.testTag(if (title == "Markdown 快捷输入") "xnote-markdown-shortcuts-switch" else "setting-$title"))
    }
}
