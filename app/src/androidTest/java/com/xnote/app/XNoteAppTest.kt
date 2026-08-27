package com.xnote.app

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
        composeRule.onNodeWithText("还没有笔记").assertIsDisplayed()
        composeRule.onNodeWithText("笔记").assertIsSelected()
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

        composeRule.onNodeWithText("设置中心已预留").assertIsDisplayed()
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
        composeRule.onNodeWithText("搜索即将接入本地笔记库").assertIsDisplayed()
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
}
