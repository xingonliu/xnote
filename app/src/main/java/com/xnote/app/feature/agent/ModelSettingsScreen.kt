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
import androidx.compose.ui.unit.dp
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
fun ModelSettingsScreen(
    store: ModelProfileStore,
    client: ModelClient,
    catalog: ModelCatalog = remember { OfficialModelCatalog() },
    onBack: () -> Unit,
) {
    val profiles by store.profiles.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val selectState = rememberXNoteSelectState()
    var editing by remember { mutableStateOf<ModelProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val selected = editing
    val saved = profiles.find { it.id == selected?.id }
    val edges = setOf(XNoteScrollEdge.Top)
    fun back() {
        if (saving) return
        selectState.dismiss()
        job?.cancel()
        error = null
        if (editing != null) editing = null else onBack()
    }
    BackHandler { back() }
    key(selected?.id) {
        XNotePageScaffold(backdrop = backdrop, scrollEdges = edges, alwaysVisibleScrollEdges = edges, content = {
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(
                start = 24.dp, end = 24.dp, top = xNoteScrollEdgePadding(insets.calculateTopPadding() + XNoteHeaderHeight),
                bottom = insets.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                error?.let { Text(it, Modifier.testTag("model-status"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (selected == null) {
                    LiquidButton({ error = null; editing = ModelProfile(UUID.randomUUID().toString(), name = "", protocol = ModelProtocol.OpenAI, modelId = "", isDefault = profiles.isEmpty()) },
                        backdrop, modifier = Modifier.fillMaxWidth().testTag("model-add")) { Text("新增配置") }
                    if (profiles.isEmpty()) Text("尚未配置模型。添加配置后可在 Agent 中发送文字。")
                    profiles.forEach { profile ->
                        XNoteGroupCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LiquidButton({ error = null; editing = profile }, backdrop,
                                    modifier = Modifier.fillMaxWidth().testTag("model-profile-${profile.id}")) {
                                    Text(profile.name + if (profile.isDefault) " · 默认" else "")
                                }
                                Text("${profile.modelId} · ${if (profile.enabled) "已启用" else "已停用"}")
                                Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    ModelProfileForm(selected, saved == null, backdrop, catalog, selectState, saving || testing, onCancel = { back() }) { profile, secret ->
                        saving = true
                        scope.launch {
                            try { store.save(profile.copy(isDefault = saved?.isDefault ?: profile.isDefault), secret); editing = null; error = null }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = safeModelError(failure) }
                            finally { saving = false }
                        }
                    }
                    if (saved != null) {
                        HorizontalDivider()
                        Text("已保存配置", style = MaterialTheme.typography.titleMedium)
                        Text("文字流式：${if (saved.capabilities.textStreaming) "已验证" else "未验证"} · 工具：${if (saved.capabilities.tools) "已验证" else "未验证"}")
                        Text("测试使用已保存配置。最多进行三次请求，可能产生服务费用。", style = MaterialTheme.typography.bodySmall)
                        LiquidButton({
                            testing = true
                            job = scope.launch {
                                try { error = ModelCapabilityTest(client, store).test(saved).detail }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { error = safeModelError(failure) }
                                finally { testing = false }
                            }
                        }, backdrop, enabled = !testing && !saving && saved.enabled) { Text("测试连接与能力") }
                        if (testing) LiquidButton({ job?.cancel() }, backdrop) { Text("取消测试") }
                        LiquidButton({
                            saving = true
                            scope.launch {
                                try { store.save(saved.copy(isDefault = true), null); error = "已设为默认配置。" }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { error = safeModelError(failure) }
                                finally { saving = false }
                            }
                        }, backdrop, enabled = !saving && !testing && saved.enabled && !saved.isDefault) { Text("设为默认") }
                        LiquidButton({
                            saving = true
                            scope.launch {
                                try { store.delete(saved.id); editing = null; error = null }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { error = safeModelError(failure) }
                                finally { saving = false }
                            }
                        }, backdrop, enabled = !saving && !testing) { Text("删除") }
                    }
                }
            }
        }, overlay = {
            XNoteHeader(if (selected == null) "模型与服务商" else if (saved == null) "新增配置" else "编辑配置",
                backdrop, onBack = { back() }, modifier = Modifier.align(Alignment.TopCenter))
            XNoteSelectMenu(selectState, backdrop)
        })
    }
}
