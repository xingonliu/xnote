package com.xnote.app.feature.creative

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xnote.app.data.files.*
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteButton
import com.xnote.app.domain.model.Attachment
import com.xnote.app.domain.model.AttachmentKind
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// -- Functions

@Composable
fun CutoutScreen(file: File, library: NoteLibrary, owner: String, onBack: () -> Unit, onInsert: (suspend (Attachment) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { CutoutEngine(context) }
    val toast = remember { SnackbarHostState() }
    var source by remember(file) { mutableStateOf<Bitmap?>(null) }
    var alpha by remember(file) { mutableStateOf<IntArray?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var run by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(2) }
    var erase by remember { mutableStateOf(false) }
    var brush by remember { mutableFloatStateOf(24f) }
    var status by remember { mutableStateOf("正在读取图片") }
    var busy by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("新贴纸") }
    val undo = remember { mutableStateListOf<IntArray>() }
    LaunchedEffect(file, run) {
        busy = true
        failed = false
        try {
            val image = source ?: decodeEditableMedia(file).also { source = it }
            alpha = engine.process(image) { status = it }
            undo.clear()
            revision++
            mode = 2
            status = "可在蒙版或结果上涂抹修正"
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { failed = true; status = error.message ?: "抠图失败，请重试" }
        finally { busy = false }
    }
    val image = source
    val mask = alpha
    val preview = remember(image, mask, revision, mode) {
        if (image == null) null
        else if (mode == 0 || mask == null) image.asImageBitmap()
        else if (mode == 2) applyCutoutMask(image, mask).asImageBitmap()
        else Bitmap.createBitmap(IntArray(mask.size) { index ->
            val gray = mask[index]
            0xff000000.toInt() or (gray shl 16) or (gray shl 8) or gray
        }, image.width, image.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    fun save(insert: Boolean, close: Boolean = false) {
        val original = image ?: return
        val alphaSnapshot = alpha?.copyOf() ?: return
        saving = true
        scope.launch {
            try {
                val bitmap = applyCutoutMask(original, alphaSnapshot)
                val attachment = try { saveMediaBitmap(context, library, bitmap, AttachmentKind.Sticker, owner) } finally { bitmap.recycle() }
                if (insert) onInsert?.invoke(attachment)
                else {
                    library.saveSticker(attachment.id, name)
                    if (close) onBack() else toast.showSnackbar("已保存到贴纸库")
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { toast.showSnackbar("保存失败，请重试") }
            finally { saving = false }
        }
    }
    CreativePage("图片编辑", onBack, toast, actions = listOf(com.xnote.app.design.XNoteHeaderAction(
        com.xnote.app.R.drawable.ic_keyline_stroke_check, "完成", { save(onInsert != null, close = true) },
        enabled = mask != null && !busy && !saving && name.isNotBlank(),
    ))) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("原图", "蒙版", "结果").forEachIndexed { index, title ->
                XNoteButton({ mode = index }, enabled = !saving) { Text("${if (mode == index) "✓ " else ""}$title") }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (image != null && preview != null) {
                val ratio = image.width.toFloat() / image.height
                val width = minOf(maxWidth, maxHeight * ratio)
                Box(Modifier.width(width).height(width / ratio)) {
                    TransparencyGrid(Modifier.matchParentSize())
                    Image(preview, "抠图预览", Modifier.fillMaxSize().testTag("xnote-cutout-preview")
                        .pointerInput(image, mask, mode, erase, brush, busy, saving) {
                            if (mask == null || mode == 0 || busy || saving) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun point(position: Offset) = Offset(position.x / size.width * image.width, position.y / size.height * image.height)
                                undo.add(mask.copyOf())
                                if (undo.size > 8) undo.removeAt(0)
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
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        if (busy || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XNoteButton({ erase = false; mode = 1 }, enabled = !busy && !saving && mask != null) { Text(if (!erase) "✓ 添加" else "添加") }
            XNoteButton({ erase = true; mode = 1 }, enabled = !busy && !saving && mask != null) { Text(if (erase) "✓ 擦除" else "擦除") }
            XNoteButton({ brush = when (brush) { 8f -> 24f; 24f -> 48f; else -> 8f } }, enabled = !busy && !saving) { Text("粗细 ${brush.toInt()}") }
            XNoteButton({ alpha = undo.removeAt(undo.lastIndex); revision++ }, enabled = undo.isNotEmpty() && !busy && !saving) { Text("撤销修正") }
            XNoteButton({ run++ }, enabled = !busy && !saving) { Text(if (failed) "重试" else "重新处理") }
        }
        BasicTextField(name, { name = it }, Modifier.fillMaxWidth().padding(8.dp).testTag("xnote-sticker-name"),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), singleLine = true)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XNoteButton(onBack) { Text("取消") }
            XNoteButton({ save(false) }, enabled = mask != null && !busy && !saving && name.isNotBlank()) { Text("保存为贴纸") }
            if (onInsert != null) XNoteButton({ save(true) }, enabled = mask != null && !busy && !saving) { Text("插入笔记") }
        }
    }
}
