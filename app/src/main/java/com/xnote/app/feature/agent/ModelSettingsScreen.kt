package com.xnote.app.feature.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.R
import com.xnote.app.data.agent.*
import com.xnote.app.design.*
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

    // -- Functions

    fun back() {
        if (saving) return
        focus.clearFocus()
        selectState.dismiss()
        job?.cancel()
        if (editing != null) editing = null else onBack()
    }
    fun test(attachments: Boolean) {
        val profile = saved ?: return
        testing = true
        job = scope.launch {
            try {
                if (attachments) toast.show(ModelAttachmentCapabilityTest(client, store).test(profile).detail)
                else toast.show(ModelCapabilityTest(client, store).test(profile).detail)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { toast.show(safeModelError(failure)) }
            finally { testing = false }
        }
    }

    // -- Listeners

    BackHandler { back() }
    key(selected?.id) {
        val scrollState = rememberScrollState()
        XNotePageScaffold(backdrop, scrollEdgeState = rememberXNoteScrollEdgeState(scrollState), content = {
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxSize().imePadding().verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp,
                    top = xNoteScrollEdgePadding(insets.calculateTopPadding() + XNoteHeaderHeight),
                    bottom = insets.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (selected == null) {
                    XNoteSettingsSection("模型配置") {
                        if (profiles.isEmpty()) XNoteSettingsRow("还没有模型配置", summary = "添加模型，让 Agent 帮你整理笔记。")
                        profiles.forEachIndexed { index, profile ->
                            if (index > 0) XNoteInsetDivider()
                            XNoteSettingsRow(profile.name + if (profile.isDefault) " · 默认" else "",
                                Modifier.testTag("model-profile-${profile.id}"),
                                summary = profile.modelId + if (!profile.enabled) " · 已停用" else "", onClick = { editing = profile })
                        }
                        if (profiles.isNotEmpty()) XNoteInsetDivider()
                        XNoteSettingsRow("新增配置", Modifier.testTag("model-add"), icon = R.drawable.ic_keyline_stroke_plus,
                            onClick = { editing = ModelProfile(UUID.randomUUID().toString(), name = "", protocol = ModelProtocol.OpenAI,
                                modelId = "", isDefault = profiles.isEmpty()) })
                    }
                } else {
                    ModelProfileForm(selected, saved == null, catalog, selectState, saving || testing, saving,
                        onNotice = toast::show) { profile, secret ->
                        focus.clearFocus()
                        saving = true
                        scope.launch {
                            try { store.save(profile.copy(isDefault = saved?.isDefault ?: profile.isDefault), secret); editing = null; toast.show("配置已保存") }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { toast.show(safeModelError(failure)) }
                            finally { saving = false }
                        }
                    }
                    if (saved != null) {
                        XNoteSettingsSection("连接与支持", description = "测试会向服务商发送请求，可能产生费用。") {
                            XNoteSettingsRow("连接状态", value = if (saved.capabilities.testedAtEpochMs == null) "未测试"
                                else if (saved.capabilities.textStreaming) "可用" else "连接失败")
                            XNoteInsetDivider()
                            XNoteSettingsRow("图片", value = if (saved.capabilities.images) "已验证" else "未验证")
                            XNoteInsetDivider()
                            XNoteSettingsRow("PDF", value = if (saved.capabilities.pdf) "已验证" else "未验证")
                            XNoteInsetDivider()
                            XNoteSettingsRow("测试连接", showsDisclosure = false, enabled = !testing && !saving && saved.enabled, onClick = { test(false) })
                            XNoteInsetDivider()
                            XNoteSettingsRow("验证图片与 PDF", showsDisclosure = false, enabled = !testing && !saving && saved.enabled, onClick = { test(true) })
                            if (testing) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                XNoteSettingsRow("取消测试", showsDisclosure = false, onClick = { job?.cancel(); toast.show("测试已取消") })
                            }
                        }
                        XNoteSettingsSection("管理") {
                            XNoteSettingsRow(if (saved.isDefault) "当前默认模型" else "设为默认", showsDisclosure = false, enabled = !saving && !testing && saved.enabled && !saved.isDefault,
                                onClick = {
                                    saving = true
                                    scope.launch {
                                        try { store.save(saved.copy(isDefault = true), null); toast.show("已设为默认配置") }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (failure: Exception) { toast.show(safeModelError(failure)) }
                                        finally { saving = false }
                                    }
                                })
                            XNoteInsetDivider()
                            XNoteSettingsRow("删除配置", Modifier.testTag("model-delete"), destructive = true,
                                enabled = !saving && !testing, onClick = { deleting = true })
                        }
                    }
                }
            }
        }, overlay = { glass ->
            XNoteHeader(if (selected == null) "模型与服务商" else if (saved == null) "新增配置" else "编辑配置",
                glass, onBack = ::back, modifier = Modifier.align(Alignment.TopCenter))
            XNoteSelectMenu(selectState, glass)
            XNoteDialog(deleting, { if (!saving) deleting = false }, "删除模型配置？", glass,
                XNoteDialogAction("删除", {
                    saved?.let { profile ->
                        saving = true
                        scope.launch {
                            try { store.delete(profile.id); deleting = false; editing = null; toast.show("配置已删除") }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { toast.show(safeModelError(failure)) }
                            finally { saving = false }
                        }
                    }
                }, enabled = !saving, destructive = true),
                dismissAction = XNoteDialogAction("取消", { deleting = false }, enabled = !saving)) {
                Text("“${saved?.name.orEmpty()}”及其密钥将被移除，聊天记录会保留。")
            }
        })
    }
}
