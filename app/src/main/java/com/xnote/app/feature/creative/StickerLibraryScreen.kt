package com.xnote.app.feature.creative

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.db.StickerEntity
import com.xnote.app.data.files.cameraFile
import com.xnote.app.data.files.cameraUri
import com.xnote.app.data.files.decodeNoteImage
import com.xnote.app.data.files.importNoteImage
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.LocalXNoteToast
import com.xnote.app.design.XNoteButton
import com.xnote.app.design.XNoteDialog
import com.xnote.app.design.XNoteDialogAction
import com.xnote.app.design.XNoteEmptyState
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteHeaderAction
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.design.XNoteTextField
import com.xnote.app.domain.model.newNoteId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

// -- Functions

@Composable
fun StickerLibraryScreen(
    library: NoteLibrary,
    onBack: () -> Unit,
    onInsert: (suspend (StickerEntity) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalXNoteToast.current
    val owner = remember { "sticker-library:${newNoteId()}" }
    val entries by remember(library) { library.observeStickers() }.collectAsState(emptyList())
    var search by rememberSaveable { mutableStateOf("") }
    var sortByName by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<StickerEntity?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var cutout by remember { mutableStateOf<File?>(null) }
    var cameraName by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(library, owner) {
        onDispose { library.releaseSessionAttachments(owner) }
    }

    fun import(uri: Uri?, temporary: String? = null) {
        if (uri == null) {
            temporary?.let { cameraFile(context, it).delete() }
            return
        }
        busy = true
        scope.launch {
            try {
                val attachment = importNoteImage(context, library, uri, owner)
                cutout = library.attachmentFile(attachment)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                toast.show("图片读取失败，请重试")
            } finally {
                busy = false
                temporary?.let { cameraFile(context, it).delete() }
            }
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
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                toast.show("操作失败，请重试")
            } finally {
                busy = false
            }
        }
    }

    CreativePage(
        title = "贴纸库",
        onBack = {
            if (selected != null) {
                selected = null
                deleting = false
            } else {
                onBack()
            }
        },
        actions = listOf(
            XNoteHeaderAction(
                iconRes = R.drawable.ic_keyline_stroke_plus,
                contentDescription = "添加",
                onClick = {
                    try {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } catch (_: Exception) {
                        toast.show("相册不可用")
                    }
                },
                enabled = !busy,
            ),
        ),
        overlay = { backdrop ->
            XNoteDialog(
                visible = deleting,
                onDismissRequest = { deleting = false },
                title = "删除贴纸",
                backdrop = backdrop,
                confirmAction = XNoteDialogAction(
                    label = "删除",
                    enabled = !busy,
                    destructive = true,
                    onClick = {
                        selected?.let { entry ->
                            action {
                                library.deleteSticker(entry.id)
                                selected = null
                                deleting = false
                            }
                        }
                    },
                ),
                dismissAction = XNoteDialogAction("取消", { deleting = false }),
            ) {
                Text("删除“${selected?.name.orEmpty()}”？已插入笔记的贴纸会保留。")
            }
        },
    ) {
        val entry = selected
        if (entry != null) {
            // Selected Sticker Detail View
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                StickerThumbnail(
                    library = library,
                    attachmentId = entry.attachmentId,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(XNoteSmoothCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), XNoteSmoothCornerShape(16.dp)),
                )
            }

            XNoteGroupCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    XNoteTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "贴纸名称",
                        modifier = Modifier.testTag("xnote-sticker-rename"),
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XNoteButton(
                            onClick = {
                                action {
                                    library.renameSticker(entry.id, name)
                                    selected = entry.copy(name = name.trim())
                                    toast.show("已重命名")
                                }
                            },
                            enabled = !busy && name.isNotBlank(),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("重命名")
                        }
                        XNoteButton(
                            onClick = { deleting = true },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("删除")
                        }
                        if (onInsert != null) {
                            XNoteButton(
                                onClick = { action { onInsert(entry) } },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("插入笔记")
                            }
                        }
                        XNoteButton(
                            onClick = { selected = null; deleting = false },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("返回")
                        }
                    }
                }
            }
        } else {
            // Sticker Library Grid View
            XNoteTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = "搜索贴纸",
                modifier = Modifier.testTag("xnote-sticker-search"),
            )

            val filtered = entries.filter { it.name.contains(search, ignoreCase = true) }
                .let { if (sortByName) it.sortedWith(compareBy<StickerEntity> { entry -> entry.name }.thenBy { entry -> entry.id }) else it }

            if (filtered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_keyline_stroke_grid_squares_x),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            if (search.isEmpty()) "从图片创建你的第一张贴纸" else "没有匹配的贴纸",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { sticker ->
                        val cardShape = XNoteSmoothCornerShape(12.dp)
                        Column(
                            Modifier
                                .clip(cardShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), cardShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), cardShape)
                                .clickable {
                                    selected = sticker
                                    name = sticker.name
                                    deleting = false
                                }
                                .testTag("xnote-sticker-${sticker.id}")
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            StickerThumbnail(
                                library = library,
                                attachmentId = sticker.attachmentId,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(XNoteSmoothCornerShape(8.dp)),
                            )
                            Text(
                                text = sticker.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // Bottom Actions Bar
            XNoteGroupCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(10.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XNoteButton(
                        onClick = { sortByName = !sortByName },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (sortByName) "按名称" else "最近创建")
                    }
                    XNoteButton(
                        onClick = {
                            try {
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            } catch (_: Exception) {
                                toast.show("相册不可用")
                            }
                        },
                        enabled = !busy,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("相册创建")
                    }
                    XNoteButton(
                        onClick = {
                            try {
                                val temporary = "${newNoteId()}.jpg"
                                cameraName = temporary
                                cameraFile(context, temporary).parentFile?.mkdirs()
                                camera.launch(cameraUri(context, temporary))
                            } catch (_: Exception) {
                                toast.show("相机不可用")
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("拍照创建")
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
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        TransparencyGrid(Modifier.matchParentSize())
        bitmap?.let { Image(it, "贴纸预览", Modifier.fillMaxSize()) }
            ?: Text(
                if (failed) "图片无法读取" else "加载中",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}
