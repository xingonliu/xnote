package com.xnote.app.feature.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.*
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.*
import com.xnote.app.design.liquidglass.LiquidButton
import com.xnote.app.domain.agent.*
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.AppSettings
import com.xnote.app.domain.model.resolveBackgroundKey
import com.xnote.app.feature.background.*
import com.xnote.app.feature.notes.editor.toAnnotatedString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

// -- Constants

private val AddedColor = Color(0xFF16823B)
private val RemovedColor = Color(0xFFC0392B)

// -- Functions

@Composable
fun AgentReviewScreen(timeline: AgentTimeline, library: NoteLibrary, settings: AppSettings, onBack: () -> Unit) {
    val store = timeline.reviewStore
    val reviews by store.reviews.collectAsState(emptyList())
    val active by library.observeActiveNotes().collectAsState(emptyList())
    val trash by library.observeTrashedNotes().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<AgentReviewDetail?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val notes = active + trash
    val background = resolveBackgroundKey(notes.find { it.id == selected }?.backgroundKey, settings.defaultBackground)
    val current = detail
    fun back() { if (selected != null) { selected = null; detail = null; notice = null } else onBack() }
    fun action(block: suspend () -> Unit) { scope.launch {
        busy = true
        try { block(); refresh++ }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { notice = if (error is IllegalStateException || error is IllegalArgumentException) error.message else "操作失败，正文与改动记录已保留。" }
        finally { busy = false }
    } }
    fun report(result: AgentReviewResult) { notice = when (result) {
        is AgentReviewResult.Conflict -> "整篇回退已暂停：${result.location}。当前内容未改变。"
        AgentReviewResult.Unrecoverable -> "笔记已永久删除，无法恢复。"
        else -> "改动已撤回，用户编辑已保留。"
    } }
    fun adjust(noteId: String) { action {
        timeline.selectDraftNotes((timeline.draftNotes.value + noteId).distinct())
        timeline.saveDraft(listOf(timeline.draft.value, "请重新读取附加笔记的当前版本，保留我的编辑，并调整尚未审阅的改动。").filter { it.isNotBlank() }.joinToString("\n\n"))
        onBack()
    } }
    BackHandler { back() }
    LaunchedEffect(selected, reviews, notes, refresh) { detail = selected?.let { store.detail(it) } }
    EditorBackgroundTheme(background, library, settings, active = selected != null) { image, onSizeChanged ->
        XNotePageScaffold(backdrop, scrollEdges = emptySet(), pageBackground = {
            XNoteNoteSurface(background, Modifier.fillMaxSize().onSizeChanged(onSizeChanged), image)
        }, content = {
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("agent-review-content").padding(
                start = 24.dp, end = 24.dp, top = xNoteScrollEdgePadding(insets.calculateTopPadding() + XNoteHeaderHeight),
                bottom = insets.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                notice?.let { Text(it, Modifier.testTag("agent-review-notice")) }
                if (selected == null) {
                    Text("Agent 改动已保存到笔记。每篇独立审阅，清空聊天不会清除这里的记录。")
                    if (reviews.isEmpty()) Text("暂无笔记改动")
                    reviews.groupBy { it.noteId }.forEach { (noteId, batches) ->
                        val pending = batches.any { it.status in setOf(AgentReviewStatus.Pending, AgentReviewStatus.Conflict) }
                        LiquidButton({ selected = noteId; notice = null }, backdrop, modifier = Modifier.fillMaxWidth().testTag("agent-review-$noteId")) {
                            Text((notes.find { it.id == noteId }?.title?.ifBlank { "未命名笔记" } ?: "已永久删除的笔记") + if (pending) " · 待审阅" else " · 已审阅")
                        }
                    }
                } else if (current == null) Text("正在读取改动…") else {
                    Text(current.current?.title?.ifBlank { "未命名笔记" } ?: "已永久删除的笔记", style = MaterialTheme.typography.titleLarge)
                    Text("来源：Agent · ${current.changes.size} 次累计改动", style = MaterialTheme.typography.bodySmall)
                    current.changes.firstOrNull()?.let { first -> Text("版本 ${first.beforeVersion?.take(12)} → ${current.changes.last().afterVersion.take(12)}", style = MaterialTheme.typography.bodySmall) }
                    Text("红色删除 · 绿色新增；用户后续编辑保留。", style = MaterialTheme.typography.bodySmall)
                    val rollback = current.rollback
                    val note = current.current
                    when {
                        note == null -> Text("笔记已永久删除，改动无法恢复。")
                        rollback is AgentContentMerge.Merged -> AgentCumulativeDiff(rollback.content, AgentEditableContent(note.title, decodeNoteDocument(note.documentJson)))
                        rollback is AgentContentMerge.Conflict -> {
                            if (notice == null) Text("回退与当前内容冲突：${rollback.location}。全部拒绝不会部分写入。", Modifier.testTag("agent-review-conflict"))
                            Text("以下为已保存的 Agent 改动记录：")
                            current.changes.forEach { change ->
                                val before = Json.decodeFromString<NoteEntity>(requireNotNull(change.beforeNoteJson))
                                val after = Json.decodeFromString<NoteEntity>(change.afterNoteJson)
                                AgentCumulativeDiff(AgentEditableContent(before.title, decodeNoteDocument(before.documentJson)), AgentEditableContent(after.title, decodeNoteDocument(after.documentJson)))
                            }
                        }
                        current.review.status == AgentReviewStatus.Undone -> Text("最近接受的改动已撤回。")
                        else -> Text("该批改动已拒绝。")
                    }
                    if (current.review.status in setOf(AgentReviewStatus.Pending, AgentReviewStatus.Conflict) && note != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiquidButton({ action { store.accept(note.id); notice = "已接受，保留当前内容。" } }, backdrop, enabled = !busy, modifier = Modifier.testTag("agent-review-accept")) { Text(if (rollback is AgentContentMerge.Conflict) "保留当前内容" else "全部接受") }
                            LiquidButton({ action { report(store.reject(note.id)) } }, backdrop, enabled = !busy, modifier = Modifier.testTag("agent-review-reject")) { Text("全部拒绝") }
                        }
                    }
                    if (rollback is AgentContentMerge.Conflict && note != null) {
                        if (current.review.status == AgentReviewStatus.Accepted) LiquidButton(onBack, backdrop, enabled = !busy) { Text("保留当前内容") }
                        LiquidButton({ adjust(note.id) }, backdrop, enabled = !busy, modifier = Modifier.testTag("agent-review-adjust")) { Text("重新调整") }
                    }
                    if (current.undoReview != null && note != null) {
                        val available = canUndoAcceptedAgentReview(current.undoReview.reviewedAtEpochMs, System.currentTimeMillis())
                        Text(if (available) "最近接受的改动可在 30 天内撤回。" else "已超过 30 天，不能撤回本次接受。")
                        LiquidButton({ action { report(store.undoAccepted(note.id)) } }, backdrop, enabled = !busy && available, modifier = Modifier.testTag("agent-review-undo")) { Text("撤回最近接受") }
                    }
                }
            }
        }, overlay = { XNoteHeader(if (selected == null) "笔记改动" else "单篇改动", backdrop, Modifier.align(Alignment.TopCenter), onBack = ::back) })
    }
}

