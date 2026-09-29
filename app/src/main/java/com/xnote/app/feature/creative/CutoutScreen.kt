package com.xnote.app.feature.creative

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xnote.app.R
import com.xnote.app.data.files.CutoutEngine
import com.xnote.app.data.files.applyCutoutMask
import com.xnote.app.data.files.decodeEditableMedia
import com.xnote.app.data.files.paintMask
import com.xnote.app.data.files.saveMediaBitmap
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.LocalXNoteToast
import com.xnote.app.design.XNoteButton
import com.xnote.app.design.XNoteGroupCard
import com.xnote.app.design.XNoteHeaderAction
import com.xnote.app.design.XNoteSegmentedControl
import com.xnote.app.design.XNoteSmoothCornerShape
import com.xnote.app.design.XNoteTextField
import com.xnote.app.domain.model.Attachment
import com.xnote.app.domain.model.AttachmentKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

// -- Functions

@Composable
fun CutoutScreen(
    file: File,
    library: NoteLibrary,
    owner: String,
    onBack: () -> Unit,
    onInsert: (suspend (Attachment) -> Unit)? = null,
) {
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
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failed = true
            status = error.message ?: "抠图失败，请重试"
        } finally {
            busy = false
        }
    }

    val image = source
    val mask = alpha
    val preview = remember(image, mask, revision, mode) {
        if (image == null) null
        else if (mode == 0 || mask == null) image.asImageBitmap()
        else if (mode == 2) applyCutoutMask(image, mask).asImageBitmap()
        else Bitmap.createBitmap(
            IntArray(mask.size) { index ->
                val gray = mask[index]
                0xff000000.toInt() or (gray shl 16) or (gray shl 8) or gray
            },
            image.width,
            image.height,
            Bitmap.Config.ARGB_8888,
        ).asImageBitmap()
    }

    fun save(insert: Boolean, close: Boolean = false) {
        val original = image ?: return
        val alphaSnapshot = alpha?.copyOf() ?: return
        saving = true
        scope.launch {
            try {
                val bitmap = applyCutoutMask(original, alphaSnapshot)
                val attachment = try {
                    saveMediaBitmap(context, library, bitmap, AttachmentKind.Sticker, owner)
                } finally {
                    bitmap.recycle()
                }
                if (insert) {
                    onInsert?.invoke(attachment)
                } else {
                    library.saveSticker(attachment.id, name)
                    if (close) onBack() else toast.show("已保存到贴纸库")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                toast.show("保存失败，请重试")
            } finally {
                saving = false
            }
        }
    }

    CreativePage(
        title = "图片编辑",
        onBack = onBack,
        actions = listOf(
            XNoteHeaderAction(
                iconRes = R.drawable.ic_keyline_stroke_check,
                contentDescription = "完成",
                onClick = { save(onInsert != null, close = true) },
                enabled = mask != null && !busy && !saving && name.isNotBlank(),
            ),
        ),
    ) {
        // Mode Selector: 原图 / 蒙版 / 结果
        XNoteSegmentedControl(
            items = listOf(0, 1, 2),
            selectedItem = mode,
            onItemSelected = { mode = it },
            label = { when (it) { 0 -> "原图"; 1 -> "蒙版"; else -> "结果" } },
            enabled = !saving,
        )

        // Preview Canvas Area
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null && preview != null) {
                val ratio = image.width.toFloat() / image.height
                val width = minOf(maxWidth, maxHeight * ratio)
                Box(
                    Modifier
                        .width(width)
                        .height(width / ratio)
                        .clip(XNoteSmoothCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), XNoteSmoothCornerShape(12.dp)),
                ) {
                    TransparencyGrid(Modifier.matchParentSize())
                    Image(
                        bitmap = preview,
                        contentDescription = "抠图预览",
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("xnote-cutout-preview")
                            .pointerInput(image, mask, mode, erase, brush, busy, saving) {
                                if (mask == null || mode == 0 || busy || saving) return@pointerInput
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    fun point(position: Offset) = Offset(
                                        position.x / size.width * image.width,
                                        position.y / size.height * image.height,
                                    )
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
                            },
                    )
                }
            }
        }

        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy || saving) LinearProgressIndicator(Modifier.fillMaxWidth())

        // Editing Tools Dock
        XNoteGroupCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XNoteSegmentedControl(
                        items = listOf(false, true),
                        selectedItem = erase,
                        onItemSelected = {
                            erase = it
                            mode = 1
                        },
                        label = { isErase -> if (isErase) "擦除" else "添加" },
                        modifier = Modifier.weight(1f),
                        enabled = !busy && !saving && mask != null,
                    )
                    XNoteButton(
                        onClick = { brush = when (brush) { 8f -> 24f; 24f -> 48f; else -> 8f } },
                        enabled = !busy && !saving,
                    ) {
                        Text("粗细 ${brush.toInt()}")
                    }
                    XNoteButton(
                        onClick = { alpha = undo.removeAt(undo.lastIndex); revision++ },
                        enabled = undo.isNotEmpty() && !busy && !saving,
                    ) {
                        Text("撤销")
                    }
                    XNoteButton(
                        onClick = { run++ },
                        enabled = !busy && !saving,
                    ) {
                        Text(if (failed) "重试" else "重算")
                    }
                }

                // Sticker Name Input
                XNoteTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "贴纸名称",
                    modifier = Modifier.testTag("xnote-sticker-name"),
                )

                // Bottom Action Buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XNoteButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    XNoteButton(
                        onClick = { save(false) },
                        enabled = mask != null && !busy && !saving && name.isNotBlank(),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("保存贴纸")
                    }
                    if (onInsert != null) {
                        XNoteButton(
                            onClick = { save(true) },
                            enabled = mask != null && !busy && !saving,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("插入笔记")
                        }
                    }
                }
            }
        }
    }
}
