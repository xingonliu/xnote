package com.xnote.app.feature.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.agent.CatalogModel
import com.xnote.app.data.agent.CatalogProvider
import com.xnote.app.data.agent.ModelCatalog
import com.xnote.app.design.XNoteButton
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteGroupSectionTitle
import com.xnote.app.design.XNoteSegmentedControl
import com.xnote.app.design.XNoteSelectField
import com.xnote.app.design.XNoteSelectOption
import com.xnote.app.design.XNoteSelectState
import com.xnote.app.design.XNoteTextField
import com.xnote.app.domain.agent.ModelException
import com.xnote.app.domain.agent.ModelLimits
import com.xnote.app.domain.agent.ModelProfile
import com.xnote.app.domain.agent.ModelProtocol
import com.xnote.app.domain.agent.validate
import kotlinx.coroutines.CancellationException

// -- Functions

@Composable
internal fun ModelProfileForm(
    initial: ModelProfile,
    isNew: Boolean,
    catalog: ModelCatalog,
    selectState: XNoteSelectState,
    busy: Boolean,
    saving: Boolean,
    onNotice: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: (ModelProfile, String?) -> Unit,
) {
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
    var provider by remember {
        mutableStateOf(
            initial.providerId?.let {
                CatalogProvider(it, initial.providerName ?: it, initial.protocol, initial.baseUrl)
            },
        )
    }

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
        val profile = initial.copy(
            usesPreset = preset,
            name = name.trim().ifBlank { model.trim() },
            protocol = protocol,
            baseUrl = root.trim(),
            providerId = if (preset) provider?.id else null,
            providerName = if (preset) provider?.name else null,
            modelId = model.trim(),
            enabled = enabled,
            contextTokens = context.toIntOrNull() ?: 0,
            outputTokens = output.toIntOrNull() ?: 0,
        )
        val key = secret.trim()
        val problem = when {
            model.isBlank() -> "请先选择或填写模型"
            needsSecret && key.isBlank() -> "请填写 API Key"
            key.any { it == '\r' || it == '\n' } -> "API Key 不能包含换行"
            profile.contextTokens !in 4096..2_000_000 -> "上下文容量应为 4096–2000000"
            profile.outputTokens !in 256..65536 -> "最大输出应为 256–65536"
            profile.outputTokens + ModelLimits.ToolReserveTokens >= profile.contextTokens -> "上下文容量需大于最大输出与 1024 Token 预留之和"
            else -> try {
                profile.validate()
                null
            } catch (_: ModelException) {
                "请检查服务地址和模型 ID"
            }
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
                if (models.isEmpty()) {
                    catalogError = true
                    notice("暂无可用模型，请重试或使用自定义配置")
                }
                if (model.isBlank()) {
                    models.firstOrNull { provider == null || it.provider.id == provider?.id }?.let(::select)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                catalogError = true
                notice("模型列表获取失败，请重试或使用自定义配置")
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Mode Selector: Segmented Control
        XNoteSegmentedControl(
            items = listOf(true, false),
            selectedItem = preset,
            onItemSelected = { isPreset ->
                if (preset != isPreset) {
                    preset = isPreset
                    model = ""
                    if (!isPreset) root = protocol.root
                    secret = ""
                }
            },
            label = { if (it) "预设服务商" else "自定义接入" },
            itemTestTag = { if (it) "model-preset" else "model-custom" },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )

        // Basic Config Group
        XNoteGroupSectionTitle(title = "模型与服务")
        XNoteGroupCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (preset) {
                    val providers = models.map { it.provider }.distinctBy { it.id }
                    XNoteSelectField(
                        label = "厂商",
                        selectedId = provider?.id.orEmpty(),
                        selectedLabel = provider?.name.orEmpty(),
                        options = providers.map { XNoteSelectOption(it.id, it.name) },
                        state = selectState,
                        onSelect = { id ->
                            if (provider?.id != id) {
                                secret = ""
                                name = ""
                                models.firstOrNull { it.provider.id == id }?.let(::select)
                            }
                        },
                        modifier = Modifier.testTag("model-provider"),
                        enabled = !busy && !loading,
                    )

                    val choices = models.filter { it.provider.id == provider?.id }
                    XNoteSelectField(
                        label = "模型",
                        selectedId = model,
                        selectedLabel = choices.find { it.id == model }?.name ?: model,
                        options = choices.map { XNoteSelectOption(it.id, it.name + " · " + it.id) },
                        state = selectState,
                        onSelect = { id -> choices.find { it.id == id }?.let(::select) },
                        modifier = Modifier.testTag("model-picker"),
                        enabled = !busy && !loading,
                    )

                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().testTag("model-loading"))
                    }
                    if (catalogError) {
                        XNoteButton(
                            onClick = { refresh++ },
                            enabled = !loading && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("重试获取预设模型")
                        }
                    }
                } else {
                    XNoteSelectField(
                        label = "接口协议",
                        selectedId = protocol.name,
                        selectedLabel = protocol.label,
                        options = ModelProtocol.entries.map { XNoteSelectOption(it.name, it.label) },
                        state = selectState,
                        onSelect = { id ->
                            protocol = ModelProtocol.valueOf(id)
                            root = protocol.root
                            secret = ""
                        },
                        modifier = Modifier.testTag("model-protocol"),
                        enabled = !busy,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "服务地址",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        XNoteTextField(
                            value = root,
                            onValueChange = { root = it },
                            placeholder = "https://api.openai.com/v1",
                            modifier = Modifier.fillMaxWidth().testTag("model-url"),
                            enabled = !busy,
                            keyboardType = KeyboardType.Uri,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "模型 ID",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        XNoteTextField(
                            value = model,
                            onValueChange = { model = it },
                            placeholder = "例如 gpt-4o 或 claude-3-5-sonnet",
                            modifier = Modifier.fillMaxWidth().testTag("model-id"),
                            enabled = !busy,
                        )
                    }
                }
            }
        }

        // Credentials Group
        XNoteGroupSectionTitle(title = "鉴权密钥")
        XNoteGroupCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "API Key",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                XNoteTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    placeholder = if (needsSecret) "填写 API Key" else "留空保留现有密钥",
                    modifier = Modifier.fillMaxWidth().testTag("model-key"),
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                )
            }
        }

        // Advanced Settings Button / Collapsible Card
        XNoteButton(
            onClick = { advanced = !advanced },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("model-advanced"),
        ) {
            Text(if (advanced) "收起高级参数设置" else "展开高级参数设置（Token、名称等）")
        }

        AnimatedVisibility(
            visible = advanced,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                XNoteGroupSectionTitle(title = "高级参数")
                XNoteGroupCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "配置展示名称",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            XNoteTextField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = "默认使用模型名称",
                                modifier = Modifier.fillMaxWidth().testTag("model-name"),
                                enabled = !busy,
                            )
                        }

                        if (preset) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = protocol.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = root,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("model-preset-url"),
                                )
                            }
                            if (!catalogError) {
                                XNoteButton(
                                    onClick = { refresh++ },
                                    enabled = !loading && !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("刷新模型列表")
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "上下文容量（Token）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            XNoteTextField(
                                value = context,
                                onValueChange = { context = it },
                                modifier = Modifier.fillMaxWidth().testTag("model-context"),
                                enabled = !busy,
                                keyboardType = KeyboardType.Number,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "最大输出（Token）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            XNoteTextField(
                                value = output,
                                onValueChange = { output = it },
                                modifier = Modifier.fillMaxWidth().testTag("model-output"),
                                enabled = !busy,
                                keyboardType = KeyboardType.Number,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "启用此配置",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "启用后方可在 Agent 对话及笔记辅助中使用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { enabled = it },
                                enabled = !busy,
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            XNoteButton(
                onClick = ::save,
                enabled = !busy,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().testTag("model-save"),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).padding(end = 6.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (saving) "保存中…" else "保存配置")
            }

            XNoteButton(
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("取消")
            }
        }
    }
}
