package com.xnote.app.feature.creative

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.db.StickerEntity
import com.xnote.app.data.files.*
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.model.newNoteId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// -- Functions

@Composable
fun StickerLibraryScreen(
    library: NoteLibrary,
    onBack: (() -> Unit)? = null,
    onInsert: (suspend (StickerEntity) -> Unit)? = null,
    contentPadding: PaddingValues? = null,
    bottomNavigationVisible: Boolean = false,
    gridState: LazyGridState = rememberLazyGridState(),
    onPrimaryChromeVisible: (Boolean) -> Unit = {},
) {
    // -- State and Variables

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalXNoteToast.current
    val owner = remember { "sticker-library:${newNoteId()}" }
    val addAnchor = rememberXNotePopupAnchor()
    val moreAnchor = rememberXNotePopupAnchor()
    var entries by remember { mutableStateOf<List<StickerEntity>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var sortByName by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var cutout by remember { mutableStateOf<java.io.File?>(null) }
    var cameraName by rememberSaveable { mutableStateOf<String?>(null) }

    // -- Derived Values

    val selected = entries?.find { it.id == selectedId }
    val menuBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        if (bottomNavigationVisible) XNoteBottomNavigationHeight else XNoteBottomNavigationSpacing
    val filtered = remember(entries, search, sortByName) {
        entries.orEmpty().filter { it.name.contains(search, ignoreCase = true) }
            .let { if (sortByName) it.sortedWith(compareBy<StickerEntity> { entry -> entry.name }.thenBy { entry -> entry.id }) else it }
    }

    // -- Functions

    fun back() {
        if (busy) return
        if (selectedId != null) selectedId = null else onBack?.invoke()
    }
    fun import(uri: android.net.Uri?, temporary: String? = null) {
        if (uri == null) {
            temporary?.let { cameraFile(context, it).delete() }
            return
        }
        busy = true
        scope.launch {
            try {
                val attachment = importNoteImage(context, library, uri, owner)
                cutout = library.attachmentFile(attachment)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { toast.show("图片读取失败，请重试") }
            finally { busy = false; temporary?.let { cameraFile(context, it).delete() } }
        }
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { toast.show("操作失败，请重试") }
            finally { busy = false }
        }
    }

    // -- Listeners

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { import(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val temporary = cameraName
        cameraName = null
        if (temporary != null) import(if (success) cameraUri(context, temporary) else null, temporary)
    }
    val takePhoto: () -> Unit = {
        try {
            val temporary = "${newNoteId()}.jpg"
            cameraName = temporary
            cameraFile(context, temporary).parentFile?.mkdirs()
            camera.launch(cameraUri(context, temporary))
        } catch (_: Exception) {
            cameraName?.let { cameraFile(context, it).delete() }
            cameraName = null
            toast.show("相机不可用")
        }
    }

    // -- Lifecycle Hooks

    SideEffect { onPrimaryChromeVisible(selected == null && cutout == null) }
    DisposableEffect(Unit) { onDispose { onPrimaryChromeVisible(true) } }
    DisposableEffect(library, owner) { onDispose { library.releaseSessionAttachments(owner) } }
    LaunchedEffect(library, loadAttempt) {
        loadFailed = false
        try { library.observeStickers().collect { entries = it } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { loadFailed = true }
    }
    val file = cutout
    if (file != null) {
        CutoutScreen(file, library, owner, onBack = { cutout = null })
        return
    }
    CreativePage(if (selected == null) "贴纸库" else "贴纸", if (selected != null || onBack != null) ::back else null,
        contentPadding = contentPadding,
        actions = buildList {
            add(XNoteHeaderAction(R.drawable.ic_keyline_stroke_more_horizontal, if (selected == null) "排序" else "管理贴纸",
                { moreMenu = true }, enabled = !busy, popupAnchor = moreAnchor))
            if (selected == null) add(XNoteHeaderAction(R.drawable.ic_keyline_stroke_plus, "创建贴纸",
                { addMenu = true }, enabled = !busy, popupAnchor = addAnchor))
        }, toolbar = if (selected != null && onInsert != null) {
            { XNoteButton({ action { onInsert(selected) } }, modifier = Modifier.fillMaxWidth(),
                enabled = !busy, tint = MaterialTheme.colorScheme.primary) { Text(if (busy) "正在插入…" else "插入笔记") } }
        } else null,
        overlay = { glass ->
            if (selected == null) LiquidButton(
                onClick = takePhoto, backdrop = glass, enabled = !busy,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(
                    end = if (contentPadding != null) contentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                        else XNoteSpacingMedium,
                    bottom = if (bottomNavigationVisible) XNoteBottomNavigationHeight + XNoteSpacingSmall
                        else XNoteBottomNavigationSpacing,
                ).size(XNoteCreateNoteButtonSize).testTag("xnote-sticker-camera"),
            ) {
                Icon(painterResource(R.drawable.ic_keyline_stroke_camera), "拍照创建贴纸",
                    tint = LocalContentColor.current, modifier = Modifier.size(XNoteIconSizeHero))
            }
            XNoteDropdownMenu(addMenu, { addMenu = false }, listOf(
                XNoteDropdownMenuItem("从相册创建", {
                    try { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    catch (_: Exception) { toast.show("相册不可用") }
                }),
                XNoteDropdownMenuItem("拍照创建", takePhoto),
            ), glass, anchor = addAnchor, bottomInset = menuBottomInset)
            XNoteDropdownMenu(moreMenu, { moreMenu = false }, if (selected == null) listOf(
                XNoteDropdownMenuItem("最近创建", { sortByName = false }, selected = !sortByName),
                XNoteDropdownMenuItem("按名称", { sortByName = true }, selected = sortByName),
            ) else listOf(
                XNoteDropdownMenuItem("重命名", { name = selected.name; renaming = true }),
                XNoteDropdownMenuItem("删除贴纸", { deleting = true }, destructive = true),
            ), glass, anchor = moreAnchor, bottomInset = menuBottomInset)
            XNoteDialog(renaming, { if (!busy) renaming = false }, "重命名贴纸", glass,
                XNoteDialogAction("保存", { selected?.let { entry -> action {
                    library.renameSticker(entry.id, name.trim()); renaming = false; toast.show("名称已保存")
                } } }, enabled = !busy && name.isNotBlank()),
                dismissAction = XNoteDialogAction("取消", { renaming = false }, enabled = !busy)) {
                XNoteTextField(name, { name = it }, Modifier.testTag("xnote-sticker-rename"), placeholder = "贴纸名称", enabled = !busy)
            }
            XNoteDialog(deleting, { if (!busy) deleting = false }, "删除贴纸？", glass,
                XNoteDialogAction("删除", { selected?.let { entry -> action {
                    library.deleteSticker(entry.id); selectedId = null; deleting = false; toast.show("贴纸已删除")
                } } }, enabled = !busy, destructive = true),
                dismissAction = XNoteDialogAction("取消", { deleting = false }, enabled = !busy)) {
                Text("“${selected?.name.orEmpty()}”将从贴纸库移除，已插入笔记的贴纸会保留。")
            }
        }) {
        if (selected != null) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StickerThumbnail(library, selected.attachmentId, Modifier.weight(1f).fillMaxWidth())
                Text(selected.name, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                XNoteTextField(search, { search = it }, Modifier.testTag("xnote-sticker-search"), placeholder = "搜索贴纸")
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (entries != null && filtered.isNotEmpty()) Text("${filtered.size} 张贴纸",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    loadFailed -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("贴纸暂时无法读取")
                            XNoteButton({ loadAttempt++ }) { Text("重试") }
                        }
                    }
                    entries == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                    filtered.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (search.isBlank()) "还没有贴纸" else "没有找到贴纸", style = MaterialTheme.typography.titleMedium)
                            Text(if (search.isBlank()) "将喜欢的图片变成贴纸。" else "试试其他名称。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            XNoteButton({ if (search.isBlank()) addMenu = true else search = "" }, enabled = !busy) {
                                Text(if (search.isBlank()) "创建贴纸" else "清除搜索")
                            }
                        }
                    }
                    else -> LazyVerticalGrid(GridCells.Adaptive(120.dp), Modifier.weight(1f).fillMaxWidth(), state = gridState,
                        contentPadding = PaddingValues(bottom = if (contentPadding == null)
                            XNoteCreateNoteButtonSize + XNoteBottomNavigationSpacing else XNoteSpacingSmall),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(filtered, key = { it.id }) { sticker ->
                            Column(Modifier.clip(XNoteSmoothCornerShape(16.dp)).clickable(role = Role.Button) { selectedId = sticker.id }
                                .testTag("xnote-sticker-${sticker.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StickerThumbnail(library, sticker.attachmentId, Modifier.fillMaxWidth().aspectRatio(1f))
                                Text(sticker.name, Modifier.padding(horizontal = 4.dp), maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StickerThumbnail(library: NoteLibrary, attachmentId: String, modifier: Modifier = Modifier) {
    var bitmap by remember(attachmentId) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(attachmentId) { mutableStateOf(false) }
    LaunchedEffect(library, attachmentId) {
        try {
            val attachment = library.getAttachment(attachmentId) ?: error("Missing attachment")
            bitmap = decodeNoteImage(library.attachmentFile(attachment)).asImageBitmap()
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
    }
    val shape = XNoteSmoothCornerShape(16.dp)
    Box(modifier.clip(shape).border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = .2f), shape), contentAlignment = Alignment.Center) {
        TransparencyGrid(Modifier.matchParentSize())
        bitmap?.let { Image(it, null, Modifier.fillMaxSize().padding(8.dp)) }
            ?: if (failed) Text("图片无法读取", style = MaterialTheme.typography.bodySmall)
            else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}
