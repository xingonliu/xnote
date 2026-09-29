package com.xnote.app.feature.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.R
import com.xnote.app.data.agent.CatalogProvider
import com.xnote.app.data.agent.ModelAttachmentCapabilityTest
import com.xnote.app.data.agent.ModelCapabilityTest
import com.xnote.app.data.agent.ModelCatalog
import com.xnote.app.data.agent.ModelClient
import com.xnote.app.data.agent.ModelProfileStore
import com.xnote.app.data.agent.OfficialModelCatalog
import com.xnote.app.domain.agent.safeModelError
import com.xnote.app.design.LocalXNoteToast
import com.xnote.app.design.XNoteButton
import com.xnote.app.design.XNoteDialog
import com.xnote.app.design.XNoteDialogAction
import com.xnote.app.design.XNoteEmptyState
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteHeader
import com.xnote.app.design.XNoteHeaderHeight
import com.xnote.app.design.XNoteInsetDivider
import com.xnote.app.design.XNotePageScaffold
import com.xnote.app.design.XNoteScrollEdge
import com.xnote.app.design.XNoteSelectMenu
import com.xnote.app.design.XNoteSettingRow
import com.xnote.app.design.rememberXNoteSelectState
import com.xnote.app.design.xNoteScrollEdgePadding
import com.xnote.app.domain.agent.ModelProfile
import com.xnote.app.domain.agent.ModelProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

// -- Functions

