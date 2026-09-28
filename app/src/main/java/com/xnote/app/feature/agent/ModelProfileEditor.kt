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
internal fun ModelProfileForm(initial: ModelProfile, isNew: Boolean, backdrop: Backdrop, catalog: ModelCatalog, selectState: XNoteSelectState, busy: Boolean, saving: Boolean, onNotice: (String) -> Unit, onCancel: () -> Unit, onSave: (ModelProfile, String?) -> Unit) {
    // -- State and Variables

    var advanced by remember { mutableStateOf(false) }
    val notice by rememberUpdatedState(onNotice)
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
    // -- Derived Values

    val needsSecret = isNew || root.trim().trimEnd('/') != initial.baseUrl.trimEnd('/')

    // -- Functions

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
    fun save() {
        val profile = initial.copy(usesPreset = preset, name = name.trim().ifBlank { model.trim() },
            protocol = protocol, baseUrl = root.trim(), providerId = if (preset) provider?.id else null,
            providerName = if (preset) provider?.name else null, modelId = model.trim(), enabled = enabled,
            contextTokens = context.toIntOrNull() ?: 0, outputTokens = output.toIntOrNull() ?: 0)
        val key = secret.trim()
        val problem = when {
            model.isBlank() -> "请先选择或填写模型"
            needsSecret && key.isBlank() -> "请填写 API Key"
            key.any { it == '\r' || it == '\n' } -> "API Key 不能包含换行"
            profile.contextTokens !in 4096..2_000_000 -> "上下文容量应为 4096–2000000"
            profile.outputTokens !in 256..65536 -> "最大输出应为 256–65536"
            profile.outputTokens + ModelLimits.ToolReserveTokens >= profile.contextTokens -> "上下文容量需大于最大输出与 1024 Token 预留之和"
            else -> try { profile.validate(); null } catch (_: ModelException) { "请检查服务地址和模型 ID" }
        }
        if (problem != null) notice(problem) else onSave(profile, key.takeIf { it.isNotEmpty() })
    }

    // -- Lifecycle Hooks

    LaunchedEffect(preset, refresh) {
        if (preset) {
            loading = true
            catalogError = false
            try {
                models = catalog.load()
                if (models.isEmpty()) { catalogError = true; notice("暂无可用模型，请重试或使用自定义配置") }
                if (model.isBlank()) models.firstOrNull { provider == null || it.provider.id == provider?.id }?.let(::select)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { catalogError = true; notice("模型列表获取失败，请重试或使用自定义配置") }
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
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("model-loading"))
        if (catalogError) LiquidButton({ refresh++ }, backdrop, enabled = !loading && !busy) { Text("重试") }
    } else {
        Text("接口协议")
        XNoteSelectField("接口类型", protocol.name, protocol.label,
            ModelProtocol.entries.map { XNoteSelectOption(it.name, it.label) }, selectState, backdrop,
            onSelect = { id -> protocol = ModelProtocol.valueOf(id); root = protocol.root; secret = "" },
            modifier = Modifier.testTag("model-protocol"), enabled = !busy)
        Text("服务地址")
        XNoteTextField(root, { root = it }, Modifier.testTag("model-url"), enabled = !busy, keyboardType = KeyboardType.Uri)
        Text("模型 ID")
        XNoteTextField(model, { model = it }, Modifier.testTag("model-id"), enabled = !busy)
    }
    Text("API Key")
    XNoteTextField(secret, { secret = it }, Modifier.testTag("model-key"),
        placeholder = if (needsSecret) "填写 API Key" else "留空保留现有密钥", enabled = !busy,
        visualTransformation = PasswordVisualTransformation(), keyboardType = KeyboardType.Password)
    LiquidButton({ advanced = !advanced }, backdrop, enabled = !busy,
        modifier = Modifier.fillMaxWidth().testTag("model-advanced")) { Text(if (advanced) "收起高级设置" else "高级设置") }
    if (advanced) {
        Text("配置名称")
        XNoteTextField(name, { name = it }, Modifier.testTag("model-name"), placeholder = "默认使用模型名称", enabled = !busy)
        if (preset) {
            Text(protocol.label, style = MaterialTheme.typography.bodySmall)
            Text(root, Modifier.testTag("model-preset-url"), style = MaterialTheme.typography.bodySmall)
            if (!catalogError) LiquidButton({ refresh++ }, backdrop, enabled = !loading && !busy) { Text("刷新模型列表") }
        }
        Text("上下文容量（Token）")
        XNoteTextField(context, { context = it }, Modifier.testTag("model-context"), enabled = !busy, keyboardType = KeyboardType.Number)
        Text("最大输出（Token）")
        XNoteTextField(output, { output = it }, Modifier.testTag("model-output"), enabled = !busy, keyboardType = KeyboardType.Number)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("启用", Modifier.weight(1f))
            Switch(enabled, { enabled = it }, enabled = !busy)
        }
    }
    LiquidButton(::save, backdrop, enabled = !busy, tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().testTag("model-save")) {
        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(if (saving) "保存中…" else "保存配置")
    }
    LiquidButton(onCancel, backdrop, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("取消") }
}
