package com.xnote.app.feature.creative

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.files.*
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidSlider
import com.xnote.app.domain.model.Attachment
import com.xnote.app.domain.model.AttachmentKind
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// -- Functions

@Composable
fun CutoutScreen(file: File, library: NoteLibrary, owner: String, onBack: () -> Unit, onInsert: (suspend (Attachment) -> Unit)? = null) {
    // -- State and Variables

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { CutoutEngine(context) }
    val toast = LocalXNoteToast.current
    var source by remember(file) { mutableStateOf<Bitmap?>(null) }
    var alpha by remember(file) { mutableStateOf<IntArray?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var run by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(2) }
    var erase by remember { mutableStateOf(false) }
    var brush by remember { mutableFloatStateOf(24f) }
    var status by remember { mutableStateOf("正在读取图片…") }
    var busy by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var brushDialog by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var restartDialog by remember { mutableStateOf(false) }
    val moreAnchor = rememberXNotePopupAnchor()
    var name by remember { mutableStateOf("新贴纸") }
    val undo = remember { mutableStateListOf<IntArray>() }
    val redo = remember { mutableStateListOf<IntArray>() }

    // -- Derived Values

    val image = source
    val mask = alpha
    val editable = mask != null && !busy && !saving
    val previewBitmap = remember(image, mask, revision, mode) {
        if (image == null) null
        else if (mode == 0 || mask == null) image
        else if (mode == 2) applyCutoutMask(image, mask)
        else Bitmap.createBitmap(IntArray(mask.size) { index ->
            val gray = mask[index]
            0xff000000.toInt() or (gray shl 16) or (gray shl 8) or gray
        }, image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    // -- Functions

    fun back() {
        if (saving) return
        if (dirty) discard = true else onBack()
    }
    fun save(insert: Boolean) {
        val original = image ?: return
        val alphaSnapshot = alpha?.copyOf() ?: return
        if (!editable) return
        saving = true
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) { applyCutoutMask(original, alphaSnapshot) }
                val attachment = try { saveMediaBitmap(context, library, bitmap, AttachmentKind.Sticker, owner) } finally { bitmap.recycle() }
                if (insert) onInsert?.invoke(attachment)
                else {
                    library.saveSticker(attachment.id, name.trim())
                    toast.show("已保存到贴纸库")
                    saveDialog = false
                    if (onInsert == null) onBack()
                }
                dirty = false
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { toast.show("未能保存，请重试") }
            finally { saving = false }
        }
    }

    // -- Lifecycle Hooks

    LaunchedEffect(file, run) {
        busy = true
        failed = false
        try {
            val decoded = source ?: decodeEditableMedia(file).also { source = it }
            alpha = engine.process(decoded) { status = it }
            undo.clear()
            redo.clear()
            revision++
            mode = 2
            dirty = true
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
        finally { busy = false }
    }
    DisposableEffect(previewBitmap) {
        onDispose { if (previewBitmap != null && previewBitmap !== image) previewBitmap.recycle() }
    }
    DisposableEffect(image) { onDispose { image?.recycle() } }

    CreativePage("抠图", ::back, actions = listOf(
        XNoteHeaderAction(R.drawable.ic_keyline_stroke_more_horizontal, "更多操作", { moreMenu = true },
            enabled = !busy && !saving, popupAnchor = moreAnchor),
        XNoteHeaderAction(R.drawable.ic_keyline_stroke_check, "完成",
            { if (onInsert != null) save(true) else saveDialog = true }, enabled = editable),
    ), toolbar = {
        XNoteChoiceGroup(listOf(0, 1, 2), mode, { listOf("原图", "选区", "结果")[it] }, { mode = it }, enabled = !saving)
        if (editable) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CreativeModeButton("保留", !erase && mode != 0, true) { erase = false; mode = 2 }
                CreativeModeButton("擦除", erase && mode != 0, true) { erase = true; mode = 2 }
                CreativeIconButton("笔触粗细", R.drawable.ic_keyline_stroke_paintbrush) { brushDialog = true }
                CreativeIconButton("撤销", R.drawable.ic_keyline_stroke_arrow_u_turn_left, undo.isNotEmpty()) {
                    redo.add(alpha!!.copyOf()); alpha = undo.removeAt(undo.lastIndex); revision++; dirty = true
                }
                CreativeIconButton("重做", R.drawable.ic_keyline_stroke_arrow_u_turn_right, redo.isNotEmpty()) {
                    undo.add(alpha!!.copyOf()); alpha = redo.removeAt(redo.lastIndex); revision++; dirty = true
                }
            }
            if (onInsert != null) XNoteButton({ saveDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("保存为贴纸") }
        }
        if (saving) {
            Text("正在保存…", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, overlay = { glass ->
        XNoteDropdownMenu(moreMenu, { moreMenu = false }, listOf(
            XNoteDropdownMenuItem("重新抠图", { restartDialog = true }),
        ), glass, anchor = moreAnchor)
        XNoteDialog(restartDialog, { restartDialog = false }, "重新抠图？", glass,
            XNoteDialogAction("重新抠图", { restartDialog = false; run++ }),
            dismissAction = XNoteDialogAction("取消", { restartDialog = false })) {
            Text("将重新识别主体，替换当前的手动修正。")
        }
        XNoteDialog(discard, { discard = false }, "放弃这次抠图？", glass,
            XNoteDialogAction("放弃", onBack, destructive = true),
            dismissAction = XNoteDialogAction("继续编辑", { discard = false })) { Text("尚未保存的结果会丢失。") }
        XNoteDialog(saveDialog, { if (!saving) saveDialog = false }, "保存贴纸", glass,
            XNoteDialogAction("保存", { save(false) }, enabled = name.isNotBlank() && !saving),
            dismissAction = XNoteDialogAction("取消", { saveDialog = false }, enabled = !saving)) {
            XNoteTextField(name, { name = it }, Modifier.testTag("xnote-sticker-name"), placeholder = "贴纸名称", enabled = !saving)
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        XNoteDialog(brushDialog, { brushDialog = false }, "笔触粗细", glass,
            XNoteDialogAction("完成", { brushDialog = false })) {
            Text("细", style = MaterialTheme.typography.bodySmall)
            LiquidSlider(value = { brush }, onValueChange = { brush = it }, valueRange = 8f..64f,
                visibilityThreshold = .1f, backdrop = glass, modifier = Modifier.fillMaxWidth().height(48.dp))
            Text("粗", Modifier.align(Alignment.End), style = MaterialTheme.typography.bodySmall)
        }
    }) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (image != null && previewBitmap != null) {
                val ratio = image.width.toFloat() / image.height
                val width = minOf(maxWidth, maxHeight * ratio)
                Box(Modifier.width(width).height(width / ratio)) {
                    TransparencyGrid(Modifier.matchParentSize())
                    Image(previewBitmap.asImageBitmap(), "抠图预览", Modifier.fillMaxSize().testTag("xnote-cutout-preview")
                        .pointerInput(image, mask, mode, erase, brush, busy, saving) {
                            if (mask == null || mode == 0 || busy || saving) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun point(position: Offset) = Offset(position.x / size.width * image.width, position.y / size.height * image.height)
                                undo.add(mask.copyOf())
                                if (undo.size > 8) undo.removeAt(0)
                                redo.clear()
                                dirty = true
                                var previous = point(down.position)
                                paintMask(mask, image.width, image.height, previous.x, previous.y, previous.x, previous.y, brush, if (erase) 0 else 255)
                                revision++
                                down.consume()
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val next = point(change.position)
                                    paintMask(mask, image.width, image.height, previous.x, previous.y, next.x, next.y, brush, if (erase) 0 else 255)
                                    previous = next
                                    change.consume()
                                    revision++
                                } while (event.changes.any { it.id == down.id && it.pressed })
                            }
                        })
                }
            }
            if (busy || failed) XNoteGroupCard(Modifier.widthIn(max = 360.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (failed) "暂时无法抠图" else status, style = MaterialTheme.typography.bodyLarge)
                    if (failed) {
                        Text("请检查网络后重试，或返回选择其他图片。", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        XNoteButton({ run++ }) { Text("重试") }
                    } else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
