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
internal fun ModelProfileForm(initial: ModelProfile, isNew: Boolean, backdrop: Backdrop, catalog: ModelCatalog, selectState: XNoteSelectState, busy: Boolean, onCancel: () -> Unit, onSave: (ModelProfile, String?) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var protocol by remember { mutableStateOf(initial.protocol) }
    var root by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.modelId) }
    var secret by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var context by remember { mutableStateOf(initial.contextTokens.toString()) }
    var output by remember { mutableStateOf(initial.outputTokens.toString()) }
    var preset by remember { mutableStateOf(isNew || (initial.usesPreset && initial.providerId != null)) }
    var models by remember { mutableStateOf<List<CatalogModel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var provider by remember { mutableStateOf(initial.providerId?.let {
        CatalogProvider(it, initial.providerName ?: it, initial.protocol, initial.baseUrl)
    }) }
    fun select(value: CatalogModel) {
        if (name.isBlank() || name == models.find { it.id == model }?.name) name = value.name
        model = value.id
        if (root != value.provider.baseUrl) secret = ""
        provider = value.provider
        root = value.provider.baseUrl
        protocol = value.provider.protocol
        context = value.contextTokens.toString()
        output = value.outputTokens.toString()
    }
    LaunchedEffect(preset, refresh) {
        if (preset) {
            loading = true
            catalogError = false
            try {
                models = catalog.load()
                if (model.isBlank()) models.firstOrNull { provider == null || it.provider.id == provider?.id }?.let(::select)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { catalogError = true }
            finally { loading = false }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(preset, {
            if (!preset) { preset = true; model = ""; secret = "" }
        }, label = { Text("预设") }, enabled = !busy, modifier = Modifier.testTag("model-preset"))
        FilterChip(!preset, {
            if (preset) { preset = false; model = ""; root = protocol.root; secret = "" }
        }, label = { Text("自定义") }, enabled = !busy, modifier = Modifier.testTag("model-custom"))
    }
    if (preset) {
        Text("选择厂商与模型，填写该厂商的 API Key。")
        val providers = models.map { it.provider }.distinctBy { it.id }
        XNoteSelectField("厂商", provider?.id.orEmpty(), provider?.name.orEmpty(),
            providers.map { XNoteSelectOption(it.id, it.name) }, selectState, backdrop,
            onSelect = { id ->
                if (provider?.id != id) {
                    secret = ""
                    name = ""
                    models.firstOrNull { it.provider.id == id }?.let(::select)
                }
            }, modifier = Modifier.testTag("model-provider"), enabled = !busy && !loading)
        val choices = models.filter { it.provider.id == provider?.id }
        XNoteSelectField("模型", model, choices.find { it.id == model }?.name ?: model,
            choices.map { XNoteSelectOption(it.id, it.name + " · " + it.id) }, selectState, backdrop,
            onSelect = { id -> choices.find { it.id == id }?.let(::select) },
            modifier = Modifier.testTag("model-picker"), enabled = !busy && !loading)
        if (model.isNotBlank()) Text(model, Modifier.testTag("model-selected"), style = MaterialTheme.typography.bodySmall)
        if (loading) Text("正在加载模型目录…")
        if (catalogError) Text("模型目录加载失败，请重试或使用自定义配置。")
        if (!loading && !catalogError && choices.isEmpty()) Text("暂无可用模型，请刷新或使用自定义配置。")
        LiquidButton({ refresh++ }, backdrop, enabled = !loading && !busy) { Text("刷新模型目录") }
    }
    Text("配置名称")
    XNoteTextField(name, { name = it }, Modifier.testTag("model-name"))
    if (preset) {
        Text("接口协议：${protocol.label}")
        Text(root, Modifier.testTag("model-preset-url"))
    } else {
        Text("接口协议")
        XNoteSelectField("接口类型", protocol.name, protocol.label,
            ModelProtocol.entries.map { XNoteSelectOption(it.name, it.label) }, selectState, backdrop,
            onSelect = { id -> protocol = ModelProtocol.valueOf(id); root = protocol.root; secret = "" },
            modifier = Modifier.testTag("model-protocol"), enabled = !busy)
        Text("服务根地址（含 API 版本路径）")
        XNoteTextField(root, { root = it }, Modifier.testTag("model-url"), keyboardType = KeyboardType.Uri)
        Text("模型 ID")
        XNoteTextField(model, { model = it }, Modifier.testTag("model-id"))
    }
    val needsSecret = isNew || root.trim().trimEnd('/') != initial.baseUrl
    Text(if (needsSecret) "API Key（此服务需要填写凭据）" else "API Key（留空保留现有凭据）")
    XNoteTextField(secret, { secret = it }, Modifier.testTag("model-key"), visualTransformation = PasswordVisualTransformation(), keyboardType = KeyboardType.Password)
    Text("模型上下文容量（Token）")
    XNoteTextField(context, { context = it }, Modifier.testTag("model-context"), keyboardType = KeyboardType.Number)
    Text("最大输出（Token，含推理）")
    XNoteTextField(output, { output = it }, Modifier.testTag("model-output"), keyboardType = KeyboardType.Number)
    Text("容量应依据服务文档填写；默认预算只用于本地估算，不代表模型已验证的容量。", style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled, { enabled = it }); Text("启用") }
    LiquidButton({ onSave(initial.copy(usesPreset = preset, name = name.trim(), protocol = protocol, baseUrl = root.trim(), providerId = if (preset) provider?.id else null, providerName = if (preset) provider?.name else null, modelId = model.trim(), enabled = enabled,
        contextTokens = context.toIntOrNull() ?: 0, outputTokens = output.toIntOrNull() ?: 0), secret.takeIf { it.isNotEmpty() }) }, backdrop, enabled = !busy && model.isNotBlank() && (!needsSecret || secret.isNotBlank()),
        modifier = Modifier.fillMaxWidth().testTag("model-save")) { Text("保存配置") }
    LiquidButton(onCancel, backdrop, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("取消") }
}
