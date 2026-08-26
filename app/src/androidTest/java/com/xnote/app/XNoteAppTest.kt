package com.xnote.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.xnote.app.design.XNoteTheme
import org.junit.Rule
import org.junit.Test

// -- Tests

class XNoteAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appStartsOnNotesHome() {
        composeRule.setContent {
            XNoteTheme {
                XNoteApp()
            }
        }

        composeRule.onNodeWithText("全部笔记").assertIsDisplayed()
        composeRule.onNodeWithText("还没有笔记").assertIsDisplayed()
    }
}
