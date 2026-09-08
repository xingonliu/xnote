package com.xnote.app

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.data.settings.InMemoryAppSettingsRepository
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.model.SystemEpochClock
import com.xnote.app.domain.document.InlineRun
import com.xnote.app.domain.document.EditorSelection
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.TableBlock
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.defaultAppSettings
import com.xnote.app.domain.model.GridBuiltinBackgroundId
import com.xnote.app.domain.model.RuledBuiltinBackgroundId
import com.xnote.app.domain.text.extractPlainText
import com.xnote.app.feature.notes.editor.NoteEditorSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

// -- Tests

class NotesFlowTest {
    private val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = XNoteDatabase.createInMemory(context)
    private val filesRoot = File(context.cacheDir, "xnote-s3-ui-${System.nanoTime()}")
    private val library = NoteLibrary(
        database = database,
        files = AttachmentFileStore(filesRoot),
        clock = SystemEpochClock,
    )
    private val cleanupRule = object : TestWatcher() {
        override fun finished(description: Description) {
            database.close()
            filesRoot.deleteRecursively()
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(cleanupRule).around(composeRule)

    @Test
    fun existingTableAtDocumentEndAllowsTypingAfterItAndSaving() {
        val note = runBlocking {
            library.saveNote(library.createNote(null).copy(title = "编辑修复验收", document = NoteDocument(blocks = listOf(
                TextBlock("before", inlines = listOf(InlineRun("表格前正文"))),
                TextBlock("number", listMarker = com.xnote.app.domain.document.ListMarker.Numbered,
                    inlines = listOf(InlineRun("编号第一行\n编号第二行"))),
                TextBlock("check", listMarker = com.xnote.app.domain.document.ListMarker.Checklist,
                    inlines = listOf(InlineRun("清单第一行\n清单第二行"))),
                com.xnote.app.domain.document.emptyTableBlock("table"),
            ))))
        }
        composeRule.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(noteLibrary = library) } }
        composeRule.onNodeWithText("编辑修复验收").performClick()
        composeRule.onNodeWithTag("xnote-editor-continue-after-table").performScrollTo().assertIsDisplayed()
        val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "editor-fixes.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        composeRule.onNodeWithTag("xnote-editor-continue-after-table").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.isFocused()).performTextInput("表格后继续输入")
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("全部笔记").fetchSemanticsNodes().isNotEmpty() }
        val saved = runBlocking { requireNotNull(library.getNote(note.id)).document }
        assertEquals("表格后继续输入", (saved.blocks.last() as TextBlock).inlines.joinToString("") { it.text })
        composeRule.onNodeWithText("编辑修复验收").performClick()
        composeRule.onNodeWithText("表格后继续输入").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun imageSourceDrawerAndImageOperationsWorkInEditor() {
        val note = runBlocking {
            val source = File(context.cacheDir, "ui-image-${System.nanoTime()}.png")
            val bitmap = android.graphics.Bitmap.createBitmap(640, 360, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.rgb(80, 130, 190))
            source.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            try {
                val attachment = com.xnote.app.data.files.importNoteImage(context, library, android.net.Uri.fromFile(source), "ui-test")
                library.saveNote(library.createNote(null).copy(title = "图片验收", document = NoteDocument(blocks = listOf(
                    TextBlock("before", inlines = listOf(InlineRun("图片前的正文"))),
                    com.xnote.app.domain.document.ImageBlock("photo", attachment.id),
                    TextBlock("after", inlines = listOf(InlineRun("图片后的正文"))),
                ))))
            } finally { source.delete() }
        }
        composeRule.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(noteLibrary = library) } }
        composeRule.onNodeWithText("图片验收").performClick()
        composeRule.onNodeWithTag("xnote-add-image").performClick()
        composeRule.onNodeWithText("相机").assertIsDisplayed()
        composeRule.onNodeWithText("相册").assertIsDisplayed()
        composeRule.onNodeWithTag("xnote-overlay-scrim").performTouchInput {
            click(percentOffset(0.5f, 0.05f))
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("相机").fetchSemanticsNodes().isEmpty()
        }
        val screenshot = File(context.getExternalFilesDir(null), "s7-editor.png")
        screenshot.outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        composeRule.onNodeWithTag("xnote-image-photo").performScrollTo().performClick()
        composeRule.onNodeWithText("向右旋转 90°").performScrollTo().performClick()
        composeRule.onNodeWithTag("xnote-image-photo").performScrollTo().performClick()
        composeRule.onNodeWithText("复制图片").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("撤销").performClick()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("全部笔记").fetchSemanticsNodes().isNotEmpty() }
        val images = runBlocking { library.getNote(note.id)!!.document.blocks.filterIsInstance<com.xnote.app.domain.document.ImageBlock>() }
        assertEquals(1, images.size)
        assertEquals(90f, images.single().rotationDegrees)
    }

    @Test
    fun createNotePersistsTitleAndBodyAfterReturningHome() {
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithTag("xnote-create-note").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("xnote-editor-title").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("xnote-editor-title").performTextInput("会议记录")
        composeRule.onNodeWithTag("xnote-editor-body").performTextInput("今天讨论进度")
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("会议记录").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("会议记录").assertIsDisplayed()
        composeRule.onNodeWithText("今天讨论进度", substring = true).assertIsDisplayed()
    }

    @Test
    fun composingTextIsPersistedWhenTheEditorFlushes() = runTest {
        val note = library.createNote(null)
        val session = NoteEditorSession(library, note.id, this)
        session.load()
        val block = session.document.blocks.filterIsInstance<TextBlock>().first()

        session.onPlainTextChange(
            target = EditorSelection(blockId = block.id, start = 2, end = 2),
            oldText = "",
            newText = "尾字",
            composing = true,
        )
        session.flushSave()

        assertEquals("尾字", extractPlainText(requireNotNull(library.getNote(note.id))))
    }

    @Test
    fun deletingANotebookRemovesItsNotesFromTheHomeList() = runTest {
        val notebook = library.createNotebook("临时本")
        val note = library.createNote(notebook.id)
        library.saveNote(note.copy(title = "应进入回收站"))

        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithTag("xnote-notebook-picker").performClick()
        composeRule.onNodeWithContentDescription("打开笔记本").performClick()
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("删除笔记本").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("应进入回收站").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("全部笔记").assertIsDisplayed()
        val trashed = library.getNote(note.id)
        assertTrue(trashed?.isTrashed == true)
        assertNull(trashed?.notebookId)
        assertEquals("临时本", trashed?.originalNotebookName)
        assertTrue(library.observeTrashedNotes().first().any { it.id == note.id })
    }

    @Test
    fun editorHeaderMovesNoteAndLaterSaveKeepsTheDestination() = runTest {
        val source = library.createNotebook("来源本")
        val destination = library.createNotebook("目标本")
        val note = library.createNote(source.id)
        library.saveNote(note.copy(title = "待移动笔记"))

        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithText("待移动笔记").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("xnote-editor-title").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("选择笔记本").performClick()
        composeRule.onNodeWithText("目标本").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("目标本").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("xnote-editor-title").performTextInput("已编辑")
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("全部笔记").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(destination.id, library.getNote(note.id)?.notebookId)
    }

    @Test
    fun editorRemainsUsableWithLargeFontsAndReducedMotion() {
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = currentDensity.density,
                        fontScale = 2f,
                    ),
                ) {
                    XNoteApp(noteLibrary = library)
                }
            }
        }

        composeRule.onNodeWithTag("xnote-create-note").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("xnote-editor-body").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("xnote-editor-title").assertIsDisplayed()
        composeRule.onNodeWithTag("xnote-editor-body").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("撤销").assertIsDisplayed()
    }

    @Test
    fun richTextToolbarAppliesInlineStyleAndEditsATable() = runTest {
        val note = library.createNote(null)
        library.saveNote(note.copy(title = "工具栏测试"))
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithText("工具栏测试").performClick()
        composeRule.onNodeWithTag("xnote-editor-body").performTextInput("plain")
        composeRule.onNodeWithText("粗体").performClick()
        composeRule.onNodeWithTag("xnote-editor-body").performTextInput("bold")
        composeRule.onNodeWithText("表格").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("单元格").fetchSemanticsNodes().size == 4
        }
        composeRule.onNodeWithText("表格").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("下方插入行").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("下方插入行").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("单元格").fetchSemanticsNodes().size == 6
        }
        composeRule.onAllNodes(hasSetTextAction()).onLast().performScrollTo().performClick().performTextInput("表格后的正文")
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("全部笔记").fetchSemanticsNodes().isNotEmpty()
        }

        val saved = requireNotNull(library.getNote(note.id))
        val text = requireNotNull(saved.document).blocks.filterIsInstance<TextBlock>().first()
        val table = saved.document.blocks.filterIsInstance<TableBlock>().single()
        assertEquals("表格后的正文", (saved.document.blocks.last() as TextBlock).inlines.joinToString("") { it.text })
        assertEquals("plainbold", text.inlines.joinToString(separator = "") { it.text })
        assertEquals("plain", text.inlines.first().text)
        assertEquals("bold", text.inlines.last().text)
        assertTrue(text.inlines.last().bold)
        assertEquals(3, table.rows.size)
        assertTrue(table.rows.all { it.cells.size == 2 })
    }

    @Test
    fun markdownShortcutTurnsHeadingPrefixIntoHeadingAndUndoRestoresCharacters() {
        runTest {
            library.saveNote(library.createNote(null).copy(title = "快捷输入"))
        }
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithText("快捷输入").performClick()
        composeRule.onNodeWithTag("xnote-editor-body").performTextInput("# ")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("xnote-editor-heading-collapse").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("xnote-editor-heading-collapse").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("撤销").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("xnote-editor-heading-collapse").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("xnote-editor-body").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("更多").performClick()
        assertTrue(composeRule.onAllNodesWithText("转换为 Markdown").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun profileMarkdownShortcutSwitchIsVisibleAndCanBeTurnedOff() {
        val settings = InMemoryAppSettingsRepository(defaultAppSettings())
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library, settings = settings)
            }
        }

        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("Markdown 快捷输入").assertIsDisplayed()
        composeRule.onNodeWithTag("xnote-markdown-shortcuts-switch").performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { settings.settings.first().markdownShortcutsEnabled.not() }
        }
    }

    @Test
    fun searchFindsChineseBodyTextAndOpensTheNote() = runTest {
        val note = library.createNote(null)
        library.saveNote(
            note.copy(
                title = "发布安排",
                document = NoteDocument(
                    blocks = listOf(
                        TextBlock(id = "body", inlines = listOf(InlineRun("我的笔记本记录了发布计划"))),
                    ),
                ),
            ),
        )
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithTag("xnote-search-field").performTextInput("笔记本")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("发布安排").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("我的笔记本记录了发布计划", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("发布安排").performClick()
        composeRule.onNodeWithTag("xnote-editor-title").assertIsDisplayed()
    }

    @Test
    fun recycleBinMultiSelectRestoresNotes() = runTest {
        val notebook = library.createNotebook("恢复目标")
        val first = library.createNote(notebook.id)
        val second = library.createNote(notebook.id)
        library.saveNote(first.copy(title = "待恢复一"))
        library.saveNote(second.copy(title = "待恢复二"))
        library.trashNotes(listOf(first.id, second.id))
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("回收站").performClick()
        composeRule.onNodeWithContentDescription("选择笔记").performClick()
        composeRule.onNodeWithText("待恢复一").performClick()
        composeRule.onNodeWithText("待恢复二").performClick()
        composeRule.onNodeWithText("恢复").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("回收站是空的").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(notebook.id, library.getNote(first.id)?.notebookId)
        assertEquals(notebook.id, library.getNote(second.id)?.notebookId)
    }

    @Test
    fun recycleBinPermanentlyDeletesOneNoteAndCanClearTheRest() = runTest {
        val first = library.saveNote(library.createNote(null).copy(title = "永久删除目标"))
        val second = library.saveNote(library.createNote(null).copy(title = "清空目标"))
        library.trashNotes(listOf(first.id, second.id))
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("回收站").performClick()
        composeRule.onAllNodesWithText("永久删除")[0].performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("永久删除目标").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("清空回收站").performClick()
        composeRule.onNodeWithText("清空").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("回收站是空的").fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(library.getNote(first.id))
        assertNull(library.getNote(second.id))
    }

    @Test
    fun profileDefaultBackgroundSettingPersistsTheSelectedPreset() {
        val settings = InMemoryAppSettingsRepository()
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library, settings = settings)
            }
        }

        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("默认笔记背景").performClick()
        composeRule.onNodeWithText("所有未设置专属背景的笔记").assertIsDisplayed()
        composeRule.onNodeWithText("方格纸").performClick()

        composeRule.waitUntil(5_000) {
            runBlocking {
                settings.settings.first().defaultBackground ==
                    BackgroundKey(GridBuiltinBackgroundId)
            }
        }
    }

    @Test
    fun editorBackgroundOverrideCanReturnToDefaultInheritance() {
        val note = runBlocking {
            library.saveNote(library.createNote(null).copy(title = "背景测试"))
        }
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithText("背景测试").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("xnote-editor-title").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("笔记背景").performClick()
        composeRule.onNodeWithText("仅当前笔记").assertIsDisplayed()
        composeRule.onNodeWithText("横线纸").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            runBlocking {
                library.getNote(note.id)?.backgroundKey ==
                    BackgroundKey(RuledBuiltinBackgroundId)
            }
        }

        composeRule.onNodeWithText("使用默认背景").performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { library.getNote(note.id)?.backgroundKey == null }
        }
    }

    @Test
    fun editorBackgroundPickerDismissesTheKeyboardAndShowsEveryPreset() {
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithTag("xnote-create-note").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("xnote-editor-body").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("xnote-editor-body").performTextInput("keyboard")
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("笔记背景").performClick()

        composeRule.onNodeWithText("暖白纸").assertIsDisplayed()
        composeRule.onNodeWithText("奶油纹理").assertIsDisplayed()
        composeRule.onNodeWithText("横线纸").assertIsDisplayed()
        composeRule.onNodeWithText("方格纸").assertIsDisplayed()
    }
}