@Composable
fun ModelSettingsScreen(
    store: ModelProfileStore,
    client: ModelClient,
    catalog: ModelCatalog = remember { OfficialModelCatalog() },
    onBack: () -> Unit,
) {
    // -- State and Variables

    val toast = LocalXNoteToast.current
    val focus = LocalFocusManager.current
    val profiles by store.profiles.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val selectState = rememberXNoteSelectState()
    var editing by remember { mutableStateOf<ModelProfile?>(null) }
    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    // -- Derived Values

    val selected = editing
    val saved = profiles.find { it.id == selected?.id }
    val edges = setOf(XNoteScrollEdge.Top)

    // -- Functions

    fun notify(message: String) {
        toast.show(message)
    }

    fun back() {
        if (saving) return
        selectState.dismiss()
        job?.cancel()
        if (editing != null) editing = null else onBack()
    }

    // -- Listeners

    BackHandler { back() }

    key(selected?.id) {
        XNotePageScaffold(
            backdrop = backdrop,
            scrollEdges = edges,
            alwaysVisibleScrollEdges = edges,
            content = {
                val insets = WindowInsets.safeDrawing.asPaddingValues()
                Column(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            top = xNoteScrollEdgePadding(insets.calculateTopPadding() + XNoteHeaderHeight),
                            bottom = insets.calculateBottomPadding() + 24.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (selected == null) {
                        // Profile List View
                        XNoteButton(
                            onClick = {
                                editing = ModelProfile(
                                    id = UUID.randomUUID().toString(),
                                    name = "",
                                    protocol = ModelProtocol.OpenAI,
                                    modelId = "",
                                    isDefault = profiles.isEmpty(),
                                )
                            },
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("model-add"),
                        ) {
                            Text("新增配置")
                        }

                        if (profiles.isEmpty()) {
                            XNoteEmptyState(
                                title = "还没有模型配置",
                                description = "添加 OpenAI、Anthropic 或 Gemini 模型配置以启用 Agent 对话与分析能力",
                                backdrop = backdrop,
                                iconRes = R.drawable.ic_keyline_stroke_code,
                            )
                        } else {
                            Text(
                                "已配置服务商",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            XNoteGroupCard(Modifier.fillMaxWidth()) {
                                profiles.forEachIndexed { index, profile ->
                                    if (index > 0) {
                                        XNoteInsetDivider(startIndent = 16.dp)
                                    }
                                    XNoteSettingRow(
                                        title = profile.name.ifBlank { profile.modelId },
                                        summary = "${profile.modelId} · ${if (profile.enabled) "已启用" else "已停用"}",
                                        trailingText = if (profile.isDefault) "默认" else null,
                                        iconRes = R.drawable.ic_keyline_stroke_code,
                                        iconTint = if (profile.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        iconContainerColor = if (profile.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                        testTag = "model-profile-${profile.id}",
                                        onClick = { editing = profile },
                                    )
                                }
                            }
                        }
                    } else {
                        // Profile Form View
                        ModelProfileForm(
                            initial = selected,
                            isNew = saved == null,
                            catalog = catalog,
                            selectState = selectState,
                            busy = saving || testing,
                            saving = saving,
                            onNotice = ::notify,
                            onCancel = { back() },
                        ) { profile, secret ->
                            focus.clearFocus()
                            saving = true
                            scope.launch {
                                try {
                                    store.save(profile.copy(isDefault = saved?.isDefault ?: profile.isDefault), secret)
                                    editing = null
                                    notify("配置已保存")
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    notify(safeModelError(failure))
                                } finally {
                                    saving = false
                                }
                            }
                        }

                        if (saved != null) {
                            Text(
                                "连接测试与能力验证",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            XNoteGroupCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "连接测试仅检查模型可用性，工具按权限默认启用。测试可能产生费用。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("连接状态", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            if (saved.capabilities.testedAtEpochMs == null) "未测试"
                                            else if (saved.capabilities.textStreaming) "可用" else "测试失败",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (saved.capabilities.textStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    XNoteInsetDivider(startIndent = 0.dp)
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("多模态输入", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "图片：${if (saved.capabilities.images) "已验证" else "未验证"} · PDF：${if (saved.capabilities.pdf) "已验证" else "未验证"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        XNoteButton(
                                            onClick = {
                                                testing = true
                                                job = scope.launch {
                                                    try {
                                                        notify(ModelCapabilityTest(client, store).test(saved).detail)
                                                    } catch (cancelled: CancellationException) {
                                                        throw cancelled
                                                    } catch (failure: Exception) {
                                                        notify(safeModelError(failure))
                                                    } finally {
                                                        testing = false
                                                    }
                                                }
                                            },
                                            enabled = !testing && !saving && saved.enabled,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(if (testing) "测试中…" else "测试连接")
                                        }
                                        XNoteButton(
                                            onClick = {
                                                testing = true
                                                job = scope.launch {
                                                    try {
                                                        notify(ModelAttachmentCapabilityTest(client, store).test(saved).detail)
                                                    } catch (cancelled: CancellationException) {
                                                        throw cancelled
                                                    } catch (failure: Exception) {
                                                        notify(safeModelError(failure))
                                                    } finally {
                                                        testing = false
                                                    }
                                                }
                                            },
                                            enabled = !testing && !saving && saved.enabled,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("验证附件能力")
                                        }
                                    }
                                    if (testing) {
                                        XNoteButton(
                                            onClick = { job?.cancel(); notify("测试已取消") },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("取消测试")
                                        }
                                    }
                                }
                            }

                            Text(
                                "操作与偏好",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            XNoteGroupCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (!saved.isDefault) {
                                        XNoteButton(
                                            onClick = {
                                                saving = true
                                                scope.launch {
                                                    try {
                                                        store.save(saved.copy(isDefault = true), null)
                                                        notify("已设为默认配置")
                                                    } catch (cancelled: CancellationException) {
                                                        throw cancelled
                                                    } catch (failure: Exception) {
                                                        notify(safeModelError(failure))
                                                    } finally {
                                                        saving = false
                                                    }
                                                }
                                            },
                                            enabled = !saving && !testing && saved.enabled,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("设为默认配置")
                                        }
                                    }
                                    XNoteButton(
                                        onClick = { deleting = true },
                                        enabled = !saving && !testing,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("删除配置", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            overlay = {
                XNoteHeader(
                    title = if (selected == null) "模型与服务商" else if (saved == null) "新增配置" else "编辑配置",
                    backdrop = backdrop,
                    onBack = { back() },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                XNoteSelectMenu(selectState, backdrop)

                saved?.let { currentProfile ->
                    XNoteDialog(
                        visible = deleting,
                        onDismissRequest = { deleting = false },
                        title = "删除配置？",
                        backdrop = backdrop,
                        confirmAction = XNoteDialogAction(
                            label = "删除",
                            destructive = true,
                            onClick = {
                                saving = true
                                deleting = false
                                scope.launch {
                                    try {
                                        store.delete(currentProfile.id)
                                        editing = null
                                        notify("配置已删除")
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        notify(safeModelError(failure))
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                        ),
                        dismissAction = XNoteDialogAction("取消", { deleting = false }),
                    ) {
                        Text("删除后将移除保存的 API Key。")
                    }
                }
            },
        )
    }
}
