package com.xnote.app.feature.agent

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.data.agent.*
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.CancellationException

// -- Functions

@Composable
internal fun ModelProfileForm(initial: ModelProfile, isNew: Boolean, backdrop: Backdrop, catalog: ModelCatalog, busy: Boolean, onCancel: () -> Unit, onSave: (ModelProfile, String?) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var protocol by remember { mutableStateOf(initial.protocol) }
    var root by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.modelId) }
    var secret by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var context by remember { mutableStateOf(initial.contextTokens.toString()) }
    var output by remember { mutableStateOf(initial.outputTokens.toString()) }
    var preset by remember { mutableStateOf(isNew || initial.usesPreset) }
    var models by remember { mutableStateOf<List<CatalogModel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var vendor by remember { mutableStateOf(initial.modelId.substringBefore('/').removePrefix("~")) }
    var search by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(isNew) }
    fun select(value: CatalogModel) {
        if (name.isBlank() || name == models.find { it.id == model }?.name) name = value.name
        model = value.id
        root = OpenRouterBaseUrl
        protocol = ModelProtocol.OpenAI
        vendor = value.vendor
        context = value.contextTokens.toString()
        output = value.outputTokens.toString()
        picking = false
    }
    LaunchedEffect(preset, refresh) {
        if (preset) {
            loading = true
            catalogError = false
            try {
                models = catalog.load()
                if (model.isBlank()) models.firstOrNull()?.let(::select)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { catalogError = true }
            finally { loading = false }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(preset, {
            if (!preset) { preset = true; model = ""; root = OpenRouterBaseUrl; protocol = ModelProtocol.OpenAI; secret = ""; picking = true }
        }, label = { Text("预设") }, enabled = !busy, modifier = Modifier.testTag("model-preset"))
        FilterChip(!preset, {
            if (preset) { preset = false; model = ""; root = protocol.root; secret = "" }
        }, label = { Text("自定义") }, enabled = !busy, modifier = Modifier.testTag("model-custom"))
    }
    if (preset) {
        Text("预设来自 OpenRouter 最新目录，需填写 OpenRouter API Key。")
        if (loading) Text("正在加载模型目录…")
        if (catalogError) Text("模型目录加载失败，请重试或使用自定义配置。")
        LiquidButton({ refresh++ }, backdrop, enabled = !loading && !busy) { Text("刷新模型目录") }
        if (model.isNotBlank()) {
            Text(model, Modifier.testTag("model-selected"))
            LiquidButton({ picking = !picking }, backdrop, enabled = !busy) { Text("选择模型") }
        }
        if (picking) {
            val vendors = models.map { it.vendor }.distinct()
            var vendorMenu by remember { mutableStateOf(false) }
            Box {
                LiquidButton({ vendorMenu = true }, backdrop, enabled = !busy) { Text("供应商：${vendor.ifBlank { "全部" }}") }
                DropdownMenu(vendorMenu, { vendorMenu = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                    vendors.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            vendorMenu = false
                            vendor = option
                            search = ""
                            models.firstOrNull { it.vendor == option }?.let(::select)
                            picking = true
                        })
                    }
                }
            }
            Text("搜索模型（按发布时间由新到旧）")
            XNoteTextField(search, { search = it }, Modifier.testTag("model-search"))
            models.filter { (vendor.isBlank() || it.vendor == vendor) &&
                (it.name.contains(search, true) || it.id.contains(search, true)) }.take(30).forEach { option ->
                LiquidButton({ select(option) }, backdrop, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(option.name) }
            }
            if (!loading && models.isNotEmpty()) Text("最多显示 30 项，可通过供应商和搜索缩小范围。", style = MaterialTheme.typography.bodySmall)
        }
    }
    Text("配置名称")
    XNoteTextField(name, { name = it }, Modifier.testTag("model-name"))
    if (preset) {
        Text("接口协议：${ModelProtocol.OpenAI.label}")
        Text(OpenRouterBaseUrl, Modifier.testTag("model-preset-url"))
    } else {
        Text("接口协议")
        ModelProtocol.entries.forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(protocol == option, { protocol = option; root = option.root })
                Text(option.label)
            }
        }
        Text("服务根地址（含 API 版本路径）")
        XNoteTextField(root, { root = it }, Modifier.testTag("model-url"), keyboardType = KeyboardType.Uri)
        Text("模型 ID")
        XNoteTextField(model, { model = it }, Modifier.testTag("model-id"))
    }
    val needsSecret = isNew || (if (preset) OpenRouterBaseUrl else root.trim().trimEnd('/')) != initial.baseUrl
    Text(if (needsSecret) "API Key（此服务需要填写凭据）" else "API Key（留空保留现有凭据）")
    XNoteTextField(secret, { secret = it }, Modifier.testTag("model-key"), visualTransformation = PasswordVisualTransformation(), keyboardType = KeyboardType.Password)
    Text("模型上下文容量（Token）")
    XNoteTextField(context, { context = it }, Modifier.testTag("model-context"), keyboardType = KeyboardType.Number)
    Text("最大输出（Token，含推理）")
    XNoteTextField(output, { output = it }, Modifier.testTag("model-output"), keyboardType = KeyboardType.Number)
    Text("容量应依据服务文档填写；默认预算只用于本地估算，不代表模型已验证的容量。", style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled, { enabled = it }); Text("启用") }
    LiquidButton({ onSave(initial.copy(usesPreset = preset, name = name.trim(), protocol = if (preset) ModelProtocol.OpenAI else protocol, baseUrl = if (preset) OpenRouterBaseUrl else root.trim(), modelId = model.trim(), enabled = enabled,
        contextTokens = context.toIntOrNull() ?: 0, outputTokens = output.toIntOrNull() ?: 0), secret.takeIf { it.isNotEmpty() }) }, backdrop, enabled = !busy && model.isNotBlank() && (!needsSecret || secret.isNotBlank()),
        modifier = Modifier.fillMaxWidth().testTag("model-save")) { Text("保存配置") }
    LiquidButton(onCancel, backdrop, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("取消") }
}
