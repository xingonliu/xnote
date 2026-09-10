package com.xnote.app.feature.reader

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.XNoteApp
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.*
import com.xnote.app.navigation.NotesRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

// -- Tests

class ReadingFlowTest {
    private val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = XNoteDatabase.createInMemory(context)
    private val root = File(context.cacheDir, "reading-${System.nanoTime()}")
    private val library = NoteLibrary(database, AttachmentFileStore(root), SystemEpochClock)
    private val cleanup = object : TestWatcher() {
        override fun finished(description: Description) { database.close(); root.deleteRecursively() }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(cleanup).around(compose)

    @Test fun singleNoteEntryFlushesUnsavedTextAndReturnsThroughEditor() {
        val note = runBlocking { library.saveNote(library.createNote(null).copy(title = "单篇阅读")) }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library) } }
        compose.onNodeWithTag("xnote-collection-all").performClick()
        compose.onNodeWithText("单篇阅读").performClick()
        compose.onNodeWithTag("xnote-editor-body").performTextInput("刚输入的最后一个字")
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("打开阅读模式").performClick()
        compose.onNodeWithText("刚输入的最后一个字").assertIsDisplayed()
        compose.onNodeWithTag("xnote-reader-next").assertIsNotEnabled()
        compose.onNodeWithContentDescription("编辑当前笔记").performClick()
        compose.onNodeWithTag("xnote-editor-body").performTextInput("继续编辑")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("继续编辑", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("xnote-editor-title").assertIsDisplayed()
        assertTrue(runBlocking { library.getNote(note.id)!!.document.blocks.filterIsInstance<TextBlock>().any { it.inlines.plainText().contains("继续编辑") } })
        screenshot("reader-single-return")
    }

    @Test fun notebookEntryOffersContentsAndKeepsCurrentReadingPositionAfterEdit() {
        val book = runBlocking { library.createNotebook("阅读笔记本") }
        val notes = runBlocking {
            listOf("第一篇", "第二篇").mapIndexed { index, title ->
                library.saveNote(library.createNote(book.id).copy(title = title, sortIndex = index.toLong(),
                    backgroundKey = if (index == 1) BackgroundKey(GridBuiltinBackgroundId) else null,
                    document = NoteDocument(blocks = listOf(TextBlock("body-$index", inlines = listOf(InlineRun("正文$title")))))))
            }
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library) } }
        compose.onNodeWithTag("xnote-notebook-${book.id}").performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("打开阅读模式").performClick()
        compose.onNodeWithTag("xnote-reader-previous").assertIsNotEnabled()
        compose.onNodeWithContentDescription("笔记目录").performClick()
        compose.onNodeWithTag("xnote-reader-toc-${notes[1].id}").performClick()
        compose.onNodeWithText("正文第二篇").assertIsDisplayed()
        compose.onNodeWithTag("xnote-reader-next").assertIsNotEnabled()
        screenshot("reader-phone-grid")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("正文第二篇").assertIsDisplayed()
        compose.onNodeWithContentDescription("编辑当前笔记").performClick()
        compose.onNodeWithTag("xnote-editor-title").assertTextEquals("第二篇")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("正文第二篇").assertIsDisplayed()
        compose.onNodeWithTag("xnote-reader-previous").performClick()
        compose.onNodeWithText("正文第一篇").assertIsDisplayed()
    }

    @Test fun measuredLongTextTableAndAllMediaRemainCompleteAcrossReflow() {
        val text = "中文分页与样式 English words ** 保留原文。\n".repeat(70)
        val note = runBlocking { library.createNote(null) }.copy(document = NoteDocument(blocks = listOf(
            TextBlock("center", alignment = TextAlignment.Center, inlines = listOf(InlineRun("居中"))),
            TextBlock("right", alignment = TextAlignment.Right, inlines = listOf(InlineRun("右对齐"))),
            TextBlock("long", inlines = listOf(InlineRun(text, bold = true, italic = true, underline = true, highlight = true, linkUrl = "https://example.com"))),
            TableBlock("table", listOf(TableRow(listOf(TableCell(listOf(InlineRun("单元格\n".repeat(80)))), TableCell(listOf(InlineRun("另一列"))))))),
            ImageBlock("image", "media", scale = 3f, rotationDegrees = 45f, offsetY = 80f),
            StickerBlock("sticker", "media", rotationDegrees = 90f),
            DrawingBlock("drawing", "media", 200f, 2000f),
        )))
        var scale by mutableStateOf(1f)
        var units = emptyList<ReadingUnit<ReadingContent>>()
        var pages = emptyList<ReadingPage<ReadingContent>>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                XNoteTheme(reduceMotion = true) {
                    val measurer = rememberTextMeasurer()
                    val measured = measureReadingUnits(listOf(note), emptyMap(), measurer, MaterialTheme.typography,
                        MaterialTheme.colorScheme, 320, 400f, 1f, "未命名")
                    SideEffect { units = measured; pages = paginateReadingUnits(measured, 400f) }
                }
            }
        }
        compose.runOnIdle {
            assertTrue(pages.size > 5)
            assertTrue(pages.all { it.units.sumOf { unit -> unit.height.toDouble() } <= 400.01 })
            val lines = units.filter { it.blockId == "long" }.map { it.content as ReadingContent.TextLine }
            assertEquals(text, lines.joinToString("") { it.layout.layoutInput.text.text.substring(it.layout.getLineStart(it.line), it.layout.getLineEnd(it.line)) })
            assertTrue(lines.first().layout.layoutInput.text.spanStyles.isNotEmpty())
            for (id in listOf("center", "right")) {
                val layout = (units.first { it.blockId == id }.content as ReadingContent.TextLine).layout
                assertEquals(320, layout.size.width)
                assertTrue(layout.getLineLeft(0) > 0)
            }
            assertTrue(lines.first().layout.layoutInput.text.getStringAnnotations("URL", 0, 1).isNotEmpty())
            assertEquals(listOf("image", "sticker", "drawing"), units.filter { it.content is ReadingContent.Media }.map { it.blockId })
        }
        val oldCount = pages.size
        compose.runOnIdle { scale = 1.8f }
        compose.runOnIdle {
            assertTrue(pages.size > oldCount)
            assertEquals(units, pages.flatMap { it.units })
            assertTrue(pages.all { it.units.sumOf { unit -> unit.height.toDouble() } <= 400.01 })
        }
    }

    @Test fun readerReflowsAtLargeFontAndShowsMissingImageWithoutEditingControls() {
        val note = runBlocking { library.saveNote(library.createNote(null).copy(title = "长篇阅读", document = NoteDocument(blocks = listOf(
            TextBlock("body", inlines = listOf(InlineRun("阅读正文。".repeat(300)))), ImageBlock("image", "missing"),
        )))) }
        var fontScale by mutableStateOf(1f)
        compose.setContent {
            val original = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(original.density, fontScale)) {
                XNoteTheme(reduceMotion = true) { ReaderScreen(NotesRoute.Reader(noteId = note.id), listOf(note), NoteListSort.Manual,
                    library, BackgroundKey(RuledBuiltinBackgroundId), {}, {}) }
            }
        }
        compose.onNodeWithTag("xnote-reader-next").performClick()
        compose.runOnIdle { fontScale = 1.5f }
        compose.onNodeWithTag("xnote-reader-previous").assertIsEnabled()
        compose.onNodeWithTag("xnote-editor-body").assertDoesNotExist()
        screenshot("reader-phone-large-font")
        for (page in 0 until 100) {
            val enabled = compose.onNodeWithTag("xnote-reader-next").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()
            if (!enabled) break
            compose.onNodeWithTag("xnote-reader-next").performClick()
        }
        compose.onNodeWithTag("xnote-reader-media").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("图片无法读取").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun tabletDarkReaderRendersTransformedImageTableAndContents() {
        val note = runBlocking {
            val bitmap = android.graphics.Bitmap.createBitmap(600, 240, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.rgb(85, 150, 170))
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(240, 195, 90) }
            canvas.drawCircle(420f, 100f, 75f, paint)
            val bytes = java.io.ByteArrayOutputStream().apply { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
            bitmap.recycle()
            val attachment = library.putAttachment(AttachmentKind.Image, "image/png", "png", bytes.inputStream(), widthPx = 600, heightPx = 240)
            library.saveNote(library.createNote(library.createNotebook("阅读笔记本").id).copy(title = "阅读与留白", backgroundKey = BackgroundKey(CreamBuiltinBackgroundId),
                document = NoteDocument(blocks = listOf(
                    TextBlock("intro", inlines = listOf(InlineRun("纸张背景、富文本与图片在分页中保持一致。", highlight = true))),
                    ImageBlock("image", attachment.id, scale = 0.8f, rotationDegrees = 12f, offsetX = 20f),
                    TableBlock("table", listOf(TableRow(listOf(TableCell(listOf(InlineRun("项目", bold = true))), TableCell(listOf(InlineRun("状态", bold = true))))),
                        TableRow(listOf(TableCell(listOf(InlineRun("阅读模式"))), TableCell(listOf(InlineRun("已完成"))))))),
                    TextBlock("quote", quoted = true, inlines = listOf(InlineRun("慢慢阅读，让想法清晰。", italic = true))),
                ))))
        }
        compose.setContent {
            val darkConfiguration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalConfiguration provides darkConfiguration) {
                XNoteTheme(darkTheme = true, reduceMotion = true) { ReaderScreen(NotesRoute.Reader(notebookId = note.notebookId), listOf(note),
                    NoteListSort.Manual, library, BackgroundKey(DefaultBuiltinBackgroundId), {}, {}) }
            }
        }
        compose.onNodeWithTag("xnote-reader-media").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("正在加载图片…").fetchSemanticsNodes().isEmpty() }
        screenshot("reader-tablet-dark")
        compose.onNodeWithContentDescription("笔记目录").performClick()
        compose.onNodeWithTag("xnote-reader-toc-${note.id}").assertIsDisplayed()
        screenshot("reader-tablet-contents")
    }

    // -- Functions

    private fun screenshot(name: String) {
        val file = File(context.getExternalFilesDir(null), "$name.png")
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