@Composable
private fun AgentCumulativeDiff(before: AgentEditableContent, after: AgentEditableContent) {
    val removed = removedColor()
    val added = addedColor()
    if (before.title != after.title) { Text("标题", style = MaterialTheme.typography.labelLarge); AgentDiffText(before.title, after.title) }
    val old = before.document.blocks.associateBy { it.id }
    val latest = after.document.blocks.associateBy { it.id }
    val changed = (before.document.blocks.map { it.id } + after.document.blocks.map { it.id }).distinct().filter { old[it] != latest[it] }
    if (changed.isEmpty() && before.title == after.title && before.document.blocks.map { it.id } == after.document.blocks.map { it.id }) Text("当前没有需要回退的内容差异。")
    if (before.document.blocks.map { it.id } != after.document.blocks.map { it.id }) {
        Text("段落顺序", style = MaterialTheme.typography.labelLarge)
        fun outline(document: NoteDocument) = document.blocks.mapIndexed { index, block ->
            "${index + 1}. " + when (block) { is ImageBlock -> "图片"; is StickerBlock -> "贴纸"; is DrawingBlock -> "画笔"; else -> blockText(block).take(40).ifBlank { "空段落" } }
        }.joinToString("\n")
        AgentDiffText(outline(before.document), outline(after.document))
    }
    changed.forEach { id ->
        XNoteGroupCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AgentDiffText(blockText(old[id]), blockText(latest[id]))
                if (old[id] is TextBlock || latest[id] is TextBlock) {
                    val prior = old[id] as? TextBlock; val next = latest[id] as? TextBlock
                    if (prior?.copy(inlines = emptyList()) != next?.copy(inlines = emptyList()) || prior?.inlines?.map { it.copy(text = "") } != next?.inlines?.map { it.copy(text = "") }) {
                        prior?.let { Text("改动前：${formatDescription(it)}", color = removed, style = MaterialTheme.typography.bodySmall) }
                        next?.let { Text("改动后：${formatDescription(it)}", color = added, style = MaterialTheme.typography.bodySmall) }
                        if (prior != null && next != null && prior.inlines != next.inlines) AgentInlineComparison(prior.inlines, next.inlines)
                    }
                }
                val priorTable = old[id] as? TableBlock
                val nextTable = latest[id] as? TableBlock
                if (priorTable != null && nextTable != null) {
                    Text("表格：${priorTable.rows.size} 行 → ${nextTable.rows.size} 行", style = MaterialTheme.typography.bodySmall)
                    priorTable.rows.forEachIndexed { row, value -> value.cells.forEachIndexed { column, cell ->
                        val nextCell = nextTable.rows.getOrNull(row)?.cells?.getOrNull(column)
                        if (nextCell != null && cell.inlines != nextCell.inlines) {
                            Text("第 ${row + 1} 行，第 ${column + 1} 列", style = MaterialTheme.typography.labelSmall)
                            AgentInlineComparison(cell.inlines, nextCell.inlines)
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun AgentInlineComparison(before: List<InlineRun>, after: List<InlineRun>) {
    val highlight = MaterialTheme.colorScheme.primaryContainer
    val link = MaterialTheme.colorScheme.primary
    Text("删除版本", color = removedColor(), style = MaterialTheme.typography.labelSmall)
    Text(before.toAnnotatedString(highlight, link))
    Text("新增版本", color = addedColor(), style = MaterialTheme.typography.labelSmall)
    Text(after.toAnnotatedString(highlight, link))
}

@Composable
private fun AgentDiffText(before: String, after: String) {
    val removed = removedColor()
    val added = addedColor()
    val text = remember(before, after, removed, added) {
        val base = before.codePoints().toArray().toList()
        val next = after.codePoints().toArray().toList()
        val edits = agentSequenceEdits(base, next) ?: listOf(AgentSequenceEdit(0, base.size, next))
        fun List<Int>.string() = StringBuilder().also { builder -> forEach { builder.appendCodePoint(it) } }.toString()
        buildAnnotatedString {
            var offset = 0
            for (edit in edits) {
                append(base.subList(offset, edit.start).string())
                withStyle(SpanStyle(color = removed, textDecoration = TextDecoration.LineThrough)) { append(base.subList(edit.start, edit.end).string()) }
                withStyle(SpanStyle(color = added)) { append(edit.replacement.string()) }
                offset = edit.end
            }
            append(base.subList(offset, base.size).string())
        }
    }
    Text(text)
}

@Composable private fun removedColor() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFF8A80) else RemovedColor
@Composable private fun addedColor() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF81C784) else AddedColor

private fun blockText(block: NoteBlock?): String = when (block) {
    is TextBlock -> block.inlines.plainText()
    is TableBlock -> block.rows.joinToString("\n") { row -> row.cells.joinToString(" | ") { it.inlines.plainText() } }
    else -> ""
}

private fun formatDescription(block: TextBlock): String = buildList {
    add(when (block.paragraphStyle) { ParagraphStyle.Body -> "正文"; ParagraphStyle.Heading -> "标题"; ParagraphStyle.Subheading -> "小标题"; ParagraphStyle.Monospace -> "等宽文字" })
    add(when (block.alignment) { TextAlignment.Left -> "居左"; TextAlignment.Center -> "居中"; TextAlignment.Right -> "居右" })
    add(when (block.listMarker) { ListMarker.None -> "普通段落"; ListMarker.Bullet -> "圆点列表"; ListMarker.Dash -> "短线列表"; ListMarker.Numbered -> "编号列表"; ListMarker.Checklist -> if (block.checked) "清单已勾选" else "清单未勾选" })
    if (block.indent > 0) add("缩进 ${block.indent} 级")
    if (block.quoted) add("引用")
    if (block.collapsed) add("已折叠")
    if (block.inlines.any { it.bold }) add("粗体")
    if (block.inlines.any { it.italic }) add("斜体")
    if (block.inlines.any { it.underline }) add("下划线")
    if (block.inlines.any { it.strikethrough }) add("删除线")
    if (block.inlines.any { it.highlight }) add("高亮")
    block.inlines.mapNotNull { it.linkUrl }.distinct().forEach { add("链接 $it") }
}.joinToString(" · ")
