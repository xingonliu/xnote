package com.xnote.app

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.files.renderDrawing
import com.xnote.app.data.files.saveMediaBitmap
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.*
import com.xnote.app.feature.creative.DrawingScreen
import com.xnote.app.feature.reader.*
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// -- Type Definitions

class S12FlowTest {
    // -- State and Variables

    private val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = XNoteDatabase.createInMemory(context)
    private val root = File(context.cacheDir, "s12-flow-${System.nanoTime()}")
    private val library = NoteLibrary(database, AttachmentFileStore(root), SystemEpochClock)
    private val cleanup = object : TestWatcher() {
        override fun finished(description: Description) { database.close(); root.deleteRecursively() }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(cleanup).around(compose)

    // -- Functions

    @Test fun cutoutPreviewMaskCorrectionSavesLibraryAndInsertsTransparentResult() {
        val assets = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val modelDirectory = File(context.filesDir, "cutout-models").apply { mkdirs() }
        assets.open("u2netp.onnx").use { input -> File(modelDirectory, "u2netp.onnx").outputStream().use(input::copyTo) }
        val photo = File(root, "source.jpg").apply { parentFile?.mkdirs() }
        assets.open("sample.jpg").use { input -> photo.outputStream().use(input::copyTo) }
        var inserted: Attachment? = null
        compose.setContent { XNoteTheme(reduceMotion = true) {
            com.xnote.app.feature.creative.CutoutScreen(photo, library, "cutout-ui", {}, { inserted = it })
        } }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("可在蒙版或结果上涂抹修正").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("原图").performClick()
        compose.onNodeWithText("蒙版").performClick()
        compose.onNodeWithText("擦除").performClick()
        compose.onNodeWithTag("xnote-cutout-preview").performTouchInput { swipe(center, centerRight, 350) }
        compose.onNodeWithText("撤销修正").performClick()
        compose.onNodeWithText("结果").performClick()
        compose.onNodeWithTag("xnote-sticker-name").performTextReplacement("羊驼贴纸")
        screenshot("s12-cutout")
        compose.onNodeWithText("保存为贴纸").performClick()
        compose.waitUntil(10_000) { runBlocking { library.observeStickers().first() }.any { it.name == "羊驼贴纸" } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("插入笔记").fetchSemanticsNodes().singleOrNull()?.config?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false }
        compose.onNodeWithText("插入笔记").performClick()
        compose.waitUntil(10_000) { inserted != null }
        val bitmap = android.graphics.BitmapFactory.decodeFile(library.attachmentFile(inserted!!).path)
        assertTrue(bitmap.hasAlpha())
        val alpha = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(alpha, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(alpha.any { android.graphics.Color.alpha(it) < 10 })
        assertTrue(alpha.any { android.graphics.Color.alpha(it) > 245 })
        bitmap.recycle()
    }

    @Test fun stickerLibrarySupportsSearchPreviewRenameInsertAndDelete() {
        val sticker = runBlocking {
            val bitmap = renderDrawing(listOf(DrawingStroke(listOf(DrawingPoint(.2f, .5f), DrawingPoint(.8f, .5f)), color = 0xff1769e0, width = 100f)))
            val attachment = try { saveMediaBitmap(context, library, bitmap, AttachmentKind.Sticker, "sticker-ui") } finally { bitmap.recycle() }
            library.saveSticker(attachment.id, "蓝色笔迹")
        }
        var inserted = false
        compose.setContent { XNoteTheme(darkTheme = true, reduceMotion = true) {
            com.xnote.app.feature.creative.StickerLibraryScreen(library, {}, { inserted = true })
        } }
        compose.onNodeWithTag("xnote-sticker-search").performTextReplacement("蓝色")
        compose.onNodeWithText("最近创建").performClick()
        compose.onNodeWithText("蓝色笔迹").performClick()
        compose.onNodeWithTag("xnote-sticker-rename").performTextReplacement("透明蓝线")
        compose.onNodeWithText("重命名").performClick()
        compose.waitUntil(10_000) { runBlocking { library.observeStickers().first() }.single().name == "透明蓝线" }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("插入笔记").fetchSemanticsNodes().singleOrNull()?.config?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false }
        screenshot("s12-sticker-dark")
        compose.onNodeWithText("插入笔记").performClick()
        compose.waitUntil { inserted }
        compose.onNodeWithText("删除").performClick()
        compose.onAllNodesWithText("删除").onLast().performClick()
        compose.waitUntil(10_000) { runBlocking { library.observeStickers().first() }.isEmpty() }
        assertTrue(library.attachmentFile(runBlocking { library.getAttachment(sticker.attachmentId) }!!).exists())
    }

    @Test fun wrapReleasesFullWidthAndLongFlowPaginationPreservesEveryCharacter() {
        val text = "环绕后的中文与 English words，必须完整保留。".repeat(80)
        val note = runBlocking { library.createNote(null) }.copy(document = NoteDocument(blocks = listOf(
            StickerBlock("s", "media", layout = MediaLayout.Wrap), TextBlock("body", inlines = listOf(InlineRun(text, bold = true))),
            ImageBlock("floating", "other", layout = MediaLayout.Float, rotationDegrees = 30f), TextBlock("end", inlines = listOf(InlineRun("尾部文字"))),
        )))
        var units = emptyList<ReadingUnit<ReadingContent>>()
        compose.setContent {
            XNoteTheme(reduceMotion = true) {
                val measured = measureReadingUnits(listOf(note), emptyMap(), rememberTextMeasurer(), MaterialTheme.typography,
                    MaterialTheme.colorScheme, 360, 480f, 1f, "未命名")
                SideEffect { units = measured }
            }
        }
        compose.runOnIdle {
            val flows = units.mapNotNull { it.content as? ReadingContent.Flow }
            val lines = flows.flatMap { it.placements }.mapNotNull { it.content as? ReadingContent.TextLine }
            val output = lines.joinToString("") { it.layout.layoutInput.text.text.substring(it.layout.getLineStart(0), it.layout.getLineEnd(0)) }
            assertEquals(text + "尾部文字", output)
            assertTrue(lines.first().x > 0f)
            assertTrue(lines.drop(8).any { it.x == 0f })
            assertEquals(listOf("media", "other"), flows.flatMap { it.mediaAttachmentIds() })
            assertTrue(flows.size > 2)
            assertTrue(units.all { it.height <= 480f })
            assertTrue(paginateReadingUnits(units, 480f).all { page -> page.units.sumOf { it.height.toDouble() } <= 480 })
        }
    }

    @Test fun drawingUiSupportsInkEraseUndoRedoClearAndSave() {
        var saved: DrawingBlock? = null
        compose.setContent { XNoteTheme(reduceMotion = true) { DrawingScreen(library, "ui-test", null, {}, { saved = it }) } }
        compose.onNodeWithTag("xnote-drawing-canvas").performTouchInput { swipe(centerLeft, centerRight, 400) }
        compose.onNodeWithText("橡皮").performClick()
        compose.onNodeWithTag("xnote-drawing-canvas").performTouchInput { swipe(topCenter, bottomCenter, 400) }
        compose.onNodeWithText("撤销").performClick()
        compose.onNodeWithText("重做").performClick()
        compose.onNodeWithText("清空").performClick()
        compose.onNodeWithText("撤销").performClick()
        compose.onNodeWithContentDescription("完成").performClick()
        compose.waitUntil(10_000) { saved != null }
        assertEquals(2, saved!!.strokes.size)
        assertTrue(saved!!.strokes.last().erase)
        assertTrue(runBlocking { library.getAttachment(saved!!.attachmentId) } != null)
        screenshot("s12-drawing")
    }

    @Test fun editorShowsWrappedStickerAndReopensItsDrawing() {
        val note = runBlocking {
            val bitmap = renderDrawing(listOf(DrawingStroke(listOf(DrawingPoint(.1f, .2f), DrawingPoint(.9f, .8f)), color = 0xff1769e0, width = 60f)))
            val attachment = try { saveMediaBitmap(context, library, bitmap, AttachmentKind.Sticker, "ui") } finally { bitmap.recycle() }
            library.saveNote(library.createNote(null).copy(title = "创作验收", document = NoteDocument(blocks = listOf(
                StickerBlock("s", attachment.id, layout = MediaLayout.Wrap),
                TextBlock("body", inlines = listOf(InlineRun("文字应当绕过贴纸，在下方恢复完整宽度。".repeat(12)))),
                DrawingBlock("d", attachment.id, 1024f, 768f, listOf(DrawingStroke(listOf(DrawingPoint(.1f, .2f), DrawingPoint(.9f, .8f))))),
            ))))
        }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library) } }
        compose.onNodeWithTag("xnote-collection-all").performClick()
        compose.onNodeWithText(note.title).performClick()
        compose.onNodeWithTag("xnote-image-s").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(context.getString(R.string.image_loading)).fetchSemanticsNodes().isEmpty() }
        screenshot("s12-wrap-editor")
        compose.onNodeWithText("编辑画板").performScrollTo().performClick()
        compose.onNodeWithTag("xnote-drawing-canvas").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("xnote-editor-title").assertExists()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("打开阅读模式").performClick()
        compose.onNodeWithTag("xnote-reader-page").assertExists()
        screenshot("s12-wrap-reader")
    }

    @Test fun wrappedNativeEditorKeepsChineseCompositionAndPersistsAtCaret() {
        val original = "环绕中文正文用于验证输入位置。".repeat(8)
        val note = runBlocking { library.saveNote(library.createNote(null).copy(title = "环绕输入", document = NoteDocument(blocks = listOf(
            ImageBlock("image", "missing", layout = MediaLayout.Wrap), TextBlock("body", inlines = listOf(InlineRun(original))),
        )))) }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library) } }
        compose.onNodeWithTag("xnote-collection-all").performClick()
        compose.onNodeWithText(note.title).performClick()
        compose.runOnIdle {
            val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).single()
            fun find(view: android.view.View): android.widget.EditText? {
                if (view is android.widget.EditText) return view
                if (view is android.view.ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
                return null
            }
            val editor = requireNotNull(find(activity.window.decorView))
            editor.requestFocus()
            editor.setSelection(2)
            val connection = requireNotNull(editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
            connection.setComposingText("输入中", 1)
            connection.commitText("输入完成", 1)
            connection.finishComposingText()
            assertEquals(original.take(2) + "输入完成" + original.drop(2), editor.text.toString())
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(10_000) { runBlocking { library.getNote(note.id)!!.document.blocks.filterIsInstance<TextBlock>().single().inlines.plainText() }.contains("输入完成") }
        assertEquals(original.take(2) + "输入完成" + original.drop(2), runBlocking { library.getNote(note.id)!!.document.blocks.filterIsInstance<TextBlock>().single().inlines.plainText() })
    }

    @Test fun exportCapturesTransparentDrawingAndWrappedSticker() {
        val before = File(context.cacheDir, "exports").listFiles().orEmpty().toSet()
        val note = runBlocking {
            val bitmap = renderDrawing(listOf(DrawingStroke(listOf(DrawingPoint(.1f, .5f), DrawingPoint(.9f, .5f)), color = 0xff1769e0, width = 60f)))
            val attachment = try { saveMediaBitmap(context, library, bitmap, AttachmentKind.Drawing, "export") } finally { bitmap.recycle() }
            library.saveNote(library.createNote(null).copy(title = "创作导出", document = NoteDocument(blocks = listOf(
                StickerBlock("s", attachment.id, layout = MediaLayout.Wrap), TextBlock("text", inlines = listOf(InlineRun("环绕导出文字完整。".repeat(10)))),
                DrawingBlock("d", attachment.id, 1024f, 768f),
            ))))
        }
        compose.setContent { XNoteTheme(darkTheme = false, reduceMotion = true) {
            com.xnote.app.design.XNoteToastProvider {
                com.xnote.app.feature.export.ExportScreen(note.id, library, defaultBackgroundKey(), {})
            }
        } }
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("xnote-export-preview").fetchSemanticsNodes().isNotEmpty() }
        val directories = File(context.cacheDir, "exports").listFiles().orEmpty().filter { it !in before }
        val files = directories.flatMap { it.walkTopDown().filter { file -> file.extension == "png" }.toList() }
        assertTrue(files.isNotEmpty())
        var bluePages = 0
        files.forEach { file ->
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.path)
            assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(0, 0))
            if ((0 until bitmap.height step 4).any { y -> (0 until bitmap.width step 4).any { x ->
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.blue(pixel) > 180 && android.graphics.Color.red(pixel) < 60
            } }) bluePages++
            bitmap.recycle()
        }
        assertTrue(bluePages > 0)
        screenshot("s12-export")
        directories.forEach { it.deleteRecursively() }
    }

    private fun screenshot(name: String) {
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
