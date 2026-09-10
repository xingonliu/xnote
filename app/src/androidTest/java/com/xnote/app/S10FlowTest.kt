package com.xnote.app

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.rememberTextMeasurer
import com.xnote.app.feature.reader.measureReadingUnits
import com.xnote.app.feature.reader.paginateReadingUnits
import androidx.test.core.app.ApplicationProvider
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.data.settings.InMemoryAppSettingsRepository
import com.xnote.app.design.*
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

class S10FlowTest {
    private val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = XNoteDatabase.createInMemory(context)
    private val root = File(context.cacheDir, "s10-${System.nanoTime()}")
    private val library = NoteLibrary(database, AttachmentFileStore(root), SystemEpochClock)
    private val cleanup = object : TestWatcher() {
        override fun finished(description: Description) { database.close(); root.deleteRecursively() }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(cleanup).around(compose)

    @Test fun profileStatisticsOpensRealNotesAndReturnsToStatistics() {
        runBlocking {
            library.saveNote(library.createNote(null).copy(title = "统计入口", document = NoteDocument(blocks = listOf(TextBlock("body", inlines = listOf(InlineRun("正文统计")))))))
            val trash = library.createNote(null)
            library.trashNotes(listOf(trash.id))
        }
        compose.setContent { XNoteTheme(reduceMotion = true) { XNoteApp(library) } }
        compose.onNodeWithText("我的").performClick()
        screenshot("profile-phone")
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithTag("xnote-statistics").assertIsDisplayed()
        compose.onAllNodesWithText("4", useUnmergedTree = true)[0].assertExists()
        screenshot("statistics-phone")
        compose.onNodeWithTag("xnote-statistics").performScrollToNode(hasText("最近创建"))
        compose.onAllNodesWithText("统计入口")[0].performClick()
        compose.onNodeWithTag("xnote-editor-title").assertTextEquals("统计入口")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("xnote-statistics").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("存储与隐私").performClick()
        compose.onNodeWithTag("xnote-storage").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("附件：", substring = true).fetchSemanticsNodes().isNotEmpty() }
        screenshot("storage-phone")
    }

