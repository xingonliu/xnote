package com.xnote.app.feature.agent

import android.content.Intent
import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xnote.app.R
import com.xnote.app.data.agent.AgentFileStore
import com.xnote.app.data.db.AgentFileCard
import com.xnote.app.data.files.agentFileShareIntent
import com.xnote.app.data.files.decodeNoteImage
import com.xnote.app.data.files.saveAgentFile
import com.xnote.app.design.*
import kotlinx.coroutines.*

// -- Type Definitions

private data class FileTypeVisual(
    val iconRes: Int,
    val iconTint: Color,
    val bgTint: Color,
    val badge: String,
)

// -- Functions

private fun resolveFileVisual(name: String, mimeType: String): FileTypeVisual = when {
    mimeType.startsWith("image/") -> FileTypeVisual(
        iconRes = R.drawable.ic_keyline_stroke_paintbrush,
        iconTint = Color(0xFF34C759),
        bgTint = Color(0xFF34C759).copy(alpha = 0.15f),
        badge = "图片",
    )
    mimeType == "application/pdf" || name.endsWith(".pdf", true) -> FileTypeVisual(
        iconRes = R.drawable.ic_keyline_stroke_file_text,
        iconTint = Color(0xFFFF3B30),
        bgTint = Color(0xFFFF3B30).copy(alpha = 0.15f),
        badge = "PDF",
    )
    name.endsWith(".md", true) || mimeType == "text/markdown" -> FileTypeVisual(
        iconRes = R.drawable.ic_keyline_stroke_code,
        iconTint = Color(0xFF007AFF),
        bgTint = Color(0xFF007AFF).copy(alpha = 0.15f),
        badge = "MD",
    )
    else -> FileTypeVisual(
        iconRes = R.drawable.ic_keyline_stroke_file_text,
        iconTint = Color(0xFFFF9500),
        bgTint = Color(0xFFFF9500).copy(alpha = 0.15f),
        badge = "TXT",
    )
}

@Composable
fun AgentComposerFileCard(
    card: AgentFileCard,
    backdrop: Backdrop,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    XNoteLiquidGlassPanel(
        backdrop = backdrop,
        modifier = modifier
            .widthIn(max = 240.dp)
            .height(52.dp)
            .testTag("agent-file-${card.id}"),
        shape = XNoteSmoothCornerShape(14.dp),
        shadowEnabled = false,
    ) {
        AgentFileCardContent(
            card = card,
            onRemove = onRemove,
            wrapContent = true,
            modifier = Modifier.clickable(onClick = onPreview),
        )
    }
}

@Composable
fun AgentFileCardView(
    card: AgentFileCard,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val highContrast = LocalXNoteInteractionSettings.current.highContrast

    val containerColor = when {
        highContrast -> MaterialTheme.colorScheme.surfaceVariant
        isLight -> Color(0xFFF2F2F7).copy(alpha = 0.95f)
        else -> Color(0xFF2C2C2E).copy(alpha = 0.90f)
    }
    val borderColor = when {
        highContrast -> MaterialTheme.colorScheme.outline
        isLight -> Color.Black.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val shape = XNoteSmoothCornerShape(14.dp)

    Surface(
        onClick = onPreview,
        modifier = modifier
            .widthIn(min = 160.dp, max = 240.dp)
            .height(52.dp)
            .testTag("agent-file-${card.id}"),
        shape = shape,
        color = containerColor,
        border = BorderStroke(0.5.dp, borderColor),
    ) {
        AgentFileCardContent(card = card, onRemove = onRemove)
    }
}

@Composable
private fun AgentFileCardContent(
    card: AgentFileCard,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    wrapContent: Boolean = false,
) {
    // -- Derived Values

    val context = LocalContext.current
    val visual = resolveFileVisual(card.name, card.mimeType)
    val sizeText = Formatter.formatFileSize(context, card.byteSize)
    val pageText = if (card.pages > 0) " · ${card.pages} 页" else ""

    Row(
        modifier = modifier
            .then(if (wrapContent) Modifier.fillMaxHeight() else Modifier.fillMaxSize())
            .padding(start = 8.dp, end = if (onRemove != null) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(XNoteSmoothCornerShape(9.dp))
                .background(visual.bgTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(visual.iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = visual.iconTint,
            )
        }
        Column(
            modifier = Modifier.weight(1f, fill = !wrapContent),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = card.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${visual.badge} · $sizeText$pageText",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(if (wrapContent) XNoteButtonSize else 28.dp)
                    .testTag("agent-remove-file-${card.id}"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyline_stroke_x),
                    contentDescription = "移除文件：${card.name}",
                    modifier = Modifier.size(if (wrapContent) XNoteIconSizeMedium else 13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AgentFilesStrip(
    files: List<AgentFileCard>,
    onPreview: (AgentFileCard) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: ((String) -> Unit)? = null,
) {
    if (files.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        files.forEach { card ->
            AgentFileCardView(
                card = card,
                onPreview = { onPreview(card) },
                onRemove = if (onRemove != null) ({ onRemove(card.id) }) else null,
            )
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

    val visual = resolveFileVisual(card.name, card.mimeType)
    val sizeText = Formatter.formatFileSize(context, card.byteSize)
    val pageText = if (card.pages > 0) " · ${card.pages} 页" else ""

    XNoteDialog(true, onDismiss, card.name, backdrop, confirmAction = XNoteDialogAction("关闭", onDismiss)) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 2.dp),
            ) {
                Surface(
                    shape = XNoteSmoothCornerShape(6.dp),
                    color = visual.bgTint,
                ) {
                    Text(
                        text = visual.badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = visual.iconTint,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = "$sizeText$pageText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "附件图片预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(XNoteSmoothCornerShape(12.dp)),
                )
            }
            if (card.mimeType == "application/pdf") {
                Text("PDF 文字预览；图片与原版式请保存后查看。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!card.mimeType.startsWith("image/")) {
                Surface(
                    shape = XNoteSmoothCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer(Modifier.padding(12.dp)) {
                        Text(
                            text = card.text.ifBlank { "此文件没有可提取的文字。" },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("agent-file-preview-text"),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ save.launch(card.name) }, enabled = !busy) { Text("保存文件") }
                TextButton({ action {
                    context.startActivity(Intent.createChooser(agentFileShareIntent(context, store, card), "分享文件"))
                } }, enabled = !busy) { Text("分享文件") }
            }
        }
    }
}
