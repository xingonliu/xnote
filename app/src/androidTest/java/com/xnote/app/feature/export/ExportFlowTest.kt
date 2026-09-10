package com.xnote.app.feature.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.XNoteApp
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.document.*
import com.xnote.app.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

// -- Tests

class ExportFlowTest {
    private val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = XNoteDatabase.createInMemory(context)
    private val root = File(context.cacheDir, "export-test-${System.nanoTime()}")
    private val library = NoteLibrary(database, AttachmentFileStore(root), SystemEpochClock)
    private val priorDirs = File(context.cacheDir, "exports").listFiles().orEmpty().toSet()
    private val gallery = mutableListOf<Uri>()
    private val cleanup = object : TestWatcher() {
        override fun finished(description: Description) {
            gallery.forEach { context.contentResolver.delete(it, null, null) }
            database.close()
            root.deleteRecursively()
            exportDirs().forEach { it.deleteRecursively() }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(cleanup).around(compose)

    @Test fun editorExportFlushesLatestTextAndReturnsToEditor() {
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library) } }
        compose.onNodeWithTag("xnote-create-note").performClick()
        compose.onNodeWithTag("xnote-editor-title").performTextInput("即时导出标题")
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("导出图片").performClick()
        awaitPreview()
        assertEquals("即时导出标题", runBlocking { library.observeActiveNotes().first() }.first().title)
        assertEquals(1, exportFiles().size)
        compose.onNodeWithTag("xnote-export-next").assertIsNotEnabled()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("xnote-editor-title").assertTextEquals("即时导出标题")
    }

    @Test fun multiPageFreezesBackgroundAndThemeAndSharesExactFiles() {
        val note = runBlocking { library.saveNote(library.createNote(null).copy(title = "分页导出",
            document = NoteDocument(blocks = listOf(TextBlock("long", inlines = listOf(InlineRun("分页正文与背景保持一致。\n".repeat(85)))))))) }
        var background by mutableStateOf(BackgroundKey(GridBuiltinBackgroundId))
        var dark by mutableStateOf(false)
        compose.setContent { XNoteTheme(darkTheme = dark, reduceMotion = true) {
            ExportScreen(note.id, library, background, {})
        } }
        compose.waitUntil(20_000) { exportFiles().isNotEmpty() }
        compose.runOnIdle { background = BackgroundKey(CreamBuiltinBackgroundId); dark = true }
        awaitPreview()
        val files = exportFiles()
        assertTrue(files.size > 1)
        files.forEach { file ->
            val bitmap = BitmapFactory.decodeFile(file.path)
            assertTrue(bitmap.width <= 2048 && bitmap.height <= 2048)
            assertEquals(Color.WHITE, bitmap.getPixel(5, 5))
            assertEquals(255, Color.alpha(bitmap.getPixel(0, 0)))
            bitmap.recycle()
        }
        compose.onNodeWithTag("xnote-export-next").performClick()
        compose.onNodeWithText("第 2 / ${files.size} 张").assertIsDisplayed()
        val intent = exportShareIntent(context, files)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals(files.size, intent.clipData!!.itemCount)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        files.forEachIndexed { index, file ->
            val bytes = context.contentResolver.openInputStream(intent.clipData!!.getItemAt(index).uri)!!.use { it.readBytes() }
            assertArrayEquals(file.readBytes(), bytes)
        }
        screenshot("export-phone-multiple")
    }

    @Test fun darkTabletCapturesMediaAndGalleryMatchesPreviewFiles() {
        val note = runBlocking {
            val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val bytes = java.io.ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
            bitmap.recycle()
            val attachment = library.putAttachment(AttachmentKind.Image, "image/png", "png", bytes.inputStream(), widthPx = 200, heightPx = 100)
            library.saveNote(library.createNote(null).copy(title = "导出图片与表格", backgroundKey = BackgroundKey(RuledBuiltinBackgroundId),
                document = NoteDocument(blocks = listOf(
                    TextBlock("quote", quoted = true, inlines = listOf(InlineRun("保留富文本与纸张", bold = true, highlight = true))),
                    ImageBlock("image", attachment.id, scale = 0.4f, rotationDegrees = 25f),
                    TableBlock("table", listOf(TableRow(listOf(TableCell(listOf(InlineRun("项目"))), TableCell(listOf(InlineRun("已完成"))))))),
                ))))
        }
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            XNoteTheme(darkTheme = true, reduceMotion = true) { ExportScreen(note.id, library, BackgroundKey(DefaultBuiltinBackgroundId), {}) }
        } }
        awaitPreview()
        val files = exportFiles()
        assertEquals(1, files.size)
        val bitmap = BitmapFactory.decodeFile(files.first().path)
        assertEquals(Color.rgb(28, 28, 30), bitmap.getPixel(5, 5))
        assertTrue((0 until bitmap.height step 4).any { y -> (0 until bitmap.width step 4).any { x ->
            val pixel = bitmap.getPixel(x, y); Color.red(pixel) > 220 && Color.green(pixel) < 30
        } })
        bitmap.recycle()
        gallery += runBlocking { saveExportToGallery(context, files) }
        assertArrayEquals(files.first().readBytes(), context.contentResolver.openInputStream(gallery.first())!!.use { it.readBytes() })
        context.contentResolver.query(gallery.first(), arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        assertEquals(Intent.ACTION_SEND, exportShareIntent(context, files).action)
        screenshot("export-tablet-dark")
    }

    @Test fun largeFontKeepsPreviewAboveControls() {
        val note = runBlocking { library.saveNote(library.createNote(null).copy(title = "大字体预览")) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                XNoteTheme(reduceMotion = true) { ExportScreen(note.id, library, BackgroundKey(DefaultBuiltinBackgroundId), {}) }
            }
        }
        awaitPreview()
        compose.onNodeWithTag("xnote-export-save").assertIsDisplayed().assertIsEnabled()
        val preview = compose.onNodeWithTag("xnote-export-preview").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("xnote-export-previous").fetchSemanticsNode().boundsInRoot
        assertTrue(preview.bottom <= controls.top)
        screenshot("export-phone-large-font")
    }

    @Test fun missingNoteCanRetryAndFailedGalleryWriteRollsBack() {
        compose.setContent { XNoteTheme(reduceMotion = true) { ExportScreen("missing", library, BackgroundKey(DefaultBuiltinBackgroundId), {}) } }
        compose.onNodeWithText("导出失败，请重试").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithTag("xnote-export-save").assertIsNotEnabled()
        val directory = createExportDirectory(context)
        val first = File(directory, "first.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val result = runCatching { runBlocking { saveExportToGallery(context, listOf(first, File(directory, "missing.png"))) } }
        assertTrue(result.isFailure)
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("XNote-${directory.name}-%"), null)!!.use { assertEquals(0, it.count) }
    }

    // -- Functions

    private fun exportDirs() = File(context.cacheDir, "exports").listFiles().orEmpty().filter { it !in priorDirs }
    private fun exportFiles() = exportDirs().flatMap { it.listFiles().orEmpty().toList() }.filter { it.extension == "png" && it.length() > 0 }.sortedBy { it.name }
    private fun awaitPreview() { compose.waitUntil(30_000) { compose.onAllNodesWithTag("xnote-export-preview").fetchSemanticsNodes().isNotEmpty() } }
    private fun screenshot(name: String) {
        checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).let { bitmap ->
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