    @Test fun appearanceControlsApplyImmediatelyAndSurviveStateRestoration() {
        val settings = InMemoryAppSettingsRepository()
        var dark = false
        var contrast = false
        var motion = false
        var scale = 0f
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val value by settings.settings.collectAsState(defaultAppSettings())
            XNoteTheme(darkTheme = value.themeMode == ThemeMode.Dark, reduceMotion = value.reduceMotion, highContrast = value.highContrast,
                fontScale = value.fontSize.scale, readingLayout = value.readingLayout) {
                dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                contrast = LocalXNoteInteractionSettings.current.highContrast
                motion = LocalXNoteInteractionSettings.current.reduceMotion
                scale = LocalDensity.current.fontScale
                XNoteApp(library, settings = settings)
            }
        }
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("外观、辅助功能与编辑").performClick()
        compose.onNodeWithText("深色").performClick()
        compose.onNodeWithTag("setting-高对比度").performScrollTo().performClick()
        compose.onNodeWithTag("setting-减少动画").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(dark); assertTrue(contrast); assertTrue(motion) }
        compose.onNodeWithText("特大").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1.3f, scale, 0.01f) }
        screenshot("appearance-dark-large")
        compose.onNodeWithText("宽松行距").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("xnote-appearance").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(ReadingLayout.Relaxed, runBlocking { settings.settings.first().readingLayout })
            assertTrue(dark); assertTrue(contrast); assertTrue(motion)
        }
    }

    @Test fun tabletPanesKeepDraftAndSelectionAcrossSearchResizeAndRecreation() {
        val book = runBlocking { library.createNotebook("平板工作本") }
        val notes = runBlocking { listOf("第一篇草稿", "第二篇草稿").map { title -> library.saveNote(library.createNote(book.id).copy(title = title)) } }
        var tablet by mutableStateOf(true)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val original = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(if (tablet) 0.8f else original.density, 1f)) {
                XNoteTheme(darkTheme = true, reduceMotion = true) { XNoteApp(library) }
            }
        }
        compose.onNodeWithTag("xnote-tablet-notebooks").assertIsDisplayed()
        compose.onNodeWithTag("xnote-tablet-note-list").assertIsDisplayed()
        compose.onNodeWithTag("xnote-tablet-content").assertIsDisplayed()
        compose.onNode(hasText("平板工作本") and hasAnyAncestor(hasTestTag("xnote-tablet-notebooks"))).performClick()
        compose.onNodeWithText("第一篇草稿").performClick()
        compose.onNodeWithTag("xnote-editor-body").performTextInput("切换前保存")
        compose.onNodeWithText("第二篇草稿").performClick()
        compose.runOnIdle { assertTrue(runBlocking { library.getNote(notes[0].id)!!.document.blocks.filterIsInstance<TextBlock>().any { it.inlines.plainText() == "切换前保存" } }) }
        compose.onNodeWithTag("xnote-editor-body").performTextInput("缩放仍保留")
        screenshot("tablet-dark-four-panes")
        compose.runOnIdle { tablet = false }
        compose.onNodeWithTag("xnote-editor-title").assertTextEquals("第二篇草稿")
        compose.onNodeWithTag("xnote-editor-body").assertTextContains("缩放仍保留")
        compose.onNodeWithContentDescription("撤销").assertIsEnabled()
        compose.runOnIdle { tablet = true }
        compose.onNodeWithText("搜索").performClick()
        compose.onNodeWithTag("xnote-search-field").performTextInput("切换前保存")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("第一篇草稿").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("第一篇草稿").performClick()
        compose.onNodeWithTag("xnote-search-field").assertIsDisplayed()
        compose.onNodeWithTag("xnote-editor-title").assertTextEquals("第一篇草稿")
        screenshot("tablet-search-detail")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("xnote-editor-title").assertTextEquals("第一篇草稿")
        compose.onNodeWithTag("xnote-editor-body").assertTextContains("切换前保存")
        runBlocking { library.moveNotes(listOf(notes[0].id), null) }
        compose.onNodeWithTag("xnote-editor-body").performTextInput("移动后续写")
        compose.waitUntil(5_000) { runBlocking {
            library.getNote(notes[0].id)!!.let { it.notebookId == null && it.document.blocks.filterIsInstance<TextBlock>().any { block -> block.inlines.plainText().contains("移动后续写") } }
        } }
        runBlocking { library.trashNotes(listOf(notes[0].id)) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("xnote-editor-title").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun relaxedReadingLayoutIncreasesPaginationAndPreservesEveryTextOffset() {
        val document = NoteDocument(blocks = listOf(
            TextBlock("body", inlines = listOf(InlineRun("正文排版。".repeat(200)))),
            TableBlock("table", rows = listOf(TableRow(cells = listOf(TableCell(inlines = listOf(InlineRun("表格内容".repeat(30)))))))),
        ))
        val note = runBlocking { library.saveNote(library.createNote(null).copy(document = document)) }
        var verified = false
        compose.setContent {
            XNoteTheme {
                val measurer = rememberTextMeasurer()
                val normal = measureReadingUnits(listOf(note), emptyMap(), measurer, MaterialTheme.typography, MaterialTheme.colorScheme,
                    300, 400f, 1f, "未命名", lineHeightScale = 1f)
                val relaxed = measureReadingUnits(listOf(note), emptyMap(), measurer, MaterialTheme.typography, MaterialTheme.colorScheme,
                    300, 400f, 1f, "未命名", lineHeightScale = 1.25f)
                SideEffect {
                    assertEquals(normal.map { it.blockId to it.offset }, relaxed.map { it.blockId to it.offset })
                    assertTrue(relaxed.sumOf { it.height.toDouble() } > normal.sumOf { it.height.toDouble() })
                    assertTrue(paginateReadingUnits(relaxed, 400f).size > paginateReadingUnits(normal, 400f).size)
                    verified = true
                }
            }
        }
        compose.runOnIdle { assertTrue(verified) }
    }

    // -- Functions
    private fun screenshot(name: String) {
        val file = File(context.getExternalFilesDir(null), "s10-$name.png")
        compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file.outputStream())
    }
}
