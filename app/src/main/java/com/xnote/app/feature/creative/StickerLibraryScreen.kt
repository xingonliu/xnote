package com.xnote.app.feature.creative

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xnote.app.data.db.StickerEntity
import com.xnote.app.data.files.*
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteButton
import com.xnote.app.design.XNoteDialog
import com.xnote.app.design.XNoteDialogAction
import com.xnote.app.domain.model.newNoteId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// -- Functions

@Composable
fun StickerLibraryScreen(library: NoteLibrary, onBack: () -> Unit, onInsert: (suspend (StickerEntity) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = remember { SnackbarHostState() }
    val owner = remember { "sticker-library:${newNoteId()}" }
    val entries by remember(library) { library.observeStickers() }.collectAsState(emptyList())
    var search by rememberSaveable { mutableStateOf("") }
    var sortByName by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<StickerEntity?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var cutout by remember { mutableStateOf<java.io.File?>(null) }
    var cameraName by rememberSaveable { mutableStateOf<String?>(null) }
    DisposableEffect(library, owner) { onDispose { library.releaseSessionAttachments(owner) } }
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
            catch (_: Exception) { toast.showSnackbar("图片读取失败，请重试") }
            finally { busy = false; temporary?.let { cameraFile(context, it).delete() } }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { import(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val temporary = cameraName
        cameraName = null
        if (temporary != null) import(if (success) cameraUri(context, temporary) else null, temporary)
    }
    val file = cutout
    if (file != null) {
        CutoutScreen(file, library, owner, onBack = { cutout = null })
        return
    }
    fun action(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { toast.showSnackbar("操作失败，请重试") }
            finally { busy = false }
        }
    }
    CreativePage("贴纸库", { if (selected != null) { selected = null; deleting = false } else onBack() }, toast,
        actions = listOf(com.xnote.app.design.XNoteHeaderAction(com.xnote.app.R.drawable.ic_keyline_stroke_plus, "添加", {
            try { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            catch (_: Exception) { scope.launch { toast.showSnackbar("相册不可用") } }
        }, enabled = !busy)), overlay = { backdrop ->
        XNoteDialog(visible = deleting, onDismissRequest = { deleting = false }, title = "删除贴纸", backdrop = backdrop,
            confirmAction = XNoteDialogAction("删除", enabled = !busy, destructive = true, onClick = {
                selected?.let { entry -> action { library.deleteSticker(entry.id); selected = null; deleting = false } }
            }), dismissAction = XNoteDialogAction("取消", { deleting = false })) {
            Text("删除“${selected?.name.orEmpty()}”？已插入笔记的贴纸会保留。")
        }
    }) {
        val entry = selected
        if (entry != null) {
            StickerThumbnail(library, entry.attachmentId, Modifier.weight(1f).fillMaxWidth())
            BasicTextField(name, { name = it }, Modifier.fillMaxWidth().padding(12.dp).testTag("xnote-sticker-rename"),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), singleLine = true)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XNoteButton({ action { library.renameSticker(entry.id, name); selected = entry.copy(name = name.trim()); toast.showSnackbar("已重命名") } }, enabled = !busy && name.isNotBlank()) { Text("重命名") }
                XNoteButton({ deleting = true }, enabled = !busy) { Text("删除") }
                if (onInsert != null) XNoteButton({ action { onInsert(entry) } }, enabled = !busy) { Text("插入笔记") }
                XNoteButton({ selected = null; deleting = false }, enabled = !busy) { Text("返回列表") }
            }
        } else {
            BasicTextField(search, { search = it }, Modifier.fillMaxWidth().padding(12.dp).testTag("xnote-sticker-search"),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), singleLine = true,
                decorationBox = { inner -> Box { if (search.isEmpty()) Text("搜索贴纸", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
            val filtered = entries.filter { it.name.contains(search, ignoreCase = true) }
                .let { if (sortByName) it.sortedWith(compareBy<StickerEntity> { entry -> entry.name }.thenBy { entry -> entry.id }) else it }
            if (filtered.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (search.isEmpty()) "从图片创建你的第一张贴纸" else "没有匹配的贴纸") }
            else LazyVerticalGrid(GridCells.Adaptive(120.dp), Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.id }) { sticker ->
                    Column(Modifier.clickable { selected = sticker; name = sticker.name; deleting = false }.testTag("xnote-sticker-${sticker.id}")) {
                        StickerThumbnail(library, sticker.attachmentId, Modifier.fillMaxWidth().aspectRatio(1f))
                        Text(sticker.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XNoteButton({ sortByName = !sortByName }, enabled = !busy) { Text(if (sortByName) "按名称" else "最近创建") }
                XNoteButton({
                    try { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    catch (_: Exception) { scope.launch { toast.showSnackbar("相册不可用") } }
                }, enabled = !busy) { Text("相册创建") }
                XNoteButton({
                    try {
                        val temporary = "${newNoteId()}.jpg"
                        cameraName = temporary
                        cameraFile(context, temporary).parentFile?.mkdirs()
                        camera.launch(cameraUri(context, temporary))
                    } catch (_: Exception) { scope.launch { toast.showSnackbar("相机不可用") } }
                }, enabled = !busy) { Text("拍照创建") }
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
    Box(modifier, contentAlignment = Alignment.Center) {
        TransparencyGrid(Modifier.matchParentSize())
        bitmap?.let { Image(it, "贴纸预览", Modifier.fillMaxSize()) }
            ?: Text(if (failed) "图片无法读取" else "加载中")
    }
}
