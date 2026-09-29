package com.xnote.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.xnote.app.design.LocalXNoteToast
import com.xnote.app.design.XNoteTheme
import com.xnote.app.design.XNoteToastProvider
import org.junit.Rule
import org.junit.Test

// -- Type Definitions

class XNoteToastTest {
    // -- State and Variables

    @get:Rule val compose = createComposeRule()

    // -- Functions

    @Test
    fun toastSurvivesPageReplacementAndDoesNotReappearAfterTimeout() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            XNoteTheme(reduceMotion = true) {
                XNoteToastProvider {
                    var page by remember { mutableIntStateOf(0) }
                    Column {
                        Button(onClick = { page = 1 - page }) { Text("切换页面") }
                        key(page) {
                            val toast = LocalXNoteToast.current
                            Text("页面 $page")
                            Button(onClick = { toast.show("全局提示") }) { Text("显示提示") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("显示提示").performClick()
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithText("全局提示").assertIsDisplayed()
        compose.onNodeWithText("切换页面").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("页面 1").assertIsDisplayed()
        compose.onAllNodesWithText("全局提示").assertCountEquals(1)
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithText("全局提示").assertDoesNotExist()
        compose.onNodeWithText("切换页面").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("页面 0").assertIsDisplayed()
        compose.onNodeWithText("全局提示").assertDoesNotExist()
    }
}
