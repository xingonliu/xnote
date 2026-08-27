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
        composeRule.onNodeWithText("笔记").assertIsSelected()
    }

    @Test
    fun bottomTabsNavigateToAgent() {
        composeRule.setContent {
            XNoteTheme {
                XNoteApp()
            }
        }

        composeRule.onNodeWithText("Agent").performClick()

        composeRule.onNodeWithText("Agent 工作区已预留").assertIsDisplayed()
        composeRule.onNode(isSelected() and hasText("Agent")).assertIsSelected()
    }

    @Test
    fun searchUsesSecondaryHeaderAndReturns() {
        composeRule.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteApp()
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
                    XNoteApp()
                }
            }
        }

        composeRule.onNodeWithTag("xnote-navigation-rail").assertIsDisplayed()
    }
}
