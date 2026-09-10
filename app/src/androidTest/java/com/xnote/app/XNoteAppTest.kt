package com.xnote.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.hasSetTextAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.design.XNoteTheme
import com.xnote.app.domain.model.SystemEpochClock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

// -- Tests

class XNoteAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = XNoteDatabase.createInMemory(context)
    private val filesRoot = File(context.cacheDir, "xnote-app-ui-${System.nanoTime()}")
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
    fun appStartsOnNotesHome() {
        composeRule.setContent {
            XNoteTheme {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithText("全部笔记").assertIsDisplayed()
        composeRule.onNodeWithText("我的笔记本").assertIsDisplayed()
        composeRule.onNodeWithTag("xnote-notebook-grid").assertIsDisplayed()
        composeRule.onNodeWithText("笔记").assertIsSelected()
    }

    @Test
    fun notebookGridOpensScopedNotesAndReturnsToTheGrid() {
        val book = runBlocking {
            val book = library.createNotebook("工作记录")
            library.saveNote(library.createNote(book.id).copy(title = "项目进度"))
            library.saveNote(library.createNote(null).copy(title = "随手想法"))
            book
        }
        composeRule.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library) } }
        composeRule.onNodeWithText("项目进度").assertDoesNotExist()
        composeRule.onNodeWithTag("xnote-notebook-${book.id}").performClick()
        composeRule.onNodeWithText("项目进度").assertIsDisplayed()
        composeRule.onNodeWithText("随手想法").assertDoesNotExist()
        composeRule.onNodeWithText("项目进度").performClick()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("项目进度").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithTag("xnote-notebook-grid").assertIsDisplayed()
        composeRule.onNodeWithTag("xnote-collection-unfiled").performClick()
        composeRule.onNodeWithText("随手想法").assertIsDisplayed()
        composeRule.onNodeWithText("项目进度").assertDoesNotExist()
    }

    @Test
    fun creatingNotebookOpensItAndNewNotesUseItsIdentity() {
        composeRule.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library) } }
        composeRule.onNodeWithTag("xnote-create-notebook").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("旅行计划")
        composeRule.onNodeWithText("创建").performClick()
        composeRule.onNodeWithText("旅行计划").assertIsDisplayed()
        composeRule.onNodeWithTag("xnote-create-note").performClick()
        composeRule.onNodeWithTag("xnote-editor-title").performTextInput("行李清单")
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("行李清单").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("旅行计划").assertIsDisplayed()
        composeRule.onNodeWithText("1 篇笔记").assertIsDisplayed()
    }

    @Test
    fun notebookGridAdaptsToThemeWindowAndLargeText() {
        val books = runBlocking { listOf("工作记录", "日常灵感", "读书摘录", "旅行计划").map { library.createNotebook(it) } }
        var dark by mutableStateOf(false)
        var tablet by mutableStateOf(false)
        var textScale by mutableStateOf(1f)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(if (tablet) 1f else density.density, textScale)) {
                XNoteTheme(darkTheme = dark, reduceMotion = true) { XNoteApp(library) }
            }
        }
        for (mode in listOf("light", "dark", "tablet", "large-text")) {
            composeRule.runOnIdle {
                dark = mode == "dark"
                tablet = mode == "tablet"
                textScale = if (mode == "large-text") 1.5f else 1f
            }
            val first = composeRule.onNodeWithTag("xnote-notebook-${books[0].id}").fetchSemanticsNode().boundsInRoot
            val second = composeRule.onNodeWithTag("xnote-notebook-${books[1].id}").fetchSemanticsNode().boundsInRoot
            if (mode == "large-text") assertTrue(second.top >= first.bottom)
            else { assertEquals(first.top, second.top); assertTrue(second.left >= first.right) }
            File(context.getExternalFilesDir(null), "notebooks-$mode.png").outputStream().use {
                composeRule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test
    fun bottomTabsNavigateToAgent() {
        composeRule.setContent {
            XNoteTheme {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithText("Agent").performClick()

        composeRule.onNodeWithText("Agent 工作区已预留").assertIsDisplayed()
        composeRule.onNode(isSelected() and hasText("Agent")).assertIsSelected()
    }

    @Test
    fun bottomTabsNavigateToProfile() {
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithText("我的").performClick()

        composeRule.onNodeWithText("回收站").assertIsDisplayed()
        composeRule.onNodeWithText("Markdown 快捷输入").assertIsDisplayed()
        composeRule.onNode(isSelected() and hasText("我的")).assertIsSelected()
    }

    @Test
    fun searchUsesSecondaryHeaderAndReturns() {
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp(noteLibrary = library)
            }
        }

        composeRule.onNodeWithContentDescription("搜索").performClick()
        composeRule.onNodeWithText("搜索笔记").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("全部笔记").assertIsDisplayed()
    }

    @Test
    fun wideWindowUsesNavigationRail() {
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = 1f,
                        fontScale = currentDensity.fontScale,
                    ),
                ) {
                    XNoteApp(noteLibrary = library)
                }
            }
        }

        composeRule.onNodeWithTag("xnote-navigation-rail").assertIsDisplayed()
    }

    @Test
    fun wideWindowKeepsTheNavigationRailWhileSearchExpandsInTheListPane() {
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = 1f,
                        fontScale = currentDensity.fontScale,
                    ),
                ) {
                    XNoteApp(noteLibrary = library)
                }
            }
        }

        composeRule.onNodeWithContentDescription("搜索").performClick()

        composeRule.onNodeWithTag("xnote-navigation-rail").assertIsDisplayed()
        composeRule.onNodeWithTag("xnote-search-field").assertIsDisplayed()
    }
}
