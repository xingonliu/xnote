package com.xnote.app.feature.agent

import android.content.Intent
import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.data.agent.AgentFileStore
import com.xnote.app.data.db.AgentFileCard
import com.xnote.app.data.files.decodeNoteImage
import com.xnote.app.data.files.saveAgentFile
import com.xnote.app.data.files.agentFileShareIntent
import com.xnote.app.design.*
import kotlinx.coroutines.*

// -- Functions

@Composable
fun AgentFilesStrip(files: List<AgentFileCard>, onPreview: (AgentFileCard) -> Unit, onRemove: ((String) -> Unit)? = null) {
    if (files.isEmpty()) return
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEach { card ->
            Column(Modifier.widthIn(max = 240.dp)) {
                TextButton({ onPreview(card) }, Modifier.testTag("agent-file-${card.id}")) {
                    Column {
                        Text(card.name, maxLines = 2)
                        Text(Formatter.formatFileSize(context, card.byteSize) + if (card.pages > 0) " · ${card.pages} 页" else "", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (onRemove != null) TextButton({ onRemove(card.id) }) { Text("移除文件") }
            }
        }
    }
}

@Composable
fun AgentFilePreview(card: AgentFileCard, store: AgentFileStore, backdrop: Backdrop, onNotice: (String) -> Unit, onDismiss: () -> Unit) {
    // -- State and Variables

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(card.id) { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    // -- Functions

    fun action(block: suspend () -> Unit) { scope.launch {
        busy = true
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { onNotice("文件无法读取或操作未完成，请重试。") }
        finally { busy = false }
    } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(card.mimeType)) { uri ->
        if (uri != null) action {
            saveAgentFile(context, store, card, uri)
            onNotice("文件已保存")
        }
    }

    // -- Lifecycle Hooks

    LaunchedEffect(card.id) {
        if (card.mimeType.startsWith("image/")) try { bitmap = decodeNoteImage(store.file(card.id)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { onNotice("无法预览图片") }
    }
    XNoteDialog(true, onDismiss, card.name, backdrop, confirmAction = XNoteDialogAction("关闭", onDismiss)) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            bitmap?.let { Image(it.asImageBitmap(), "附件图片预览", Modifier.fillMaxWidth().heightIn(max = 280.dp)) }
            if (card.mimeType == "application/pdf") Text("PDF 文字预览；图片与原版式请保存后查看。", style = MaterialTheme.typography.bodySmall)
            if (!card.mimeType.startsWith("image/")) SelectionContainer { Text(card.text.ifBlank { "此文件没有可提取的文字。" }, Modifier.testTag("agent-file-preview-text")) }
            Row {
                TextButton({ save.launch(card.name) }, enabled = !busy) { Text("保存文件") }
                TextButton({ action {
                    context.startActivity(Intent.createChooser(agentFileShareIntent(context, store, card), "分享文件"))
                } }, enabled = !busy) { Text("分享文件") }
            }
        }
    }
}
