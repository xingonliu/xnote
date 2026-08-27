package com.xnote.app

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.data.settings.InMemoryAppSettingsRepository
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.model.SystemEpochClock
import com.xnote.app.domain.document.InlineRun
import com.xnote.app.domain.document.NoteDocument
import com.xnote.app.domain.document.TextBlock
import com.xnote.app.domain.model.NoteKind
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.GridBuiltinBackgroundId
import com.xnote.app.domain.model.RuledBuiltinBackgroundId
import com.xnote.app.domain.model.encode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

// -- Tests

class NotesFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = XNoteDatabase.createInMemory(context)
    private val filesRoot = File(context.cacheDir, "xnote-s3-ui-${System.nanoTime()}")
    private val library = NoteLibrary(
        database = database,
        files = AttachmentFileStore(filesRoot),
        clock = SystemEpochClock,
    )

    @After
    fun tearDown() {
        database.close()
        filesRoot.deleteRecursively()
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
    fun deletingANotebookRemovesItsNotesFromTheHomeList() = runTest {
        val notebook = library.createNotebook("临时本")
        val note = library.createRichNote(notebook.id)
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
        val note = library.createRichNote(source.id)
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
    fun convertToMarkdownEditsPreviewsAndReturnsToEditing() {
        var noteId = ""
        runTest {
            val created = library.createRichNote(null)
            noteId = library.saveNote(
                created.copy(
                    title = "转换测试",
                    document = NoteDocument(
                        blocks = listOf(
                            TextBlock(id = "body", inlines = listOf(InlineRun("预览正文"))),
                        ),
                    ),
                ),
            ).id
        }
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithText("转换测试").performClick()
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("转换为 Markdown").performClick()
        composeRule.onNodeWithText("永久转换").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("xnote-markdown-editor").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("xnote-markdown-editor").assertIsDisplayed()
        runTest {
            assertEquals("# 转换测试\n\n预览正文", library.getNote(noteId)?.markdownText)
        }
        composeRule.onNodeWithTag("xnote-markdown-editor")
            .performTextReplacement("# 编辑后标题\n\n编辑后的正文")
        composeRule.onNodeWithTag("xnote-markdown-done").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("xnote-markdown-preview").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("编辑后的正文").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("编辑 Markdown").performClick()
        composeRule.onNodeWithTag("xnote-markdown-editor").assertIsDisplayed()

        runTest {
            assertEquals(NoteKind.Markdown, library.getNote(noteId)?.kind)
            assertEquals("编辑后标题", library.getNote(noteId)?.title)
            assertEquals("# 编辑后标题\n\n编辑后的正文", library.getNote(noteId)?.markdownText)
            assertEquals(1, library.getNoteRevisions(noteId).size)
        }
    }

    @Test
    fun searchFindsChineseBodyTextAndOpensTheNote() = runTest {
        val note = library.createRichNote(null)
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
        val first = library.createRichNote(notebook.id)
        val second = library.createRichNote(notebook.id)
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
        val first = library.saveNote(library.createRichNote(null).copy(title = "永久删除目标"))
        val second = library.saveNote(library.createRichNote(null).copy(title = "清空目标"))
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
                settings.settings.first().defaultBackgroundKey ==
                    BackgroundKey.Builtin(GridBuiltinBackgroundId).encode()
            }
        }
    }

    @Test
    fun editorBackgroundOverrideCanReturnToDefaultInheritance() {
        val note = runBlocking {
            library.saveNote(library.createRichNote(null).copy(title = "背景测试"))
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
                    BackgroundKey.Builtin(RuledBuiltinBackgroundId).encode()
            }
        }

        composeRule.onNodeWithText("使用默认背景").performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { library.getNote(note.id)?.backgroundKey == null }
        }
    }
}
