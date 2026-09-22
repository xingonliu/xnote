package com.xnote.app.feature.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.data.agent.*
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

// -- Functions

@Composable
fun ModelSettingsScreen(store: ModelProfileStore, client: ModelClient, onBack: () -> Unit) {
    val profiles by store.profiles.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    var editing by remember { mutableStateOf<ModelProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val edges = setOf(XNoteScrollEdge.Top, XNoteScrollEdge.Bottom)
    BackHandler { if (editing != null) editing = null else onBack() }
    XNotePageScaffold(backdrop = backdrop, scrollEdges = edges, alwaysVisibleScrollEdges = edges, content = {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(
            start = 24.dp, end = 24.dp, top = insets.calculateTopPadding() + XNoteHeaderHeight + 16.dp,
            bottom = insets.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("对话与记忆共用默认配置。服务地址须遵循所选协议；模型 ID 可手填。", style = MaterialTheme.typography.bodyMedium)
            error?.let { Text(it, Modifier.testTag("model-status"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val selected = editing
            if (selected != null) key(selected.id) {
                ModelProfileForm(selected, profiles.none { it.id == selected.id }, backdrop, onCancel = { editing = null }) { profile, key ->
                    scope.launch {
                        try { store.save(profile, key); editing = null; error = "配置已保存。" }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = safeModelError(failure) }
                    }
                }
            } else {
                LiquidButton({ editing = ModelProfile(UUID.randomUUID().toString(), name = "", protocol = ModelProtocol.OpenAI, modelId = "", isDefault = profiles.isEmpty()) },
                    backdrop, enabled = testing == null, modifier = Modifier.fillMaxWidth().testTag("model-add")) { Text("新增配置") }
                if (profiles.isEmpty()) Text("尚未配置模型。添加配置后可在 Agent 中发送文字。")
                profiles.forEach { profile ->
                    XNoteGroupCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(profile.name + if (profile.isDefault) " · 默认" else "", style = MaterialTheme.typography.titleMedium)
                            Text(profile.protocol.label)
                            Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall)
                            Text("${profile.modelId} · ${if (profile.enabled) "已启用" else "已停用"}")
                            Text("文字流式：${if (profile.capabilities.textStreaming) "已验证" else "未验证"} · 工具：${if (profile.capabilities.tools) "已验证" else "未验证"}")
                            Text("能力测试会发送至该服务，最多进行三次请求，可能产生服务费用。", style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LiquidButton({ editing = profile }, backdrop, enabled = testing == null) { Text("编辑") }
                                LiquidButton({ scope.launch {
                                    try { store.save(profile.copy(isDefault = true), null); error = "已设为默认配置。" }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (failure: Exception) { error = safeModelError(failure) }
                                } }, backdrop, enabled = testing == null && profile.enabled && !profile.isDefault) { Text("设为默认") }
                                LiquidButton({ scope.launch {
                                    try { store.delete(profile.id); error = "配置已删除。" }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (failure: Exception) { error = safeModelError(failure) }
                                } }, backdrop, enabled = testing == null) { Text("删除") }
                                LiquidButton({
                                    testing = profile.id
                                    job = scope.launch {
                                        try { error = ModelCapabilityTest(client, store).test(profile).detail }
                                        catch (cancelled: CancellationException) { error = "测试已取消。"; throw cancelled }
                                        catch (failure: Exception) { error = safeModelError(failure) }
                                        finally { testing = null }
                                    }
                                }, backdrop, enabled = testing == null && profile.enabled) { Text("测试连接与能力") }
                                if (testing == profile.id) LiquidButton({ job?.cancel() }, backdrop) { Text("取消测试") }
                            }
                        }
                    }
                }
            }
        }
    }, overlay = {
        XNoteHeader("模型与服务商", backdrop, onBack = { if (editing != null) editing = null else onBack() }, modifier = Modifier.align(Alignment.TopCenter))
    })
}

@Composable
private fun ModelProfileForm(initial: ModelProfile, isNew: Boolean, backdrop: Backdrop, onCancel: () -> Unit, onSave: (ModelProfile, String?) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var protocol by remember { mutableStateOf(initial.protocol) }
    var root by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.modelId) }
    var secret by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var context by remember { mutableStateOf(initial.contextTokens.toString()) }
    var output by remember { mutableStateOf(initial.outputTokens.toString()) }
    Text("配置名称")
    XNoteTextField(name, { name = it }, Modifier.testTag("model-name"))
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
    Text(if (isNew) "API Key" else "API Key（留空保留现有凭据）")
    XNoteTextField(secret, { secret = it }, Modifier.testTag("model-key"), visualTransformation = PasswordVisualTransformation(), keyboardType = KeyboardType.Password)
    Text("模型上下文容量（Token）")
    XNoteTextField(context, { context = it }, Modifier.testTag("model-context"), keyboardType = KeyboardType.Number)
    Text("最大输出（Token，含推理）")
    XNoteTextField(output, { output = it }, Modifier.testTag("model-output"), keyboardType = KeyboardType.Number)
    Text("容量应依据服务文档填写；默认预算只用于本地估算，不代表模型已验证的容量。", style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled, { enabled = it }); Text("启用") }
    LiquidButton({ onSave(initial.copy(name = name.trim(), protocol = protocol, baseUrl = root.trim(), modelId = model.trim(), enabled = enabled,
        contextTokens = context.toIntOrNull() ?: 0, outputTokens = output.toIntOrNull() ?: 0), secret.takeIf { it.isNotEmpty() }) }, backdrop,
        modifier = Modifier.fillMaxWidth().testTag("model-save")) { Text("保存配置") }
    LiquidButton(onCancel, backdrop, modifier = Modifier.fillMaxWidth()) { Text("取消") }
}
