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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.xnote.app.R
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
    // -- State and Variables

    val toast = rememberXNoteToastHostState()
    val focus = LocalFocusManager.current
    val profiles by store.profiles.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val selectState = rememberXNoteSelectState()
    var editing by remember { mutableStateOf<ModelProfile?>(null) }
    var testing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    // -- Derived Values

    val selected = editing
    val saved = profiles.find { it.id == selected?.id }
    val edges = setOf(XNoteScrollEdge.Top)
    // -- Functions

    fun notify(message: String) {
        scope.launch {
            toast.currentSnackbarData?.dismiss()
            toast.showSnackbar(message)
        }
    }

    fun back() {
        if (saving) return
        selectState.dismiss()
        job?.cancel()
        toast.currentSnackbarData?.dismiss()
        if (editing != null) editing = null else onBack()
    }
    // -- Listeners

    BackHandler { back() }
    key(selected?.id) {
        XNotePageScaffold(backdrop = backdrop, scrollEdges = edges, alwaysVisibleScrollEdges = edges, content = {
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(
                start = 24.dp, end = 24.dp, top = xNoteScrollEdgePadding(insets.calculateTopPadding() + XNoteHeaderHeight),
                bottom = insets.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (selected == null) {
                    LiquidButton({ toast.currentSnackbarData?.dismiss(); editing = ModelProfile(UUID.randomUUID().toString(), name = "", protocol = ModelProtocol.OpenAI, modelId = "", isDefault = profiles.isEmpty()) },
                        backdrop, modifier = Modifier.fillMaxWidth().testTag("model-add")) { Text("新增配置") }
                    if (profiles.isEmpty()) Text("还没有模型配置")
                    profiles.forEach { profile ->
                        XNoteGroupCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LiquidButton({ toast.currentSnackbarData?.dismiss(); editing = profile }, backdrop,
                                    modifier = Modifier.fillMaxWidth().testTag("model-profile-${profile.id}")) {
                                    Text(profile.name + if (profile.isDefault) " · 默认" else "")
                                }
                                Text("${profile.modelId} · ${if (profile.enabled) "已启用" else "已停用"}")
                            }
                        }
                    }
                } else {
                    ModelProfileForm(selected, saved == null, backdrop, catalog, selectState, saving || testing, saving, onNotice = ::notify, onCancel = { back() }) { profile, secret ->
                        focus.clearFocus()
                        saving = true
                        scope.launch {
                            try { store.save(profile.copy(isDefault = saved?.isDefault ?: profile.isDefault), secret); editing = null; notify("配置已保存") }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { notify(safeModelError(failure)) }
                            finally { saving = false }
                        }
                    }
                    if (saved != null) {
                        HorizontalDivider()
                        Text("测试已保存配置，可能产生费用", style = MaterialTheme.typography.bodySmall)
                        LiquidButton({
                            testing = true
                            job = scope.launch {
                                try { notify(ModelCapabilityTest(client, store).test(saved).detail) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { notify(safeModelError(failure)) }
                                finally { testing = false }
                            }
                        }, backdrop, enabled = !testing && !saving && saved.enabled) { Text(if (testing) "测试中…" else "测试连接") }
                        Text("图片：${if (saved.capabilities.images) "已验证" else "未验证"} · PDF 原生输入：${if (saved.capabilities.pdf) "已验证" else "未验证"}", style = MaterialTheme.typography.bodySmall)
                        LiquidButton({
                            testing = true
                            job = scope.launch {
                                try { notify(ModelAttachmentCapabilityTest(client, store).test(saved).detail) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { notify(safeModelError(failure)) }
                                finally { testing = false }
                            }
                        }, backdrop, enabled = !testing && !saving && saved.enabled) { Text("验证图片与 PDF（两次请求）") }
                        if (testing) LiquidButton({ job?.cancel(); notify("测试已取消") }, backdrop) { Text("取消测试") }
                        LiquidButton({
                            saving = true
                            scope.launch {
                                try { store.save(saved.copy(isDefault = true), null); notify("已设为默认配置") }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { notify(safeModelError(failure)) }
                                finally { saving = false }
                            }
                        }, backdrop, enabled = !saving && !testing && saved.enabled && !saved.isDefault) { Text("设为默认") }
                        LiquidButton({
                            saving = true
                            scope.launch {
                                try { store.delete(saved.id); editing = null; notify("配置已删除") }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { notify(safeModelError(failure)) }
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
            XNoteToastHost(toast, backdrop, Modifier.align(Alignment.BottomCenter).imePadding(),
                dismissLabel = stringResource(R.string.toast_dismiss))
        })
    }
}
